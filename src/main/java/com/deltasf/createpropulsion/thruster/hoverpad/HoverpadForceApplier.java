package com.deltasf.createpropulsion.thruster.hoverpad;

import com.deltasf.createpropulsion.PropulsionConfig;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.physics.RayCastResult;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.core.api.world.PhysLevel;

import javax.annotation.Nullable;

/**
 * Physics-thread half of a Hoverpad.
 *
 * <p>Every physics tick the pad raycasts along its axis (skipping its own
 * ship) and behaves like a spring-damper "suspension" against whatever it
 * hits: lift is strongest when the pad touches the ground, falls linearly to
 * zero at the target height, and a damping term opposes the pad's velocity
 * relative to the ground so the ship settles instead of bouncing. A ship too
 * heavy for its pads simply settles lower; above the target height there is
 * no lift at all, so pads can never sustain flight.
 *
 * <p>Doing this on the physics thread (rather than updating a thrust value
 * every few game ticks like other thrusters) is what makes it stable: the
 * spring is evaluated at physics rate with up-to-date distances and
 * velocities.
 *
 * <p>A pure "full lift at the ground" spring launches light ships: a ship
 * far lighter than the pad's max lift sits on a hugely compressed spring and
 * gets catapulted well above the target height. So, relative to the load
 * the pad carries, the spring is softened (a light ship still settles within
 * {@link #MAX_SAG} of the target), lift is capped at {@link #MAX_LIFT_G}
 * times that load's weight, and the damper may briefly pull the ship back
 * down (up to {@link #MAX_BRAKE_G}) while it rises through the hover zone.
 * Heavy ships are unaffected by these limits.
 *
 * <p>When the ground is another (non-static) ship, an equal and opposite
 * force is applied to it. Besides being correct, this closes the obvious
 * exploit of "hovering" on a platform that is attached to your own ship.
 *
 * <p>Usage is two-pass, driven by {@link HoverpadAttachment}: {@link #sense}
 * on every pad first, so the ship's mass can be split across the pads that
 * are actually bearing load, then {@link #apply}.
 */
public class HoverpadForceApplier {
    /** Distance from the block centre to its bottom face, in blocks. */
    private static final double EMITTER_OFFSET = 0.5;
    /** Fluid surfaces come from the game thread and can be a tick old;
     *  ignore one if the pad has since moved this far (squared, blocks²)
     *  from where it was sampled. */
    private static final double MAX_FLUID_SURFACE_DRIFT_SQ = 8.0 * 8.0;
    /** Cap on the spring's natural frequency relative to the physics step
     *  (omega * dt). Very light ships on stiff (short) springs would
     *  otherwise oscillate or explode numerically. */
    private static final double MAX_OMEGA_DT = 0.5;
    /** Only used to size the limits below, not to simulate anything. */
    private static final double GRAVITY = 10.0;
    /** How far below the target height (blocks) a very light ship settles:
     *  this, or {@link #SAG_FRACTION} of the target height if smaller. */
    private static final double MAX_SAG = 0.4;
    private static final double SAG_FRACTION = 0.1;
    /** Max lift as a multiple of the weight a pad carries. */
    private static final double MAX_LIFT_G = 3.0;
    /** Max downward pull from the damper, as a multiple of that weight. */
    private static final double MAX_BRAKE_G = 2.0;

    private final BlockPos pos;
    private final HoverpadData data;

    // Results of sense(), consumed by apply(). Physics thread only.
    private double groundDistance = HoverpadData.NO_GROUND;
    @Nullable
    private PhysShip groundShip = null;
    private final Vector3d origin = new Vector3d();
    private final Vector3d rayWorld = new Vector3d();
    private final Vector3d hitPoint = new Vector3d();

    // Scratch, physics thread only
    private final Vector3d padVelocity = new Vector3d();
    private final Vector3d groundVelocity = new Vector3d();
    private final Vector3d lever = new Vector3d();
    private final Vector3d force = new Vector3d();
    private final Vector3d relativePos = new Vector3d();

    public HoverpadForceApplier(BlockPos pos, HoverpadData data) {
        this.pos = pos.immutable();
        this.data = data;
    }

    /** Measures the distance to the ground. Returns true if the pad is
     *  active and has ground within its target height. */
    boolean sense(PhysShip ship, PhysLevel level) {
        groundDistance = HoverpadData.NO_GROUND;
        groundShip = null;

        double targetHeight = data.getTargetHeight();
        if (!data.isActive() || targetHeight <= 0) return false;

        ShipTransform transform = ship.getTransform();
        double scale = transform.getShipToWorldScaling().x();
        origin.set(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        transform.getShipToWorld().transformPosition(origin);
        transform.getShipToWorld().transformDirection(data.getRayDirection(), rayWorld);
        if (rayWorld.lengthSquared() < 1e-12) return false;
        rayWorld.normalize();

        double range = (EMITTER_OFFSET + targetHeight) * scale;
        double nearest = Double.POSITIVE_INFINITY;

        RayCastResult hit = level.rayCast(origin, rayWorld, range, ship.getId());
        if (hit != null && hit.getDistance() <= range) {
            nearest = Math.max(0.0, hit.getDistance());
            PhysShip body = hit.getHitBody();
            if (body != null && !body.isStatic() && body.getId() != ship.getId()) {
                groundShip = body;
            }
        }

        HoverpadData.FluidSurface fluid = data.getFluidSurface();
        if (fluid != null) {
            double fluidDistance = intersectFluid(fluid);
            if (fluidDistance >= 0 && fluidDistance < nearest) {
                nearest = fluidDistance;
                groundShip = null;
            }
        }

        if (nearest > range) {
            groundShip = null;
            return false;
        }

        hitPoint.set(rayWorld).mul(nearest).add(origin);
        groundDistance = Math.max(0.0, nearest / scale - EMITTER_OFFSET);
        return true;
    }

    /** Ray/plane intersection with the cached fluid surface. Returns the
     *  distance along the ray, or -1 if it doesn't apply. */
    private double intersectFluid(HoverpadData.FluidSurface fluid) {
        Vector3dc n = fluid.normal();
        Vector3dc p = fluid.point();
        double denom = rayWorld.dot(n);
        if (denom > -1e-3) return -1; // ray parallel to or leaving the surface
        double t = ((p.x() - origin.x) * n.x() + (p.y() - origin.y) * n.y() + (p.z() - origin.z) * n.z()) / denom;
        if (t < 0) return -1;
        double hx = origin.x + rayWorld.x * t - p.x();
        double hz = origin.z + rayWorld.z * t - p.z();
        if (hx * hx + hz * hz > MAX_FLUID_SURFACE_DRIFT_SQ) return -1;
        return t;
    }

    /**
     * Applies lift for the distance measured by {@link #sense}.
     *
     * @param massShare the part of the ship's mass this pad is expected to
     *                  carry (ship mass / pads in range); sets how much
     *                  damping is needed
     * @param dt        physics step in seconds
     */
    void apply(PhysShip ship, double massShare, double dt) {
        double distance = groundDistance;
        PhysShip ground = groundShip;
        groundDistance = HoverpadData.NO_GROUND;
        groundShip = null;

        double targetHeight = data.getTargetHeight();
        if (distance < 0 || targetHeight <= 0) {
            data.recordLift(0.0, HoverpadData.NO_GROUND);
            return;
        }

        ShipTransform transform = ship.getTransform();
        Vector3dc scaling = transform.getShipToWorldScaling();
        double scale = scaling.x();
        double massScale = scaling.x() * scaling.y() * scaling.z();
        double maxLift = data.getMaxLift() * massScale;
        double fullLift = data.getFullLift() * massScale;
        if (maxLift <= 0 || fullLift <= 0 || massShare <= 0) {
            data.recordLift(0.0, distance);
            return;
        }

        // Closing speed: how fast the pad approaches the ground along its ray
        // (positive = getting closer), relative to the ground's own motion.
        pointVelocity(ship, origin, padVelocity);
        if (ground != null) {
            pointVelocity(ground, hitPoint, groundVelocity);
        } else {
            groundVelocity.zero();
        }
        double closingSpeed = padVelocity.sub(groundVelocity).dot(rayWorld);

        // Spring: zero lift at the target height, rising linearly towards
        // the ground (max lift at contact, unless softened for light loads).
        double compression = (targetHeight - distance) * scale;
        double sag = Math.min(MAX_SAG, SAG_FRACTION * targetHeight) * scale;
        double maxOmega = MAX_OMEGA_DT / dt;
        double stiffness = Math.min(maxLift / (targetHeight * scale),
                massShare * Math.min(GRAVITY / sag, maxOmega * maxOmega));
        // Damper: scaled to the load so the configured ratio means the same
        // thing for a skiff and a barge (1.0 = critically damped).
        double damping = 2.0 * PropulsionConfig.HOVERPAD_DAMPING.get() * Math.sqrt(stiffness * massShare);
        damping = Math.min(damping, massShare / dt);

        double weight = massShare * GRAVITY;
        double lift = stiffness * compression + damping * closingSpeed;
        lift = Math.max(-weight * MAX_BRAKE_G, Math.min(lift, Math.min(maxLift, weight * MAX_LIFT_G)));
        data.recordLift(Math.abs(lift) / fullLift, distance);
        if (lift == 0) return;

        if (PropulsionConfig.HOVERPAD_PUSH_STRAIGHT_UP.get()) {
            force.set(0, lift, 0);
        } else {
            force.set(rayWorld).mul(-lift);
        }

        relativePos.set(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5).sub(transform.getPositionInShip());
        ship.applyInvariantForceToPos(force, relativePos);

        if (ground != null) {
            ShipTransform groundTransform = ground.getTransform();
            groundTransform.getWorldToShip().transformPosition(hitPoint, relativePos).sub(groundTransform.getPositionInShip());
            ground.applyInvariantForceToPos(force.negate(), relativePos);
        }
    }

    /** Velocity of a world-space point rigidly attached to a ship. */
    private Vector3d pointVelocity(PhysShip ship, Vector3dc worldPoint, Vector3d out) {
        lever.set(worldPoint).sub(ship.getTransform().getPositionInWorld());
        ship.getAngularVelocity().cross(lever, out);
        return out.add(ship.getVelocity());
    }
}
