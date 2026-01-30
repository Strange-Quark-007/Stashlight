package dev.strangequark.stashlight.logic.sort;

import dev.strangequark.stashlight.gui.UIStyle;
import dev.strangequark.stashlight.model.IndexedItem;
import net.minecraft.text.Text;

import java.util.List;

public class AlphabeticalSort implements SortStrategy {
    @Override
    public SortKey key() {
        return SortKey.ALPHABETICAL;
    }

    @Override
    public String getLabel() {
        return UIStyle.SORT_NAME;
    }

    @Override
    public Text getTooltip() {
        return Text.translatable("gui.stashlight.sort.alphabetical.tooltip");
    }

    @Override
    public void sort(List<IndexedItem> items) {
        items.sort((a, b) -> a.stack().getName().getString().compareToIgnoreCase(b.stack().getName().getString()));
    }
}