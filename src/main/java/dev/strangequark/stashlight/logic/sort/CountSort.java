package dev.strangequark.stashlight.logic.sort;

import dev.strangequark.stashlight.gui.UIStyle;
import dev.strangequark.stashlight.model.IndexedItem;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CountSort implements SortStrategy {
    @Override
    public SortKey key() {
        return SortKey.COUNT;
    }

    @Override
    public String getLabel() {
        return UIStyle.SORT_COUNT;
    }

    @Override
    public Component getTooltip() {
        return Component.translatable("gui.stashlight.sort.count.tooltip");
    }

    @Override
    public void sort(List<IndexedItem> items) {
        items.sort((a, b) -> Integer.compare(b.stack().getCount(), a.stack().getCount()));
    }
}