package com.deltasf.createpropulsion.telegraph;

import java.util.List;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds the selected signal strength (via Create's scroll value behaviour)
 * and animates the lever on the client. The behaviour handles saving,
 * client sync, and the value settings UI; the callback just pushes
 * neighbour updates so the redstone output follows the selection.
 */
public class TelegraphBlockEntity extends SmartBlockEntity {
    //Lever swing from vertical, in degrees; value 0 pulls back, 15 pushes forward
    public static final float MAX_SWING = 50.0f;
    //Lever animation speed in degrees per client tick
    private static final float SWING_SPEED = 10.0f;

    public TelegraphScrollBehaviour power;

    //Client-side animation state, lerped by the renderer with partial ticks
    public float visualAngle;
    public float prevVisualAngle;
    private boolean visualsInitialized = false;

    public TelegraphBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        power = new TelegraphScrollBehaviour(this);
        power.withCallback(i -> onPowerChanged());
        behaviours.add(power);
    }

    public int getPower() {
        return power == null ? 0 : TelegraphScrollBehaviour.toRedstone(power.getValue());
    }

    private void onPowerChanged() {
        Level level = getLevel();
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        level.updateNeighborsAt(worldPosition, state.getBlock());
        level.updateNeighborsAt(worldPosition.below(), state.getBlock());
    }

    //=== Client animation ===

    /** Target lever angle for a power level: +MAX_SWING at 0 (pulled back), -MAX_SWING at 15. */
    public static float angleForPower(int value) {
        return MAX_SWING * (1.0f - 2.0f * value / 15.0f);
    }

    @Override
    public void tick() {
        super.tick();
        Level level = getLevel();
        if (level == null || !level.isClientSide) return;

        float target = angleForPower(getPower());
        if (!visualsInitialized) {
            visualsInitialized = true;
            visualAngle = target;
            prevVisualAngle = target;
            return;
        }

        prevVisualAngle = visualAngle;
        float diff = target - visualAngle;
        if (diff != 0) {
            visualAngle += Mth.clamp(diff, -SWING_SPEED, SWING_SPEED);
        }
    }
}
