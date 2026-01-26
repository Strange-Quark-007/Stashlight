package dev.strangequark.stashlight.render;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class HighlightGeometry {

    private static final float THICKNESS = 0.02f;
    private static final float MAX_THICKNESS = 0.7f;
    private static final float R = 1f, G = 1f, B = 1f, A = 1f;


    static void drawWireframeBox(PoseStack matrices, VertexConsumer vc, Vec3 cam, BlockPos pos) {
        double dist = pos.distToCenterSqr(cam.x, cam.y, cam.z);
        float t = (float) Math.min(THICKNESS + Math.sqrt(dist) * 0.002, MAX_THICKNESS);

        float min = -t;
        float max = 1f + t;

        // Vertical pillars
        drawBox(matrices, vc, min, min, min, min + t, max, min + t);
        drawBox(matrices, vc, max - t, min, min, max, max, min + t);
        drawBox(matrices, vc, min, min, max - t, min + t, max, max);
        drawBox(matrices, vc, max - t, min, max - t, max, max, max);

        // Bottom edges
        drawBox(matrices, vc, min + t, min, min, max - t, min + t, min + t);
        drawBox(matrices, vc, min + t, min, max - t, max - t, min + t, max);
        drawBox(matrices, vc, min, min, min + t, min + t, min + t, max - t);
        drawBox(matrices, vc, max - t, min, min + t, max, min + t, max - t);

        // Top edges
        drawBox(matrices, vc, min + t, max - t, min, max - t, max, min + t);
        drawBox(matrices, vc, min + t, max - t, max - t, max - t, max, max);
        drawBox(matrices, vc, min, max - t, min + t, min + t, max, max - t);
        drawBox(matrices, vc, max - t, max - t, min + t, max, max, max - t);
    }

    private static void drawBox(PoseStack matrices, VertexConsumer vc,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2) {
        Matrix4f mat = matrices.last().pose();

        // Top
        vertex(vc, mat, x1, y2, z1);
        vertex(vc, mat, x1, y2, z2);
        vertex(vc, mat, x2, y2, z2);
        vertex(vc, mat, x2, y2, z1);

        // Bottom
        vertex(vc, mat, x1, y1, z2);
        vertex(vc, mat, x1, y1, z1);
        vertex(vc, mat, x2, y1, z1);
        vertex(vc, mat, x2, y1, z2);

        // Front
        vertex(vc, mat, x1, y1, z1);
        vertex(vc, mat, x1, y2, z1);
        vertex(vc, mat, x2, y2, z1);
        vertex(vc, mat, x2, y1, z1);

        // Back
        vertex(vc, mat, x2, y1, z2);
        vertex(vc, mat, x2, y2, z2);
        vertex(vc, mat, x1, y2, z2);
        vertex(vc, mat, x1, y1, z2);

        // Left
        vertex(vc, mat, x1, y1, z2);
        vertex(vc, mat, x1, y2, z2);
        vertex(vc, mat, x1, y2, z1);
        vertex(vc, mat, x1, y1, z1);

        // Right
        vertex(vc, mat, x2, y1, z1);
        vertex(vc, mat, x2, y2, z1);
        vertex(vc, mat, x2, y2, z2);
        vertex(vc, mat, x2, y1, z2);
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, float x, float y, float z) {
        vc.addVertex(mat, x, y, z).setColor(R, G, B, A).setNormal(0f, 1f, 0f);
    }
}
