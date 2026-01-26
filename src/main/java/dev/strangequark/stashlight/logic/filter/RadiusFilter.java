package dev.strangequark.stashlight.logic.filter;

import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.model.IndexedItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class RadiusFilter implements FilterStrategy {

    private static final int[] RADIUS_VALUES = {4, 8, 16, 32, 64, -1};
    private static final int DEFAULT_INDEX = 2;

    @Override
    public String getLabel() {
        int index = Config.get().searchRadiusIndex();
        int val = RADIUS_VALUES[index];
        return val == -1 ? "All" : val + " (Chunks)";
    }

    @Override
    public boolean matches(IndexedItem item) {
        int index = Config.get().searchRadiusIndex();
        int maxRadius = RADIUS_VALUES[index];

        if (maxRadius == -1) { // All
            return true;
        }

        var player = Minecraft.getInstance().player;
        if (player == null || item.pos() == null) return true;

        // Convert BlockPos to Chunk coordinates via bit-shift (>> 4)
        int pX = player.getBlockPosBelowThatAffectsMyMovement().getX() >> 4;
        int pZ = player.getBlockPosBelowThatAffectsMyMovement().getZ() >> 4;
        int iX = item.pos().getX() >> 4;
        int iZ = item.pos().getZ() >> 4;

        // Chebyshev distance (Square radius)
        int dist = Math.max(Math.abs(pX - iX), Math.abs(pZ - iZ));

        return dist <= maxRadius;
    }

    public static Component getLabelForIndex(int index) {
        int safeIndex = (index < 0 || index >= RADIUS_VALUES.length) ? DEFAULT_INDEX : index;
        int val = RADIUS_VALUES[safeIndex];

        Component valueText = (val == -1)
                ? Component.translatable("gui.stashlight.label.rangeAll")
                : Component.translatable("gui.stashlight.label.rangeChunks", val);

        return Component.translatable("gui.stashlight.label.searchRange", valueText);
    }
}