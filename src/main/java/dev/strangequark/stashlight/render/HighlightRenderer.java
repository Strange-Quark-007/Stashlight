package dev.strangequark.stashlight.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.strangequark.stashlight.model.HighlightPos;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.world.phys.Vec3;

public final class HighlightRenderer {
    public static void render(LevelRenderContext context) {
        HighlightManager.removeExpired();

        Vec3 cam = context.levelState().cameraRenderState.pos;

        for (HighlightPos highlight : HighlightManager.getActiveHighlights()) {
            long elapsed = System.currentTimeMillis() - highlight.startTimeMillis();
            if (!HighlightEffect.shouldRender(elapsed)) continue;

            PoseStack matrices = new PoseStack();
            matrices.translate(
                    highlight.pos().getX() - cam.x,
                    highlight.pos().getY() - cam.y,
                    highlight.pos().getZ() - cam.z
            );

            context.submitNodeCollector().submitCustomGeometry(
                    matrices,
                    HighlightRenderLayer.XRAY_LAYER,
                    (pose, vc) -> HighlightGeometry.drawWireframeBox(pose, vc, cam, highlight.pos())
            );
        }
    }
}