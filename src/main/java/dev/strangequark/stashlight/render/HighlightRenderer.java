package dev.strangequark.stashlight.render;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strangequark.stashlight.model.HighlightPos;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.world.phys.Vec3;

public final class HighlightRenderer {

    private HighlightRenderer() {
    }

    public static void render(WorldRenderContext context) {
        VertexConsumer vc = context.consumers().getBuffer(HighlightRenderLayer.XRAY_LAYER);
        Vec3 cam = context.gameRenderer().getMainCamera().getPosition();

        HighlightManager.removeExpired();

        for (HighlightPos highlight : HighlightManager.getActiveHighlights()) {
            long elapsed = System.currentTimeMillis() - highlight.startTimeMillis();
            if (!HighlightEffect.shouldRender(elapsed)) continue;

            PoseStack matrices = context.matrices();
            matrices.pushPose();
            matrices.translate(
                    highlight.pos().getX() - cam.x,
                    highlight.pos().getY() - cam.y,
                    highlight.pos().getZ() - cam.z
            );
            HighlightGeometry.drawWireframeBox(matrices, vc, cam, highlight.pos());
            matrices.popPose();
        }
    }
}
