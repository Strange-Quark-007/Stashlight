package dev.strangequark.stashlight.gui;

import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.model.IndexedItem;
import dev.strangequark.stashlight.render.HighlightManager;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

import static dev.strangequark.stashlight.gui.UIStyle.SLOT_SIZE;

public class ItemSlot extends StackLayout {
    private final IndexedItem indexedItem;
    private static final Surface BASE_SURFACE = Surface.flat(0x55888888);
    private static final Surface HOVER_SURFACE = Surface.flat(0x44FFFFFF).and(Surface.outline(0xFFFFFFFF));

    public static ItemSlot of(IndexedItem indexedItem) {
        return new ItemSlot(indexedItem);
    }

    protected ItemSlot(IndexedItem indexedItem) {
        super(Sizing.fixed(SLOT_SIZE), Sizing.fixed(SLOT_SIZE));
        this.indexedItem = indexedItem;
        ItemStack stack = indexedItem.stack();

        ItemComponent itemdisplay = Components.item(stack);
        itemdisplay.showOverlay(false).sizing(Sizing.fill(85));

        int count = stack.getCount();
        var scale = count > 999 ? 0.75f : 0.85f;

        QuantityLabel countLabel = QuantityLabel.of(Component.literal(String.valueOf(count)));
        countLabel.positioning(Positioning.relative(90, 90));

        this.surface(BASE_SURFACE).alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        this.child(itemdisplay);
        if (count > 1) {
            this.child(countLabel.scale(scale));
        }
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        boolean isMouseInside = mouseX >= this.x && mouseX <= (this.x + this.width)
                && mouseY >= this.y && mouseY <= (this.y + this.height);

        this.surface(isMouseInside ? HOVER_SURFACE : BASE_SURFACE);
        super.draw(context, mouseX, mouseY, partialTicks, delta);

        if (isMouseInside) {
            this.drawTooltip(context, mouseX, mouseY, partialTicks, delta);
        }
    }

    @Override
    public void drawTooltip(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }

        double dist = Math.sqrt(client.player.blockPosition().distSqr(indexedItem.pos()));
        String formattedDist = String.format("%.1f", dist);
        String posStr = String.format("%d, %d, %d", indexedItem.pos().getX(), indexedItem.pos().getY(), indexedItem.pos().getZ());

        // 1. Get Vanilla Lines (Handles Item Name & Data Components)
        List<Component> lines = new ArrayList<>(this.indexedItem.stack().getTooltipLines(
                Item.TooltipContext.of(client.level),
                client.player,
                client.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL
        ));

        lines.add(Component.empty()); // Spacer

        // Container
        lines.add(Component.translatable("gui.stashlight.label.container").withStyle(ChatFormatting.GRAY).append(": ")
                .append(Component.literal(indexedItem.containerName()).withStyle(ChatFormatting.WHITE)));

        // Location
        lines.add(Component.translatable("gui.stashlight.label.location").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(posStr).withStyle(ChatFormatting.AQUA))
                .append(Component.translatable("gui.stashlight.label.blocksAway", formattedDist).withStyle(ChatFormatting.GRAY)));

        // Dimension
        lines.add(Component.translatable("gui.stashlight.label.dimension").withStyle(ChatFormatting.GRAY).append(": ")
                .append(Component.literal(indexedItem.dimension()).withStyle(ChatFormatting.GREEN)));

        // Call the vanilla internal method
        context.setTooltipForNextFrame(
                client.font,
                lines,
                this.indexedItem.stack().getTooltipImage(),
                mouseX,
                mouseY,
                this.indexedItem.stack().get(DataComponents.TOOLTIP_STYLE)
        );
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) {
            return super.onMouseDown(click, doubled);
        }

        var client = Minecraft.getInstance();
        var player = client.player;
        if (player == null) {
            return true;
        }

        boolean highlighted = HighlightManager.tryHighlight(indexedItem);
        if (!highlighted) {
            return true;
        }
        if (Config.get().lookAtTarget()) {
            lookAt(player, indexedItem.pos());
        }
        client.setScreen(null);
        return true;
    }

    private void lookAt(Player player, BlockPos target) {
        double d = target.getX() + 0.5 - player.getX();
        double e = target.getY() + 0.5 - player.getEyeY();
        double f = target.getZ() + 0.5 - player.getZ();
        double g = Math.sqrt(d * d + f * f);

        float yaw = (float) (Math.atan2(f, d) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) (-(Math.atan2(e, g) * 180.0 / Math.PI));

        player.setYRot(yaw);
        player.setXRot(pitch);
    }
}