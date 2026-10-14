package com.deltasf.createpropulsion.thruster.hoverpad;

import com.deltasf.createpropulsion.utility.AttachmentUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.ships.ShipPhysicsListener;
import org.valkyrienskies.core.api.world.PhysLevel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-ship physics listener driving every Hoverpad on that ship.
 *
 * <p>Registered with a transient serializer (see {@code PropulsionValkyrien}):
 * appliers hold live references to their block entity's state, so instead of
 * being saved with the ship they are re-registered by each
 * {@link HoverpadBlockEntity} when it loads, and removed when it unloads.
 */
public final class HoverpadAttachment implements ShipPhysicsListener {
    private static final double DEFAULT_DT = 1.0 / 60.0;

    private final Map<Long, HoverpadForceApplier> appliers = new ConcurrentHashMap<>();

    public HoverpadAttachment() {}

    @Override
    public void physTick(@NotNull PhysShip ship, @NotNull PhysLevel level) {
        physTick(ship, level, DEFAULT_DT);
    }

    @Override
    public void physTick(@NotNull PhysShip ship, @NotNull PhysLevel level, double delta) {
        if (appliers.isEmpty()) return;
        double dt = (delta > 1e-4 && delta < 0.25) ? delta : DEFAULT_DT;

        // Pass 1: find which pads are actually bearing load, so the ship's
        // mass can be shared between them when sizing each pad's damping.
        int padsInRange = 0;
        for (HoverpadForceApplier applier : appliers.values()) {
            if (applier.sense(ship, level)) padsInRange++;
        }

        double massShare = ship.getMass() / Math.max(1, padsInRange);
        for (HoverpadForceApplier applier : appliers.values()) {
            applier.apply(ship, massShare, dt);
        }
    }

    public void addApplier(BlockPos pos, HoverpadForceApplier applier) {
        appliers.put(pos.asLong(), applier);
    }

    /** Removes the applier at {@code pos}, but only if it is still
     *  {@code expected}: a newer block entity at the same position may have
     *  registered its own in the meantime. */
    public void removeApplier(BlockPos pos, HoverpadForceApplier expected) {
        appliers.remove(pos.asLong(), expected);
    }

    public static HoverpadAttachment get(Level level, BlockPos pos) {
        return AttachmentUtils.get(level, pos, HoverpadAttachment.class, HoverpadAttachment::new);
    }
}
