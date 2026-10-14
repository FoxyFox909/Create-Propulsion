package com.deltasf.createpropulsion.thruster.hoverpad;

import com.deltasf.createpropulsion.PropulsionConfig;
import com.deltasf.createpropulsion.compat.PropulsionCompatibility;
import com.deltasf.createpropulsion.compat.computercraft.ComputerBehaviour;
import com.deltasf.createpropulsion.particles.ParticleTypes;
import com.deltasf.createpropulsion.particles.plasma.PlasmaParticleData;
import com.deltasf.createpropulsion.thruster.AbstractThrusterBlock;
import com.deltasf.createpropulsion.thruster.AbstractThrusterBlockEntity;
import com.deltasf.createpropulsion.thruster.ion.IonThrusterBlockEntity.IonEnergyStorage;
import com.deltasf.createpropulsion.thruster.thruster.ThrusterBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Hoverpad block entity: an energy-powered pad that holds a ship at a set
 * height above whatever is below it (terrain, fluids or other ships), but
 * gives no lift above that height, so it allows hovering and never flight.
 *
 * <p>The hover itself is computed on the physics thread by
 * {@link HoverpadForceApplier}; this class is the game-thread half:
 * <ul>
 *   <li>turns redstone (or CC {@code setPower}) into a target height:
 *       signal / 15 of the configured max, 0 = off;</li>
 *   <li>bills Forge Energy for the lift the physics thread actually
 *       produced, and throttles lift when the buffer can't keep up;</li>
 *   <li>probes for fluid surfaces, which the physics raycast can't see;</li>
 *   <li>syncs ground distance and lift to clients for goggles.</li>
 * </ul>
 *
 * <p>Energy is accepted on every face except the emitter, like the Ion
 * Thruster, and can't be extracted.
 */
public class HoverpadBlockEntity extends AbstractThrusterBlockEntity {
    /** Lift of one pad touching the ground at multiplier 1.0. Matches the
     *  fluid and ion thrusters' full thrust. */
    public static final int BASE_MAX_LIFT = ThrusterBlockEntity.BASE_MAX_THRUST;

    private static final int SYNC_INTERVAL_TICKS = 5;
    /** Longest billing window; caps the catch-up bill after the block entity
     *  hasn't ticked for a while (e.g. its chunk stopped ticking). */
    private static final long MAX_BILLING_TICKS = 100;

    private final HoverpadData hoverData = new HoverpadData();
    @Nullable
    private HoverpadForceApplier applier;

    // Energy. The real capacity is only known once the server config has
    // loaded, so the buffer starts as a 0-capacity placeholder and is resized
    // in initialize(); the saved amount is parked in pendingEnergy until then.
    private IonEnergyStorage energy;
    private LazyOptional<IEnergyStorage> energyCap;
    private int pendingEnergy = 0;

    private long lastBillingTick = -1;
    private double energyAvailability = 1.0;

    // Synced to clients for goggles, particles and sound
    private float groundDistance = (float) HoverpadData.NO_GROUND;
    private float liftFraction = 0.0f;

    private int lastBroadcastEnergy = -1;
    private float lastBroadcastDistance = Float.NaN;
    private float lastBroadcastLift = Float.NaN;
    private int syncCooldown = 0;

    public HoverpadBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        this.energy = new IonEnergyStorage(0, 0, this::setChanged);
        this.energyCap = LazyOptional.of(() -> energy);
    }

    @Override
    public void initialize() {
        int capacity = PropulsionConfig.HOVERPAD_CAPACITY.get();
        int maxReceive = PropulsionConfig.HOVERPAD_RF_PER_TICK.get()
                * PropulsionConfig.THRUSTER_TICKS_PER_UPDATE.get() * 4;
        if (energy.getMaxEnergyStored() != capacity) {
            if (energyCap != null) energyCap.invalidate();
            energy = new IonEnergyStorage(capacity, maxReceive, this::setChanged);
            energy.setEnergy(Math.min(pendingEnergy, capacity));
            energyCap = LazyOptional.of(() -> energy);
        }
        super.initialize();
    }

    @Override
    protected void attachToShip(BlockState state) {
        HoverpadAttachment ship = HoverpadAttachment.get(level, worldPosition);
        if (ship == null) return;
        Direction emitter = state.getValue(AbstractThrusterBlock.FACING).getOpposite();
        hoverData.setRayDirection(VectorConversionsMCKt.toJOMLD(emitter.getNormal()));
        applier = new HoverpadForceApplier(worldPosition, hoverData);
        ship.addApplier(worldPosition, applier);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // No ThrusterDamager: a hover field doesn't set things below on fire.
        if (PropulsionCompatibility.CC_ACTIVE) {
            behaviours.add(computerBehaviour = new ComputerBehaviour(this));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide() || isRemoved()) return;

        if (hoverData.isActive()) {
            probeFluidSurface();
        } else {
            hoverData.setFluidSurface(null);
        }
        groundDistance = (float) hoverData.getGroundDistance();

        // Throttled client sync: energy changes from cables never trigger a
        // block update on their own, and distance/lift change continuously.
        if (syncCooldown > 0) syncCooldown--;
        if (syncCooldown == 0 && needsSync()) {
            lastBroadcastEnergy = energy.getEnergyStored();
            lastBroadcastDistance = groundDistance;
            lastBroadcastLift = liftFraction;
            syncCooldown = SYNC_INTERVAL_TICKS;
            sendData();
        }
    }

    private boolean needsSync() {
        if (energy.getEnergyStored() != lastBroadcastEnergy) return true;
        if (Float.isNaN(lastBroadcastDistance)) return true;
        if ((groundDistance < 0) != (lastBroadcastDistance < 0)) return true;
        if (Math.abs(groundDistance - lastBroadcastDistance) > 0.1f) return true;
        return Math.abs(liftFraction - lastBroadcastLift) > 0.01f;
    }

    // =====================================================================
    // Hover control
    // =====================================================================

    @Override
    public void updateThrust(BlockState currentBlockState) {
        billEnergy();

        boolean active = isOnShip() && isPowered() && isWorking() && !isObstructed();
        double fullLift = BASE_MAX_LIFT * PropulsionConfig.HOVERPAD_LIFT_MULTIPLIER.get();
        hoverData.setTargetHeight(active ? getTargetHeight() : 0.0);
        hoverData.setLift(fullLift * energyAvailability, fullLift);
        hoverData.setActive(active);
        isThrustDirty = false;
    }

    /** Charges the buffer for the lift produced since the last update. If it
     *  couldn't cover the bill, lift is throttled by the same ratio until the
     *  next update, so an underpowered pad sags rather than cutting out. */
    private void billEnergy() {
        long now = level.getGameTime();
        long elapsed = lastBillingTick < 0 ? 0 : Math.min(now - lastBillingTick, MAX_BILLING_TICKS);
        lastBillingTick = now;

        double averageLift = hoverData.drainAverageLiftFraction();
        liftFraction = (float) averageLift;
        if (elapsed <= 0 || averageLift <= 0) {
            energyAvailability = 1.0;
            return;
        }
        int needed = (int) Math.ceil(PropulsionConfig.HOVERPAD_RF_PER_TICK.get() * averageLift * elapsed);
        int consumed = energy.consumeEnergy(needed, false);
        energyAvailability = needed > 0 ? (double) consumed / needed : 1.0;
    }

    /** Looks for a fluid surface below the pad. Fluids aren't part of the
     *  physics world, so the physics raycast passes straight through them;
     *  this hands the physics thread a surface plane to hover on instead.
     *  Solid ground is ignored here; the physics raycast handles it. */
    @SuppressWarnings("null")
    private void probeFluidSurface() {
        Direction emitter = getBlockState().getValue(AbstractThrusterBlock.FACING).getOpposite();
        Vec3 from = Vec3.atCenterOf(worldPosition);
        Vec3 direction = Vec3.atLowerCornerOf(emitter.getNormal());
        double scale = 1.0;

        LoadedShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, worldPosition);
        if (ship != null) {
            Vector3d worldFrom = ship.getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(from));
            Vector3d worldDirection = ship.getShipToWorld().transformDirection(VectorConversionsMCKt.toJOML(direction));
            scale = ship.getTransform().getShipToWorldScaling().x();
            from = VectorConversionsMCKt.toMinecraft(worldFrom);
            direction = VectorConversionsMCKt.toMinecraft(worldDirection.normalize());
        }

        double range = (0.5 + hoverData.getTargetHeight()) * scale + 1.0;
        Vec3 to = from.add(direction.scale(range));
        // vanillaClip: world blocks and fluids only. Ship blocks live in the
        // shipyard, so this never sees its own ship (or any other).
        BlockHitResult hit = RaycastUtilsKt.vanillaClip(level,
                new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, null));

        HoverpadData.FluidSurface surface = null;
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = hit.getBlockPos();
            boolean isFluid = !level.getFluidState(hitPos).isEmpty()
                    && level.getBlockState(hitPos).getCollisionShape(level, hitPos).isEmpty();
            if (isFluid) {
                surface = new HoverpadData.FluidSurface(
                        VectorConversionsMCKt.toJOML(hit.getLocation()),
                        VectorConversionsMCKt.toJOMLD(hit.getDirection().getNormal()));
            }
        }
        hoverData.setFluidSurface(surface);
    }

    /** Hover height requested by redstone / CC, in blocks. */
    public double getTargetHeight() {
        return getPower() * PropulsionConfig.HOVERPAD_MAX_HEIGHT.get();
    }

    public double getMaxHoverHeight() {
        return PropulsionConfig.HOVERPAD_MAX_HEIGHT.get();
    }

    /** Ground distance in blocks from the pad's bottom face, or a negative
     *  value if there is no ground within the target height. */
    public float getGroundDistance() {
        return groundDistance;
    }

    /** Lift being produced, as a fraction (0..1) of a fully-fed pad's max. */
    public float getLiftFraction() {
        return liftFraction;
    }

    public boolean isOnShip() {
        return level != null && VSGameUtilsKt.isBlockInShipyard(level, worldPosition);
    }

    /** Pads need a clear column below them on their own ship, as deep as the
     *  max hover height. (The physics raycast ignores the pad's own ship, so
     *  without this a pad buried in the hull would still "see" the ground.) */
    public boolean isObstructed() {
        return emptyBlocks < getObstructionScanLength();
    }

    private int getObstructionScanLength() {
        return Math.max(1, (int) Math.ceil(PropulsionConfig.HOVERPAD_MAX_HEIGHT.get()));
    }

    @Override
    public void calculateObstruction(Level level, BlockPos pos, Direction forwardDirection) {
        int length = getObstructionScanLength();
        int oldEmptyBlocks = this.emptyBlocks;
        if (!VSGameUtilsKt.isBlockInShipyard(level, pos)) {
            // Off-ship, the blocks below are the ground itself, not an
            // obstruction. (The pad does nothing off-ship anyway.)
            emptyBlocks = length;
        } else {
            for (emptyBlocks = 0; emptyBlocks < length; emptyBlocks++) {
                BlockState state = level.getBlockState(pos.relative(forwardDirection.getOpposite(), emptyBlocks + 1));
                if (!(state.isAir() || !state.isSolid())) break;
            }
        }
        if (oldEmptyBlocks != this.emptyBlocks) {
            isThrustDirty = true;
        }
    }

    @Override
    protected boolean isWorking() {
        return energy.getEnergyStored() > 0;
    }

    private boolean isLifting() {
        return isPowered() && isWorking() && !isObstructed() && liftFraction > 0.005f;
    }

    @Override
    protected boolean shouldEmitParticles() {
        return isLifting();
    }

    @Override
    protected boolean shouldPlaySound() {
        return isLifting();
    }

    @Override
    protected double getNozzleOffsetFromCenter() {
        return 0.95;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ParticleOptions createParticleOptions() {
        return new PlasmaParticleData((ParticleType<PlasmaParticleData>) ParticleTypes.getPlasmaType());
    }

    // =====================================================================
    // Energy storage & capabilities
    // =====================================================================

    public int getEnergyStored() {
        return energy.getEnergyStored();
    }

    public int getMaxEnergyStored() {
        return energy.getMaxEnergyStored();
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            if (side == null) return energyCap.cast();
            Direction facing = getBlockState().getValue(AbstractThrusterBlock.FACING);
            if (side != facing.getOpposite()) return energyCap.cast();
            return LazyOptional.empty();
        }
        if (PropulsionCompatibility.CC_ACTIVE && computerBehaviour.isPeripheralCap(cap)) {
            return computerBehaviour.getPeripheralCapability().cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (energyCap != null) {
            energyCap.invalidate();
        }
        // Called on removal and on chunk unload: stop pushing the ship either
        // way. A reloaded block entity re-registers in initialize().
        if (level != null && !level.isClientSide() && applier != null) {
            HoverpadAttachment ship = HoverpadAttachment.get(level, worldPosition);
            if (ship != null) {
                ship.removeApplier(worldPosition, applier);
            }
            applier = null;
        }
    }

    // =====================================================================
    // Goggles
    // =====================================================================

    @Override
    protected LangBuilder getGoggleStatus() {
        if (!isOnShip()) {
            return CreateLang.builder()
                    .add(Component.translatable("createpropulsion.gui.goggles.hoverpad.status.needs_ship"))
                    .style(ChatFormatting.GOLD);
        }
        if (!isWorking()) {
            return CreateLang.builder()
                    .add(Component.translatable("createpropulsion.gui.goggles.ion_thruster.status.no_energy"))
                    .style(ChatFormatting.RED);
        }
        if (!isPowered()) {
            return CreateLang.builder()
                    .add(Component.translatable("createpropulsion.gui.goggles.thruster.status.not_powered"))
                    .style(ChatFormatting.GOLD);
        }
        if (isObstructed()) {
            return CreateLang.builder()
                    .add(Component.translatable("createpropulsion.gui.goggles.thruster.obstructed"))
                    .style(ChatFormatting.RED);
        }
        if (groundDistance < 0) {
            return CreateLang.builder()
                    .add(Component.translatable("createpropulsion.gui.goggles.hoverpad.status.no_ground"))
                    .style(ChatFormatting.GOLD);
        }
        return CreateLang.builder()
                .add(Component.translatable("createpropulsion.gui.goggles.hoverpad.status.hovering"))
                .style(ChatFormatting.GREEN);
    }

    @Override
    protected void addThrusterDetails(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.builder()
                .add(Component.translatable("createpropulsion.gui.goggles.hoverpad.target_height"))
                .text(": ")
                .add(CreateLang.number(getTargetHeight()))
                .space()
                .add(Component.translatable("createpropulsion.gui.goggles.hoverpad.unit_blocks"))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        if (groundDistance >= 0) {
            CreateLang.builder()
                    .add(Component.translatable("createpropulsion.gui.goggles.hoverpad.ground_distance"))
                    .text(": ")
                    .add(CreateLang.number(groundDistance))
                    .space()
                    .add(Component.translatable("createpropulsion.gui.goggles.hoverpad.unit_blocks"))
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip);
        }

        CreateLang.builder()
                .add(Component.translatable("createpropulsion.gui.goggles.hoverpad.lift"))
                .text(": ")
                .add(CreateLang.number(Math.round(liftFraction * 100)))
                .text("%")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.builder()
                .add(Component.translatable("createpropulsion.gui.goggles.ion_thruster.energy"))
                .text(": ")
                .add(CreateLang.number(energy.getEnergyStored()))
                .text(" / ")
                .add(CreateLang.number(energy.getMaxEnergyStored()))
                .space()
                .add(Component.translatable("createpropulsion.gui.goggles.ion_thruster.unit_fe"))
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip);
    }

    // =====================================================================
    // NBT
    // =====================================================================

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        compound.putInt("Energy", energy.getEnergyStored());
        if (clientPacket) {
            compound.putFloat("GroundDistance", groundDistance);
            compound.putFloat("Lift", liftFraction);
        }
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        pendingEnergy = compound.getInt("Energy");
        // Before initialize() the buffer is still the 0-capacity placeholder;
        // initialize() applies pendingEnergy then.
        if (energy.getMaxEnergyStored() > 0) {
            energy.setEnergy(Math.min(pendingEnergy, energy.getMaxEnergyStored()));
        }
        if (clientPacket) {
            groundDistance = compound.getFloat("GroundDistance");
            liftFraction = compound.getFloat("Lift");
        }
    }
}
