package com.deltasf.createpropulsion.thruster.thruster;

import com.deltasf.createpropulsion.PropulsionConfig;
import com.deltasf.createpropulsion.compat.PropulsionCompatibility;
import com.deltasf.createpropulsion.thruster.AbstractThrusterBlockEntity;
import com.deltasf.createpropulsion.thruster.FluidThrusterProperties;
import com.deltasf.createpropulsion.thruster.ThrusterFuelManager;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;
import java.util.List;

public class ThrusterBlockEntity extends AbstractThrusterBlockEntity {
    public static final float BASE_FUEL_CONSUMPTION = 2;
    public static final int BASE_MAX_THRUST = 600000;
    //Fuel tank: accessed from the FACING side (front). Unchanged from single-block setup.
    public SmartFluidTankBehaviour tank;
    //Oxidizer tank: accessed from FACING.getOpposite() (rear). Consumed alongside fuel
    //once Multiblock Thruster logic lands; for now it stores but is not drained.
    public SmartFluidTankBehaviour oxidizerTank;

    public ThrusterBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        tank = SmartFluidTankBehaviour.single(this, 200);
        behaviours.add(tank);
        //INPUT (not the default TYPE) gives the oxidizer tank a distinct behaviour key,
        //so its NBT ("Input") and behaviour lookup do not collide with the fuel tank's ("").
        //Matches the pattern used by Create's BasinBlockEntity for input/output tanks.
        oxidizerTank = new SmartFluidTankBehaviour(SmartFluidTankBehaviour.INPUT, this, 1, 200, false);
        behaviours.add(oxidizerTank);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            if (side == getFluidCapSide()) {
                return tank.getCapability().cast();
            }
            if (side == getOxidizerCapSide()) {
                return oxidizerTank.getCapability().cast();
            }
        }
        if (PropulsionCompatibility.CC_ACTIVE && computerBehaviour.isPeripheralCap(cap)) {
            return computerBehaviour.getPeripheralCapability().cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void updateThrust(BlockState currentBlockState) {
        float thrust = 0;
        float currentPower = getPower();

        //This thruster only works if it has valid fuel and power
        if (isWorking() && currentPower > 0) {
            var properties = getFuelProperties(fluidStack().getRawFluid());
            float obstructionEffect = calculateObstructionEffect();
            float thrustPercentage = Math.min(currentPower, obstructionEffect);

            if (thrustPercentage > 0 && properties != null) {
                int tick_rate = PropulsionConfig.THRUSTER_TICKS_PER_UPDATE.get();
                int consumption = calculateFuelConsumption(currentPower, properties.consumptionMultiplier, tick_rate);
                FluidStack drainedStack = tank.getPrimaryHandler().drain(consumption, IFluidHandler.FluidAction.EXECUTE);
                int fuelConsumed = drainedStack.getAmount();

                if (fuelConsumed > 0) {
                    float consumptionRatio = (float) fuelConsumed / (float) consumption;
                    float thrustMultiplier = PropulsionConfig.THRUSTER_THRUST_MULTIPLIER.get().floatValue();
                    thrust = BASE_MAX_THRUST * thrustMultiplier * thrustPercentage * properties.thrustMultiplier * consumptionRatio;
                }
            }
        }
        thrusterData.setThrust(thrust);
        isThrustDirty = false;
    }

    @Override
    protected boolean isWorking() {
        return validFluid();
    }

    protected Direction getFluidCapSide() {
        return getBlockState().getValue(ThrusterBlock.FACING);
    }

    //Oxidizer feeds in from the rear (opposite the nozzle/FACING), which is
    //the natural plumbing direction for propellants and keeps it away from the
    //fuel inlet to avoid mis-filling on an empty thruster.
    protected Direction getOxidizerCapSide() {
        return getBlockState().getValue(ThrusterBlock.FACING).getOpposite();
    }

    @Override
    protected double getNozzleOffsetFromCenter() {
        return 0.95;
    }

    @Override
    protected LangBuilder getGoggleStatus() {
        if (fluidStack().isEmpty()) {
            return CreateLang.builder().add(Component.translatable("createpropulsion.gui.goggles.thruster.status.no_fuel")).style(ChatFormatting.RED);
        } else if (!validFluid()) {
            return CreateLang.builder().add(Component.translatable("createpropulsion.gui.goggles.thruster.status.wrong_fuel")).style(ChatFormatting.RED);
        } else if (!isPowered()) {
            return CreateLang.builder().add(Component.translatable("createpropulsion.gui.goggles.thruster.status.not_powered")).style(ChatFormatting.GOLD);
        } else if (emptyBlocks == 0) {
            return CreateLang.builder().add(Component.translatable("createpropulsion.gui.goggles.thruster.obstructed")).style(ChatFormatting.RED);
        } else {
            return CreateLang.builder().add(Component.translatable("createpropulsion.gui.goggles.thruster.status.working")).style(ChatFormatting.GREEN);
        }
    }

    @Override
    protected void addThrusterDetails(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addThrusterDetails(tooltip, isPlayerSneaking);
        containedFluidTooltip(tooltip, isPlayerSneaking, tank.getCapability().cast());
        containedFluidTooltip(tooltip, isPlayerSneaking, oxidizerTank.getCapability().cast());
    }

    public FluidStack fluidStack() {
        return tank.getPrimaryHandler().getFluid();
    }

    public FluidStack oxidizerStack() {
        return oxidizerTank.getPrimaryHandler().getFluid();
    }

    public boolean validFluid() {
        if (fluidStack().isEmpty()) return false;
        return getFuelProperties(fluidStack().getRawFluid()) != null;
    }

    //Hook for multiblock thrusters to gate operation on oxidizer presence.
    //Single-block thrusters do not consume oxidizer and so do not call this yet.
    public boolean validOxidizer() {
        return !oxidizerStack().isEmpty();
    }

    public FluidThrusterProperties getFuelProperties(Fluid fluid) {
        return ThrusterFuelManager.getProperties(fluid);
    }

    private int calculateFuelConsumption(float powerPercentage, float fluidPropertiesConsumptionMultiplier, int tick_rate) {
        float base_consumption = BASE_FUEL_CONSUMPTION * PropulsionConfig.THRUSTER_CONSUMPTION_MULTIPLIER.get().floatValue();
        return (int) Math.ceil(base_consumption * powerPercentage * fluidPropertiesConsumptionMultiplier * tick_rate);
    }
}