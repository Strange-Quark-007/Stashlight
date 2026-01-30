package dev.strangequark.stashlight.model;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public record StackKey(ItemStack stack) {
    @Override
    public boolean equals(Object obj) {
        return obj instanceof StackKey(ItemStack stack1) && ItemStack.isSameItemSameComponents(this.stack, stack1);
    }

    @Override
    public int hashCode() {
        // We hash the Item and the Components, ignoring the count
        return Objects.hash(stack.getItem(), stack.getComponents());
    }
}