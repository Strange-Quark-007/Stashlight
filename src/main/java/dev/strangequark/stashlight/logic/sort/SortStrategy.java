package dev.strangequark.stashlight.logic.sort;

import dev.strangequark.stashlight.model.IndexedItem;
import net.minecraft.network.chat.Component;

import java.util.List;

public interface SortStrategy {
    SortKey key();

    String getLabel();

    Component getTooltip();

    void sort(List<IndexedItem> items);
}
