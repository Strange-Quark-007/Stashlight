package dev.strangequark.stashlight.model;


import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Represents a frozen state of a specific container in the world.
 */
public record ContainerSnapshot(
        String containerName,
        int containerCapacity,
        List<ItemStack> items,
        long timestamp
) {
}
