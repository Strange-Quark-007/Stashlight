package dev.strangequark.stashlight.gui;

import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.model.IndexedItem;
import dev.strangequark.stashlight.render.HighlightManager;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.PositionedRectangle;
import io.wispforest.owo.ui.core.Sizing;
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
import java.util.Collections;
import java.util.List;

import static dev.strangequark.stashlight.gui.UIStyle.*;

public class ItemGrid extends BaseUIComponent {
    private List<IndexedItem> items = Collections.emptyList();
    private int slotsPerRow = 1;

    // Hovered slot index (-1 = none)
    private int hoveredIndex = -1;

    public ItemGrid() {
        this.horizontalSizing(Sizing.content());
        this.verticalSizing(Sizing.content());
    }

    public void setItems(List<IndexedItem> newItems, int newSlotsPerRow) {
        this.items = newItems;
        this.slotsPerRow = Math.max(1, newSlotsPerRow);
        this.hoveredIndex = -1;
        this.notifyParentIfMounted();
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return slotsPerRow * (SLOT_SIZE + GAP) + GAP;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        if (items.isEmpty()) return 0;
        int rows = (int) Math.ceil((double) items.size() / slotsPerRow);
        return rows * (SLOT_SIZE + GAP) + GAP;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (items.isEmpty()) return;

        var mc = Minecraft.getInstance();
        var font = mc.font;

        // Recompute hovered slot from raw mouse coords (O(1) math, no iteration)
        hoveredIndex = slotIndexAt(mouseX, mouseY);

        int n = items.size();
        for (int i = 0; i < n; i++) {
            int row = i / slotsPerRow;
            int col = i % slotsPerRow;

            int slotX = this.x + GAP + col * (SLOT_SIZE + GAP);
            int slotY = this.y + GAP + row * (SLOT_SIZE + GAP);

            // Scissor cull: skip slots fully outside the scroll viewport.
            // intersectsScissor uses the scissor rect already set by ScrollContainer.
            if (!graphics.intersectsScissor(PositionedRectangle.of(slotX, slotY, SLOT_SIZE, SLOT_SIZE))) continue;

            boolean hovered = (i == hoveredIndex);

            // Background
            graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE,
                    hovered ? SLOT_HOVER : SLOT_BG);

            // Hover outline
            if (hovered) {
                graphics.drawRectOutline(slotX, slotY, SLOT_SIZE, SLOT_SIZE, SLOT_OUTLINE);
            }

            // Item icon — render directly at 16x16 centered in the 24x24 slot.
            // No scaling = pixel perfect sharpness.
            ItemStack stack = items.get(i).stack();
            int iconX = slotX + (SLOT_SIZE - 16) / 2;
            int iconY = slotY + (SLOT_SIZE - 16) / 2;
            graphics.renderItem(stack, iconX, iconY);

            // Count label
            int count = stack.getCount();
            if (count > 1) {
                String countStr = String.valueOf(count);
                float scale = count > 999 ? 0.75f : 0.85f;

                float labelX = slotX + SLOT_SIZE - font.width(countStr) * scale - 1;
                float labelY = slotY + SLOT_SIZE - font.lineHeight * scale - 1;
                graphics.drawText(
                        Component.literal(countStr),
                        labelX, labelY, scale,
                        COUNT_COLOR,
                        OwoUIGraphics.TextAnchor.TOP_LEFT
                );
            }

            // Tooltip — only for the hovered slot, deferred to end of frame
            if (hovered && mc.player != null && mc.level != null) {
                renderTooltip(graphics, items.get(i), mouseX, mouseY, mc);
            }
        }
    }

    private void renderTooltip(OwoUIGraphics graphics, IndexedItem item, int mouseX, int mouseY, Minecraft mc) {
        assert mc.player != null;
        double dist = Math.sqrt(mc.player.blockPosition().distSqr(item.pos()));
        String formattedDist = String.format("%.1f", dist);
        String posStr = String.format("%d, %d, %d",
                item.pos().getX(), item.pos().getY(), item.pos().getZ());

        List<Component> lines = new ArrayList<>(item.stack().getTooltipLines(
                Item.TooltipContext.of(mc.level),
                mc.player,
                mc.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL
        ));

        lines.add(Component.empty());
        lines.add(Component.translatable("gui.stashlight.label.container").withStyle(ChatFormatting.GRAY)
                .append(": ")
                .append(Component.literal(item.containerName()).withStyle(ChatFormatting.WHITE)));
        lines.add(Component.translatable("gui.stashlight.label.location").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(posStr).withStyle(ChatFormatting.AQUA))
                .append(Component.translatable("gui.stashlight.label.blocksAway", formattedDist).withStyle(ChatFormatting.GRAY)));
        lines.add(Component.translatable("gui.stashlight.label.dimension").withStyle(ChatFormatting.GRAY).append(": ")
                .append(Component.literal(item.dimension()).withStyle(ChatFormatting.GREEN)));

        graphics.setTooltipForNextFrame(
                mc.font, lines,
                item.stack().getTooltipImage(),
                mouseX, mouseY,
                item.stack().get(DataComponents.TOOLTIP_STYLE)
        );
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return super.onMouseDown(click, doubled);

        // click coords are relative to the component origin in owo
        int idx = slotIndexAt((int) (this.x + click.x()), (int) (this.y + click.y()));
        if (idx < 0 || idx >= items.size()) return true;

        var mc = Minecraft.getInstance();
        if (mc.player == null) return true;

        IndexedItem item = items.get(idx);
        if (!HighlightManager.tryHighlight(item)) return true;

        if (Config.get().lookAtTarget()) lookAt(mc.player, item.pos());
        mc.setScreen(null);
        return true;
    }

    /**
     * Returns the item index under screen coords (mouseX, mouseY), or -1.
     * Pure integer math — O(1), no iteration.
     */
    private int slotIndexAt(int mouseX, int mouseY) {
        int relX = mouseX - this.x - GAP;
        int relY = mouseY - this.y - GAP;
        if (relX < 0 || relY < 0) return -1;

        int stride = SLOT_SIZE + GAP;
        int col = relX / stride;
        int row = relY / stride;

        // Reject clicks that land in the gap between slots
        if (relX % stride >= SLOT_SIZE) return -1;
        if (relY % stride >= SLOT_SIZE) return -1;
        if (col >= slotsPerRow) return -1;

        int idx = row * slotsPerRow + col;
        return idx < items.size() ? idx : -1;
    }

    private void lookAt(Player player, BlockPos target) {
        double dx = target.getX() + 0.5 - player.getX();
        double dy = target.getY() + 0.5 - player.getEyeY();
        double dz = target.getZ() + 0.5 - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        player.setYRot((float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f);
        player.setXRot((float) (-(Math.atan2(dy, dist) * 180.0 / Math.PI)));
    }
}
