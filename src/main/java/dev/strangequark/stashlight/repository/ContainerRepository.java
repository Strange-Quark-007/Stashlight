package dev.strangequark.stashlight.repository;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.model.ContainerSnapshot;
import dev.strangequark.stashlight.model.IndexedItem;
import dev.strangequark.stashlight.model.StackKey;
import dev.strangequark.stashlight.serializer.Serializer;
import dev.strangequark.stashlight.util.Util;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContainerRepository {

    private final Serializer serializer;
    private volatile boolean isDirty = false;

    private final Map<String, Map<BlockPos, ContainerSnapshot>> CONTAINER_ENTRIES_MAP;
    private List<IndexedItem> SEARCH_INDEX = new ArrayList<>();

    private final Map<String, Map<BlockPos, List<IndexedItem>>> INDEX_LOOKUP = new HashMap<>();


    private final ExecutorService cleanupExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ChestFinder-Cleanup");
        t.setDaemon(true);
        return t;
    });

    // Prevents submitting duplicate save tasks when saveIfDirty() is called
    // rapidly (e.g. every 3000 ticks) before a previous save has finished.
    private volatile boolean isSavePending = false;

    private static final int CLEANUP_BATCH_SIZE = 64;
    private String cleanupDimension = null;
    private List<BlockPos> cleanupQueue = Collections.emptyList();
    private int cleanupIndex = 0;
    private int cleanupCooldownTicks = 0;

    public ContainerRepository(Serializer serializer) {
        this.serializer = serializer;
        this.CONTAINER_ENTRIES_MAP = serializer.read();
        rebuildIndex();
    }

    public void runCleanup(ClientLevel world) {
        String dimension = Util.getDimensionName(world);

        if (!dimension.equals(cleanupDimension)) {
            cleanupDimension = dimension;
            cleanupQueue = Collections.emptyList();
            cleanupIndex = 0;
            cleanupCooldownTicks = 0;
        }

        if (cleanupIndex >= cleanupQueue.size()) {
            if (cleanupCooldownTicks > 0) {
                cleanupCooldownTicks--;
                return;
            }

            synchronized (CONTAINER_ENTRIES_MAP) {
                Map<BlockPos, ContainerSnapshot> dataMap = CONTAINER_ENTRIES_MAP.get(dimension);
                if (dataMap == null || dataMap.isEmpty()) {
                    return;
                }
                cleanupQueue = new ArrayList<>(dataMap.keySet());
                cleanupIndex = 0;
            }
        }

        // Check a bounded batch per tick on the main thread to avoid frame spikes.
        int end = Math.min(cleanupIndex + CLEANUP_BATCH_SIZE, cleanupQueue.size());
        List<BlockPos> toRemove = new ArrayList<>();

        for (int i = cleanupIndex; i < end; i++) {
            BlockPos pos = cleanupQueue.get(i);
            if (world.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                if (!Util.isValidSearchableContainer(world.getBlockState(pos))) {
                    toRemove.add(pos);
                }
            }
        }
        cleanupIndex = end;

        if (cleanupIndex >= cleanupQueue.size()) {
            cleanupQueue = Collections.emptyList();
            cleanupCooldownTicks = 100;
        }

        if (toRemove.isEmpty()) {
            return;
        }

        // Only map mutation + dirty flag + save goes to executor
        cleanupExecutor.submit(() -> {
            synchronized (CONTAINER_ENTRIES_MAP) {
                Map<BlockPos, ContainerSnapshot> dataMap = CONTAINER_ENTRIES_MAP.get(dimension);
                if (dataMap != null) {
                    for (BlockPos pos : toRemove) {
                        dataMap.remove(pos);
                    }
                    removeAllFromIndex(dimension, toRemove);
                    this.isDirty = true;
                }
            }
            saveIfDirty();
        });
    }

    public void update(String dimension, BlockPos pos, String blockName, int capacity, List<ItemStack> stacks) {
        List<ItemStack> copiedStacks = new ArrayList<>();
        for (ItemStack original : stacks) {
            if (original != null && !original.isEmpty()) {
                copiedStacks.add(original.copy());
            }
        }

        ContainerSnapshot snapshot = new ContainerSnapshot(blockName, capacity, copiedStacks, System.currentTimeMillis());

        synchronized (CONTAINER_ENTRIES_MAP) {
            CONTAINER_ENTRIES_MAP.computeIfAbsent(dimension, k -> new HashMap<>()).put(pos, snapshot);
            this.isDirty = true;

            removeFromIndex(dimension, pos);
            addToIndex(dimension, pos, snapshot);
        }
    }

    public void remove(String dimension, BlockPos pos) {
        synchronized (CONTAINER_ENTRIES_MAP) {
            Map<BlockPos, ContainerSnapshot> dimMap = CONTAINER_ENTRIES_MAP.get(dimension);
            if (dimMap != null && dimMap.remove(pos) != null) {
                this.isDirty = true;
                removeFromIndex(dimension, pos);
            }
        }
    }

    private void removeFromIndex(String dimension, BlockPos pos) {
        removeAllFromIndex(dimension, Collections.singletonList(pos));
    }

    private void removeAllFromIndex(String dimension, Collection<BlockPos> positions) {
        Map<BlockPos, List<IndexedItem>> dimLookup = INDEX_LOOKUP.get(dimension);
        if (dimLookup == null || positions.isEmpty()) {
            return;
        }

        Set<IndexedItem> itemsToRemove = new HashSet<>();
        for (BlockPos pos : positions) {
            List<IndexedItem> oldItems = dimLookup.remove(pos);
            if (oldItems != null) {
                itemsToRemove.addAll(oldItems);
            }
        }

        if (!itemsToRemove.isEmpty()) {
            // Build new list in O(N) time
            List<IndexedItem> newIndex = new ArrayList<>(SEARCH_INDEX.size());
            for (IndexedItem item : SEARCH_INDEX) {
                if (!itemsToRemove.contains(item)) {
                    newIndex.add(item);
                }
            }
            SEARCH_INDEX = newIndex;
        }
    }

    private void addToIndex(String dimension, BlockPos pos, ContainerSnapshot snapshot) {
        Map<StackKey, ItemStack> localMap = new LinkedHashMap<>();
        for (ItemStack stack : snapshot.items()) {
            if (stack == null || stack.isEmpty()) continue;
            StackKey key = new StackKey(stack);
            if (localMap.containsKey(key)) {
                localMap.get(key).grow(stack.getCount());
            } else {
                localMap.put(key, stack.copy());
            }
        }

        List<IndexedItem> newItems = new ArrayList<>();
        for (ItemStack summedStack : localMap.values()) {
            IndexedItem indexedItem = new IndexedItem(
                    summedStack,
                    pos,
                    dimension,
                    snapshot.containerName(),
                    snapshot.containerCapacity(),
                    snapshot.timestamp()
            );
            newItems.add(indexedItem);
            SEARCH_INDEX.add(indexedItem);
        }

        INDEX_LOOKUP.computeIfAbsent(dimension, k -> new HashMap<>()).put(pos, newItems);
    }

    public void saveIfDirty() {
        if (!this.isDirty || this.isSavePending || !serializer.isLoadSucceeded()) return;
        this.isSavePending = true;
        cleanupExecutor.submit(() -> {
            try {
                Map<String, Map<BlockPos, ContainerSnapshot>> snapshot = snapshotIfDirty();
                if (snapshot != null) {
                    serializer.write(snapshot);
                }
            } finally {
                this.isSavePending = false;
            }
        });
    }

    private Map<String, Map<BlockPos, ContainerSnapshot>> snapshotIfDirty() {
        synchronized (CONTAINER_ENTRIES_MAP) {
            if (!this.isDirty) {
                return null;
            }
            Map<String, Map<BlockPos, ContainerSnapshot>> snapshot = new HashMap<>(CONTAINER_ENTRIES_MAP.size());
            for (var entry : CONTAINER_ENTRIES_MAP.entrySet()) {
                snapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
            }
            this.isDirty = false;
            return snapshot;
        }
    }

    public void rebuildIndex() {
        synchronized (CONTAINER_ENTRIES_MAP) {
            SEARCH_INDEX = new ArrayList<>();
            INDEX_LOOKUP.clear();

            for (var dimEntry : CONTAINER_ENTRIES_MAP.entrySet()) {
                String dimension = dimEntry.getKey();
                for (var posEntry : dimEntry.getValue().entrySet()) {
                    BlockPos pos = posEntry.getKey();
                    ContainerSnapshot snapshot = posEntry.getValue();
                    addToIndex(dimension, pos, snapshot);
                }
            }
        }
    }

    public Set<String> getDimensions() {
        synchronized (CONTAINER_ENTRIES_MAP) {
            return new HashSet<>(CONTAINER_ENTRIES_MAP.keySet());
        }
    }

    public List<IndexedItem> getSearchIndex() {
        synchronized (CONTAINER_ENTRIES_MAP) {
            return new ArrayList<>(SEARCH_INDEX);
        }
    }

    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                Stashlight.LOGGER.warn("Stashlight cleanup executor did not finish in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (serializer.isLoadSucceeded()) {
            Map<String, Map<BlockPos, ContainerSnapshot>> snapshot = snapshotIfDirty();
            if (snapshot != null) {
                serializer.write(snapshot);
            }
        }
    }
}