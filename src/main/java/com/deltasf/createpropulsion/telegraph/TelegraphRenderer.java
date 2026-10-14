package com.deltasf.createpropulsion.telegraph;

import com.deltasf.createpropulsion.registries.PropulsionPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders the telegraph's lever, swinging it to the selected power level
 * with interpolation between client ticks.
 */
public class TelegraphRenderer extends SafeBlockEntityRenderer<TelegraphBlockEntity> {
    //Lever hinge in the partial model, in block coordinates (8, 2.5, 8 in pixels)
    private static final double PIVOT_X = 0.5;
    private static final double PIVOT_Y = 2.5 / 16.0;
    private static final double PIVOT_Z = 0.5;

    public TelegraphRenderer(BlockEntityRendererProvider.Context context) { super(); }

    @Override
    protected void renderSafe(TelegraphBlockEntity telegraph, float partialTicks, PoseStack poseStack,
                              MultiBufferSource bufferSource, int light, int overlay) {
        float angle = Mth.lerp(partialTicks, telegraph.prevVisualAngle, telegraph.visualAngle);
        BlockState state = telegraph.getBlockState();
        Direction facing = state.getValue(TelegraphBlock.FACING);

        SuperByteBuffer lever = CachedBuffers.partial(PropulsionPartialModels.TELEGRAPH_LEVER, state);
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutout());

        poseStack.pushPose();

        //Orient with the block. The partial is authored for a north-facing telegraph.
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(horizontalAngle(facing)));
        poseStack.translate(-0.5, -0.5, -0.5);

        //Swing the lever around its hinge
        poseStack.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);
        poseStack.mulPose(Axis.XP.rotationDegrees(angle));
        poseStack.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);

        lever.light(light).renderInto(poseStack, buffer);

        poseStack.popPose();
    }

    private static float horizontalAngle(Direction facing) {
        switch (facing) {
            case EAST: return 270;
            case SOUTH: return 180;
            case WEST: return 90;
            case NORTH:
            default: return 0;
        }
    }
}
