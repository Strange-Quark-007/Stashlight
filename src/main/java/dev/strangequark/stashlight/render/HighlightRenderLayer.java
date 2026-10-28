package dev.strangequark.stashlight.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.strangequark.stashlight.Stashlight;
import dev.strangequark.stashlight.mixin.RenderTypeInvoker;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public class HighlightRenderLayer {

    private static final DepthStencilState XRAY_DEPTH_STATE = new DepthStencilState(CompareOp.ALWAYS_PASS, false);


    public static final RenderPipeline XRAY_PIPELINE =
            RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                            .withLocation(Identifier.fromNamespaceAndPath(Stashlight.MOD_ID, "xray"))
                            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                            .withPrimitiveTopology(PrimitiveTopology.QUADS)
                            .withDepthStencilState(XRAY_DEPTH_STATE)
                            .build()
            );

    public static final RenderType XRAY_LAYER = RenderTypeInvoker.create(
            "chestfinder_xray",
            RenderSetup.builder(XRAY_PIPELINE).createRenderSetup()
    );
}
