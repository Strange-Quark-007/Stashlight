package dev.strangequark.stashlight.logic.sort;

import dev.strangequark.stashlight.gui.UIStyle;
import dev.strangequark.stashlight.model.IndexedItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

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
    public Text getTooltip() {
        return Text.translatable("gui.stashlight.sort.distance.tooltip");
    }

    @Override
    public void sort(List<IndexedItem> items) {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return;

        BlockPos playerPos = player.getBlockPos();

        items.sort((a, b) -> {
            double distA = a.pos().getSquaredDistance(playerPos);
            double distB = b.pos().getSquaredDistance(playerPos);
            return Double.compare(distA, distB);
        });
    }
}