package com.deltasf.createpropulsion.thruster.hoverpad;

import com.deltasf.createpropulsion.registries.PropulsionBlockEntities;
import com.deltasf.createpropulsion.thruster.AbstractThrusterBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntityTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Hoverpad block. Always placed with its emitter facing down: {@code FACING}
 * (the thrust direction, as for every thruster) is locked to {@code UP}.
 *
 * <p>The {@code FACING} property itself is kept so the shared thruster code
 * (shape, obstruction scan, particles) keeps working unchanged, and so a
 * contraption rotation that does tip the block over still renders and
 * behaves consistently: {@link HoverpadBlockEntity} works in terms of
 * {@code FACING}, not a hardcoded "down".
 */
public class HoverpadBlock extends AbstractThrusterBlock {

    public HoverpadBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, Direction.UP);
    }

    @Override
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new HoverpadBlockEntity(PropulsionBlockEntities.HOVERPAD_BLOCK_ENTITY.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@Nonnull Level level,
                                                                  @Nonnull BlockState state,
                                                                  @Nonnull BlockEntityType<T> type) {
        if (type == PropulsionBlockEntities.HOVERPAD_BLOCK_ENTITY.get()) {
            return new SmartBlockEntityTicker<>();
        }
        return null;
    }
}
