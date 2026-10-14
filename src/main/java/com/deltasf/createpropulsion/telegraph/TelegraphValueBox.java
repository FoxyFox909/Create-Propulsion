package com.deltasf.createpropulsion.telegraph;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Places the telegraph's value box on every reachable side of the pedestal
 * so the signal strength can be adjusted from any direction, including from
 * above - the pedestal is short, so the top face is the most natural target.
 */
public class TelegraphValueBox extends ValueBoxTransform.Sided {
    @Override
    protected boolean isSideActive(BlockState state, Direction direction) {
        return direction != Direction.DOWN;
    }

    @Override
    protected Vec3 getSouthLocation() {
        return VecHelper.voxelSpace(8.0, 5.0, 14.5);
    }
}
