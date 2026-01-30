package dev.strangequark.stashlight.model;

import net.minecraft.core.BlockPos;

/**
 * Immutable data describing a block position being highlighted.
 */
public record HighlightPos(
        BlockPos pos,
        long startTimeMillis
) {
}

