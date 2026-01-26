package dev.strangequark.stashlight.serializer;

import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.model.ContainerSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Serializer {
    private final Path file;
    private final HolderLookup.Provider lookup;

    private static final String NBT_CONTAINER_NAME_KEY = "name";
    private static final String NBT_CONTAINER_CAPACITY_KEY = "capacity";
    private static final String NBT_STACK_LIST_KEY = "items";
    private static final String NBT_TIMESTAMP_KEY = "time";

    public Serializer(Path file, HolderLookup.Provider lookup) {
        this.file = file;
        this.lookup = lookup;
    }

    public Map<String, Map<BlockPos, ContainerSnapshot>> read() {
        Map<String, Map<BlockPos, ContainerSnapshot>> database = new HashMap<>();
        if (file == null || !Files.exists(file)) return database;

        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());

            for (String dim : root.keySet()) {
                root.getCompound(dim).ifPresent(dimTag -> {
                    Map<BlockPos, ContainerSnapshot> posMap = new HashMap<>();
                    for (String key : dimTag.keySet()) {
                        BlockPos pos = BlockPos.of(Long.parseLong(key));
                        dimTag.getCompound(key).ifPresent(snapNbt -> posMap.put(pos, deserializeSnapshot(snapNbt)));
                    }
                    database.put(dim, posMap);
                });
            }
        } catch (Exception e) {
            Stashlight.LOGGER.error("Load failed", e);
        }
        return database;
    }

    public void write(Map<String, Map<BlockPos, ContainerSnapshot>> database) {
        if (file == null) return;

        CompoundTag root = new CompoundTag();
        database.forEach((dim, posMap) -> {
            CompoundTag dimTag = new CompoundTag();
            posMap.forEach((pos, snap) -> dimTag.put(String.valueOf(pos.asLong()), serializeSnapshot(snap)));
            root.put(dim, dimTag);
        });

        try {
            NbtIo.writeCompressed(root, file);
        } catch (Exception e) {
            Stashlight.LOGGER.error("Save failed", e);
        }
    }

    private CompoundTag serializeSnapshot(ContainerSnapshot snap) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString(NBT_CONTAINER_NAME_KEY, snap.containerName());
        nbt.putInt(NBT_CONTAINER_CAPACITY_KEY, snap.containerCapacity());
        nbt.putLong(NBT_TIMESTAMP_KEY, snap.timestamp());

        ListTag itemList = new ListTag();
        for (ItemStack stack : snap.items()) {
            itemList.add(serializeStack(stack));
        }
        nbt.put(NBT_STACK_LIST_KEY, itemList);
        return nbt;
    }

    private ContainerSnapshot deserializeSnapshot(CompoundTag nbt) {
        String name = nbt.getStringOr(NBT_CONTAINER_NAME_KEY, "");
        int capacity = nbt.getIntOr(NBT_CONTAINER_CAPACITY_KEY, 0);
        long timestamp = nbt.getLongOr(NBT_TIMESTAMP_KEY, 0L);

        List<ItemStack> items = new ArrayList<>();
        nbt.getList(NBT_STACK_LIST_KEY).ifPresent(itemList -> {
            for (int i = 0; i < itemList.size(); i++) {
                itemList.getCompound(i)
                        .map(this::deserializeStack)
                        .ifPresent(items::add);
            }
        });
        return new ContainerSnapshot(name, capacity, items, timestamp);
    }

    private Tag serializeStack(ItemStack stack) {
        try {
            var ops = RegistryOps.create(NbtOps.INSTANCE, lookup);
            return ItemStack.CODEC.encodeStart(ops, stack)
                    .resultOrPartial(error -> Stashlight.LOGGER.warn("Failed to serialize item: {}", error))
                    .orElse(new CompoundTag());
        } catch (Exception e) {
            Stashlight.LOGGER.warn("ItemStack serialization failed for {}", stack.getItem(), e);
            return new CompoundTag();
        }
    }

    private ItemStack deserializeStack(CompoundTag nbt) {
        try {
            var ops = RegistryOps.create(NbtOps.INSTANCE, lookup);
            return ItemStack.CODEC.parse(ops, nbt)
                    .resultOrPartial(error -> Stashlight.LOGGER.warn("Failed to deserialize item: {}", error))
                    .orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            Stashlight.LOGGER.warn("ItemStack deserialization failed", e);
            return ItemStack.EMPTY;
        }
    }
}