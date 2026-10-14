package com.deltasf.createpropulsion.thruster.hoverpad;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * State shared between a {@link HoverpadBlockEntity} (game thread) and its
 * {@link HoverpadForceApplier} (physics thread).
 *
 * <p>Game thread → physics thread: whether the pad is allowed to lift, how
 * high it should hover, how much lift it may produce (already scaled by
 * energy availability), and the nearest fluid surface below it. Physics
 * thread → game thread: the lift it actually produced (for energy billing)
 * and the last measured ground distance (for goggles and CC).
 *
 * <p>Every field is either volatile or a concurrent accumulator, and vectors
 * are never mutated after being published, so neither side needs locking.
 */
public class HoverpadData {
    /** Sentinel for "no ground within range". */
    public static final double NO_GROUND = -1.0;

    /** A fluid surface found by the game-thread probe, in world space. Fluid
     *  blocks aren't part of the physics world, so the physics raycast can't
     *  see them; the physics thread intersects its ray with this plane
     *  instead, which stays accurate between game ticks. */
    public record FluidSurface(Vector3dc point, Vector3dc normal) {}

    // Game thread -> physics thread
    private volatile boolean active = false;
    private volatile double targetHeight = 0.0;
    private volatile double maxLift = 0.0;
    private volatile double fullLift = 0.0;
    private volatile Vector3dc rayDirection = new Vector3d(0, -1, 0);
    @Nullable
    private volatile FluidSurface fluidSurface = null;

    // Physics thread -> game thread
    private final DoubleAdder liftFractionSum = new DoubleAdder();
    private final LongAdder liftSamples = new LongAdder();
    private volatile double groundDistance = NO_GROUND;

    // ---- Game thread writes ----

    public void setActive(boolean active) { this.active = active; }

    /** Target hover height in blocks, measured from the pad's bottom face. */
    public void setTargetHeight(double targetHeight) { this.targetHeight = targetHeight; }

    /** @param maxLift  lift available right now (N), after energy shortages
     *  @param fullLift lift a fully-fed pad would have (N); lift fractions
     *                  reported back for billing are relative to this */
    public void setLift(double maxLift, double fullLift) {
        this.maxLift = maxLift;
        this.fullLift = fullLift;
    }

    /** Ray direction in ship space (unit vector, away from the emitter). */
    public void setRayDirection(Vector3dc rayDirection) { this.rayDirection = new Vector3d(rayDirection); }

    public void setFluidSurface(@Nullable FluidSurface fluidSurface) { this.fluidSurface = fluidSurface; }

    /** Average lift fraction (0..1 of full lift) produced since the last
     *  call, and resets the accumulator. 0 if physics hasn't ticked. */
    public double drainAverageLiftFraction() {
        double sum = liftFractionSum.sumThenReset();
        long samples = liftSamples.sumThenReset();
        return samples > 0 ? sum / samples : 0.0;
    }

    // ---- Physics thread writes ----

    void recordLift(double liftFraction, double groundDistance) {
        liftFractionSum.add(liftFraction);
        liftSamples.increment();
        this.groundDistance = groundDistance;
    }

    // ---- Reads ----

    public boolean isActive() { return active; }
    public double getTargetHeight() { return targetHeight; }
    public double getMaxLift() { return maxLift; }
    public double getFullLift() { return fullLift; }
    public Vector3dc getRayDirection() { return rayDirection; }
    @Nullable
    public FluidSurface getFluidSurface() { return fluidSurface; }

    /** Distance from the pad's bottom face to the ground in blocks, or
     *  {@link #NO_GROUND} if nothing is within range of the target height. */
    public double getGroundDistance() { return groundDistance; }
}
