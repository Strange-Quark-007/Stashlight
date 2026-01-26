package dev.strangequark.stashlight.render;

import dev.strangequark.stashlight.model.HighlightPos;
import dev.strangequark.stashlight.model.IndexedItem;
import dev.strangequark.stashlight.util.Util;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class HighlightManager {
    private static final List<HighlightPos> highlights = new ArrayList<>();

    private HighlightManager() {
    }

    public static boolean tryHighlight(IndexedItem item) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return false;

        String currentDim = Util.getDimensionName(client.level);
        if (!currentDim.equals(item.dimension())) {
            notifyWrongDimension(client.player);
            return false;
        }

        highlights.add(new HighlightPos(item.pos(), System.currentTimeMillis()));
        return true;
    }

    public static void removeExpired() {
        long now = System.currentTimeMillis();
        highlights.removeIf(h -> HighlightEffect.isExpired(now - h.startTimeMillis()));
    }

    public static List<HighlightPos> getActiveHighlights() {
        long now = System.currentTimeMillis();
        List<HighlightPos> active = new ArrayList<>();
        for (HighlightPos h : highlights) {
            if (!HighlightEffect.isExpired(now - h.startTimeMillis())) {
                active.add(h);
            }
        }
        return active;
    }

    private static void notifyWrongDimension(@NotNull Player player) {
        player.displayClientMessage(Component.literal("Container is in a different dimension.").withStyle(ChatFormatting.RED), false);
    }
}
