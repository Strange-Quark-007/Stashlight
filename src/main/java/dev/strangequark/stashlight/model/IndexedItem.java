package dev.strangequark.stashlight.model;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * A 'ready-to-search' entry. We pre-calculate the sums and
 * attach the metadata so the UI doesn't have to do any math.
 */
public record IndexedItem(
        ItemStack stack,
        BlockPos pos,
        String dimension,
        String containerName,
        int containerCapacity,
        long timestamp,
        String searchKey
) {
    public IndexedItem(ItemStack stack, BlockPos pos, String dimension, String containerName, int containerCapacity, long timestamp) {
        this(
                stack,
                pos,
                dimension,
                containerName,
                containerCapacity,
                timestamp,
                stack.getHoverName().getString().toLowerCase()
        );
    }
}