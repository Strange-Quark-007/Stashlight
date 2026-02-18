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

    public ContainerRepository(Serializer serializer) {
        this.serializer = serializer;
        this.CONTAINER_ENTRIES_MAP = serializer.read();
        rebuildIndex();
    }

    public void runCleanup(ClientLevel world) {
        String dimension = Util.getDimensionName(world);
        Map<BlockPos, ContainerSnapshot> dataMap = CONTAINER_ENTRIES_MAP.get(dimension);

        if (dataMap == null || dataMap.isEmpty()) return;

        cleanupExecutor.submit(() -> {
            // Step 1: Collect positions to check (quick, inside lock)
            List<BlockPos> toCheck;
            synchronized (CONTAINER_ENTRIES_MAP) {
                toCheck = new ArrayList<>(dataMap.keySet());
            }

            // Step 2: Check world state (slow, OUTSIDE lock - doesn't block other operations)
            List<BlockPos> toRemove = new ArrayList<>();
            for (BlockPos pos : toCheck) {
                try {
                    if (world.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                        var state = world.getBlockState(pos);
                        if (!Util.isValidSearchableContainer(state)) {
                            toRemove.add(pos);
                        }
                    }
                } catch (Exception e) {
                    // Skip this position if world access fails
                }
            }


            if (!toRemove.isEmpty()) {
                synchronized (CONTAINER_ENTRIES_MAP) {
                    for (BlockPos pos : toRemove) {
                        dataMap.remove(pos);
                        removeFromIndex(dimension, pos);
                    }
                    this.isDirty = true;
                }
                saveIfDirty();
            }
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
        Map<BlockPos, List<IndexedItem>> dimLookup = INDEX_LOOKUP.get(dimension);
        if (dimLookup != null) {
            List<IndexedItem> oldItems = dimLookup.remove(pos);

            if (oldItems != null && !oldItems.isEmpty()) {
                Set<IndexedItem> itemsToRemove = new HashSet<>(oldItems);

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
        if (!this.isDirty || this.isSavePending) return;
        this.isSavePending = true;
        // Snapshot the data under the lock, then write to disk off the main thread.
        // NbtIo.writeCompressed() is blocking I/O — never run it on the render thread.
        cleanupExecutor.submit(() -> {
            try {
                synchronized (CONTAINER_ENTRIES_MAP) {
                    serializer.write(CONTAINER_ENTRIES_MAP);
                    this.isDirty = false;
                }
            } finally {
                this.isSavePending = false;
            }
        });
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
        // Flush any pending dirty state, then wait for the executor to finish
        // so the final save completes before the game disconnects.
        saveIfDirty();
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                Stashlight.LOGGER.warn("Stashlight cleanup executor did not finish in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}