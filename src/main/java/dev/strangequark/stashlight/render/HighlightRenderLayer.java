package dev.strangequark.stashlight.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.mixin.RenderTypeInvoker;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public class HighlightRenderLayer {
    public static final RenderPipeline XRAY_PIPELINE =
            RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                            .withLocation(Identifier.fromNamespaceAndPath(Stashlight.MOD_ID, "xray"))
                            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .build()
            );

    public static final RenderType XRAY_LAYER = RenderTypeInvoker.create(
            "chestfinder_xray",
            RenderSetup.builder(XRAY_PIPELINE).createRenderSetup()
    );
}
