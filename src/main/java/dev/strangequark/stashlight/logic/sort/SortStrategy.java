package dev.strangequark.stashlight.logic.sort;

import dev.strangequark.stashlight.model.IndexedItem;
import net.minecraft.text.Text;

import java.util.List;

public interface SortStrategy {
    SortKey key();

    String getLabel();

    Text getTooltip();

    void sort(List<IndexedItem> items);
}
