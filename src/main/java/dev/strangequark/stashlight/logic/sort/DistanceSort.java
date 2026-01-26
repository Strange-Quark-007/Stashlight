package dev.strangequark.stashlight.logic.sort;

import dev.strangequark.stashlight.gui.UIStyle;
import dev.strangequark.stashlight.model.IndexedItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

public class DistanceSort implements SortStrategy {
    @Override
    public SortKey key() {
        return SortKey.DISTANCE;
    }

    @Override
    public String getLabel() {
        return UIStyle.SORT_DIST;
    }

    @Override
    public Component getTooltip() {
        return Component.translatable("gui.stashlight.sort.distance.tooltip");
    }

    @Override
    public void sort(List<IndexedItem> items) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        BlockPos playerPos = player.getOnPos();

        items.sort((a, b) -> {
            double distA = a.pos().distSqr(playerPos);
            double distB = b.pos().distSqr(playerPos);
            return Double.compare(distA, distB);
        });
    }
}