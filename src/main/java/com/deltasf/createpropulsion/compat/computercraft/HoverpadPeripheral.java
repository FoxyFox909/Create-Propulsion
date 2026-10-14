package com.deltasf.createpropulsion.compat.computercraft;

import com.deltasf.createpropulsion.thruster.AbstractThrusterBlockEntity;
import com.deltasf.createpropulsion.thruster.hoverpad.HoverpadBlockEntity;
import com.simibubi.create.compat.computercraft.implementation.peripherals.SyncedPeripheral;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;

/**
 * ComputerCraft peripheral for the Hoverpad. {@code setPower} works like on
 * the other thrusters but sets the target hover height as a fraction of the
 * max ({@code setTargetHeight} does the same in blocks).
 */
public class HoverpadPeripheral extends SyncedPeripheral<HoverpadBlockEntity> {

    public HoverpadPeripheral(HoverpadBlockEntity blockEntity) {
        super(blockEntity);
    }

    @Override
    public final String getType() {
        return "propulsion_hoverpad";
    }

    @LuaFunction(mainThread = true)
    public final void setPower(double power) {
        blockEntity.setDigitalInput((float) power);
    }

    @LuaFunction(mainThread = true)
    public final float getPower() {
        return blockEntity.getPower();
    }

    /** Sets the target hover height in blocks (clamped to 0..max). */
    @LuaFunction(mainThread = true)
    public final void setTargetHeight(double height) {
        double max = blockEntity.getMaxHoverHeight();
        blockEntity.setDigitalInput((float) (max > 0 ? height / max : 0));
    }

    @LuaFunction(mainThread = true)
    public final double getTargetHeight() {
        return blockEntity.getTargetHeight();
    }

    @LuaFunction(mainThread = true)
    public final double getMaxHeight() {
        return blockEntity.getMaxHoverHeight();
    }

    /** Distance to the ground in blocks, or -1 if none is within the target height. */
    @LuaFunction(mainThread = true)
    public final double getGroundDistance() {
        return blockEntity.getGroundDistance();
    }

    /** Lift being produced, 0..1 of a fully-fed pad's max. */
    @LuaFunction(mainThread = true)
    public final double getLift() {
        return blockEntity.getLiftFraction();
    }

    @LuaFunction(mainThread = true)
    public final int getEnergyStored() {
        return blockEntity.getEnergyStored();
    }

    @LuaFunction(mainThread = true)
    public final int getMaxEnergyStored() {
        return blockEntity.getMaxEnergyStored();
    }

    @Override
    public boolean equals(IPeripheral other) {
        if (this == other) return true;
        if (other instanceof HoverpadPeripheral otherPad) {
            return this.blockEntity == otherPad.blockEntity;
        }
        return false;
    }

    @Override
    public void attach(@NotNull IComputerAccess computer) {
        super.attach(computer);
        blockEntity.setControlMode(AbstractThrusterBlockEntity.ControlMode.PERIPHERAL);
    }

    @Override
    public void detach(@NotNull IComputerAccess computer) {
        super.detach(computer);
        blockEntity.setDigitalInput(0.0f);
        blockEntity.setControlMode(AbstractThrusterBlockEntity.ControlMode.NORMAL);
    }
}
