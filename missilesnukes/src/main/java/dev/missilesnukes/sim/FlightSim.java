package dev.missilesnukes.sim;

import dev.missilesnukes.sim.FlightState.Stage;

/**
 * Deterministic missile flight simulator. The same {@link #step} is used by the live server entity, by the in-game
 * dry run that checks range / predicts flight time, and by the guidance predictor, so all three always agree.
 *
 * Nothing here is scripted: thrust spools from fuel and mass flow, drag comes from air density and speed, liftoff
 * happens when thrust exceeds weight, cutoff is commanded by closed-loop guidance from a ballistic prediction,
 * engine separation happens when residual thrust has decayed, and warhead separation happens when the stack
 * passes apogee.
 */
public final class FlightSim {
    private FlightSim() {}

    // ---------------------------------------------------------------- mission setup

    /** Horizontal ground distance from the pad to the target. */
    public static double horizontalRange(double padX, double padZ, double tx, double tz) {
        return Math.hypot(tx - padX, tz - padZ);
    }

    /** Planned burnout elevation angle (from horizontal): steep for short shots, flatter for long ones. */
    public static double plannedBurnoutPitchFromVertical(double range, double maxRange) {
        double r0 = 80.0;
        double f = Math.log(Math.max(range, r0) / r0) / Math.log(Math.max(maxRange, r0 * 1.01) / r0);
        f = Math.max(0, Math.min(1, f));
        double elevation = Math.toRadians(78.0 - 36.0 * f);
        return Math.PI / 2 - elevation;
    }

    public static FlightState prepare(MissileSpec spec, double padX, double padY, double padZ, double tx, double ty, double tz) {
        FlightState s = new FlightState();
        s.spec = spec;
        s.fuel = spec.fuelCapacity;
        s.padX = padX; s.padY = padY; s.padZ = padZ;
        s.px = padX; s.py = padY; s.pz = padZ;
        s.tx = tx; s.ty = ty; s.tz = tz;
        double dx = tx - padX, dz = tz - padZ, d = Math.hypot(dx, dz);
        if (d > 1e-6) { s.azX = dx / d; s.azZ = dz / d; }
        s.thetaBurnout = plannedBurnoutPitchFromVertical(d, spec.maxRange);
        return s;
    }

    public static void ignite(FlightState s) {
        if (s.stage != Stage.PAD) return;
        s.stage = Stage.IGNITION;
        s.throttleCmd = 1.0;
        s.tick = 0;
    }

    // ---------------------------------------------------------------- physics

    public static double mass(FlightState s) {
        double m = s.spec.mWarhead;
        if (s.bodyAttached) m += s.spec.mBody + s.fuel;
        if (s.engineAttached) m += s.spec.mEngine;
        return m;
    }

    private static double cdA(FlightState s) {
        if (s.engineAttached) return s.spec.cdaStack;
        return s.bodyAttached ? s.spec.cdaBody : s.spec.cdaWarhead;
    }

    /** Advances the missile by one tick. Returns a bit mask of FlightState.EV_* events that happened. */
    public static int step(FlightState s, boolean guided) {
        int events = 0;
        if (s.stage == Stage.PAD || s.stage == Stage.IMPACT) return 0;

        // ---- closed-loop guidance runs at the start of the tick, before this tick's thrust is applied
        if (guided && s.stage == Stage.ASCENT && s.pitchStarted && s.fuel > 0 && s.tick >= s.nextPredictTick) {
            if (guide(s)) {                      // cut right now
                s.stage = Stage.CUTOFF; s.throttleCmd = 0; s.cutoffTick = s.tick + 1;
                events |= FlightState.EV_CUTOFF;
            }
        }

        s.tick++;
        final double[] n = new double[3];

        // ---- engine output: spool up, throttle slew, or exponential tail-off
        switch (s.stage) {
            case IGNITION -> s.thrustFrac = Math.min(1.0, s.thrustFrac + 1.0 / s.spec.ignitionTicks);
            case ASCENT -> {
                double d = s.throttleCmd - s.thrustFrac;
                s.thrustFrac += Math.max(-Flight.THROTTLE_SLEW, Math.min(Flight.THROTTLE_SLEW, d));
            }
            default -> s.thrustFrac *= Flight.TAIL_OFF;
        }
        boolean cutThisTick = false;
        if (s.cutFrac > 0 && s.stage == Stage.ASCENT) {   // engine shuts down part-way through this tick
            s.thrustFrac *= s.cutFrac; s.cutFrac = 0; cutThisTick = true;
        }
        if (!s.engineAttached || s.fuel <= 0) s.thrustFrac = 0;
        double thrust = s.thrustFrac * s.spec.thrust;
        double burned = Math.min(s.fuel, thrust / s.spec.exhaustVelocity);
        s.fuel -= burned;

        double m = mass(s);
        s.q.noseAxis(n);
        double ax = thrust / m * n[0];
        double ay = thrust / m * n[1] - Flight.G;
        double az = thrust / m * n[2];

        // ---- aerodynamic drag opposes velocity
        double speed = s.speed();
        double k = cdA(s) * Atmosphere.density(s.py) / m;
        ax -= k * speed * s.vx; ay -= k * speed * s.vy; az -= k * speed * s.vz;

        // ---- sitting on the pad: held down until thrust exceeds weight (dynamic liftoff)
        if (s.stage == Stage.IGNITION) {
            double axial = thrust / m;
            if (axial <= Flight.G * 1.001) {
                s.px = s.padX; s.py = s.padY; s.pz = s.padZ; s.vx = s.vy = s.vz = 0;
                return events;
            }
            s.stage = Stage.ASCENT;
            s.liftoffTick = s.tick;
            events |= FlightState.EV_LIFTOFF;
        }

        double prevVy = s.vy;
        s.px += s.vx + 0.5 * ax; s.py += s.vy + 0.5 * ay; s.pz += s.vz + 0.5 * az;
        s.vx += ax; s.vy += ay; s.vz += az;
        speed = s.speed();
        if (speed > s.maxSpeed) s.maxSpeed = speed;
        if (s.py > s.apogeeY) { s.apogeeY = s.py; s.apogeeTick = s.tick; }

        // ---- stage logic and attitude control
        switch (s.stage) {
            case ASCENT -> {
                double h = s.py - s.padY;
                if (!s.pitchStarted && h >= s.spec.towerClear && speed >= Flight.PITCH_SPEED) s.pitchStarted = true;
                if (s.pitchStarted) s.pitchAngle = Math.min(s.thetaBurnout, s.pitchAngle + Flight.PITCH_RATE);
                double sn = Math.sin(s.pitchAngle), cs = Math.cos(s.pitchAngle);
                s.q.turnNoseTowards(s.azX * sn, cs, s.azZ * sn, Flight.ATT_MAX_RATE, Flight.ATT_BLEND);

                boolean cut = cutThisTick;
                if (s.fuel <= 0) { s.shortfall = true; cut = true; }
                if (cut) {
                    s.stage = Stage.CUTOFF; s.throttleCmd = 0; s.cutoffTick = s.tick;
                    events |= FlightState.EV_CUTOFF;
                }
            }
            case CUTOFF -> {
                // The latches may only release once residual thrust is gone AND the aerodynamic load is low enough
                // (dynamic pressure q = 1/2 * rho * v^2), so a shot that cuts off low and fast waits longer.
                double q = 0.5 * Atmosphere.density(s.py) * speed * speed;
                if (s.thrustFrac < 0.01 && s.tick - s.cutoffTick >= Flight.SEP_LATCH_TICKS && q <= s.spec.sepMaxDynamicPressure) {
                    separateEngine(s);
                    events |= FlightState.EV_ENGINE_SEP;
                }
            }
            case MIDCOURSE -> {
                if (prevVy > 0 && s.vy <= 0) events |= FlightState.EV_APOGEE;
                if (s.vy <= 0 && s.tick - s.engineSepTick >= Flight.WARHEAD_DWELL_TICKS) {
                    separateWarhead(s);
                    events |= FlightState.EV_WARHEAD_SEP;
                }
            }
            case TERMINAL ->
                s.q.turnNoseTowards(s.vx, s.vy, s.vz, Flight.TERMINAL_FLIP_RATE, 0.2);
            default -> { }
        }
        return events;
    }

    private static void separateEngine(FlightState s) {
        double mRest = s.spec.mBody + s.fuel + s.spec.mWarhead;
        double mTot = mRest + s.spec.mEngine;
        double[] n = new double[3];
        s.q.noseAxis(n);
        double dvEngine = s.spec.sepVrelEngine * mRest / mTot;
        double dvRest = s.spec.sepVrelEngine * s.spec.mEngine / mTot;
        double[] c = new double[3];
        s.q.rotate(s.spec.centroidEngine[0], s.spec.centroidEngine[1], s.spec.centroidEngine[2], c);
        s.sepX = s.px + c[0]; s.sepY = s.py + c[1]; s.sepZ = s.pz + c[2];
        s.sepVx = s.vx - n[0] * dvEngine; s.sepVy = s.vy - n[1] * dvEngine; s.sepVz = s.vz - n[2] * dvEngine;
        s.sepQ.set(s.q);
        s.vx += n[0] * dvRest; s.vy += n[1] * dvRest; s.vz += n[2] * dvRest;
        s.engineAttached = false;
        s.stage = Stage.MIDCOURSE;
        s.engineSepTick = s.tick;
        s.thrustFrac = 0;
    }

    private static void separateWarhead(FlightState s) {
        double mBody = s.spec.mBody + s.fuel;
        double mTot = mBody + s.spec.mWarhead;
        double[] n = new double[3];
        s.q.noseAxis(n);
        double dvWar = s.spec.sepVrelWarhead * mBody / mTot;
        double dvBody = s.spec.sepVrelWarhead * s.spec.mWarhead / mTot;
        double[] c = new double[3];
        s.q.rotate(s.spec.centroidBody[0], s.spec.centroidBody[1], s.spec.centroidBody[2], c);
        s.sepX = s.px + c[0]; s.sepY = s.py + c[1]; s.sepZ = s.pz + c[2];
        s.sepVx = s.vx - n[0] * dvBody; s.sepVy = s.vy - n[1] * dvBody; s.sepVz = s.vz - n[2] * dvBody;
        s.sepQ.set(s.q);
        s.vx += n[0] * dvWar; s.vy += n[1] * dvWar; s.vz += n[2] * dvWar;
        // re-base the simulated position onto the warhead's own centre so that it pivots about itself
        s.q.rotate(s.spec.centroidWarhead[0], s.spec.centroidWarhead[1], s.spec.centroidWarhead[2], c);
        s.px += c[0]; s.py += c[1]; s.pz += c[2];
        s.bodyAttached = false;
        s.warheadPivot = true;
        s.stage = Stage.TERMINAL;
        s.warheadSepTick = s.tick;
    }

    // ---------------------------------------------------------------- guidance

    /**
     * Closed-loop cutoff guidance, evaluated at the start of a tick with the engine burning at full output.
     * Uses the real simulator to predict where the warhead would land if the engine were cut:
     *  - if cutting right now already reaches the target, cut now;
     *  - if cutting after this whole tick would overshoot, bisect the fraction of this tick's thrust that lands
     *    exactly on the target (sub-tick cutoff trim), so the result does not depend on the tick granularity;
     *  - otherwise keep burning and schedule the next prediction no later than the estimated time of arrival.
     * Returns true when the engine must be cut immediately.
     */
    private static boolean guide(FlightState s) {
        double m0 = predictMiss(s);
        if (m0 >= 0) return true;

        FlightState probe = s.copy();
        step(probe, false);                       // one more tick of burning
        double m1 = predictMiss(probe);           // ... then cut

        if (m1 >= 0) {
            double lo = Flight.TAIL_OFF, hi = 1.0; // fraction of this tick's thrust; lo reproduces "cut right now"
            for (int i = 0; i < 40 && hi - lo > 1e-7; i++) {
                double mid = 0.5 * (lo + hi);
                FlightState c = s.copy();
                c.cutFrac = mid;
                step(c, false);
                double mm = predictMiss(c);
                if (Math.abs(mm) < Flight.GUIDANCE_TOLERANCE) { lo = hi = mid; break; }
                if (mm < 0) lo = mid; else hi = mid;
            }
            s.cutFrac = 0.5 * (lo + hi);
            return false;
        }

        double gainPerTick = m1 - m0;
        long skip = 0;
        if (gainPerTick > 1e-9) skip = (long) Math.max(0, Math.min(10, Math.floor(0.2 * (-m0) / gainPerTick) - 1));
        s.nextPredictTick = s.tick + 1 + skip;
        return false;
    }

    /**
     * Predicts the along-track miss distance (blocks, negative = falls short) if the engine were cut right now.
     * Runs the real step() on a copy until the warhead descends through the target altitude.
     */
    public static double predictMiss(FlightState live) {
        FlightState p = live.copy();
        p.throttleCmd = 0;
        if (p.stage == Stage.ASCENT) { p.stage = Stage.CUTOFF; p.cutoffTick = p.tick; }
        double[] hit = new double[3];
        if (!runToTargetPlane(p, hit)) return -1e9;
        double sImp = (hit[0] - p.padX) * p.azX + (hit[2] - p.padZ) * p.azZ;
        double sTgt = (p.tx - p.padX) * p.azX + (p.tz - p.padZ) * p.azZ;
        return sImp - sTgt;
    }

    private static boolean runToTargetPlane(FlightState p, double[] hit) {
        double prevY = p.py;
        for (int i = 0; i < Flight.MAX_STEPS; i++) {
            double x0 = p.px, z0 = p.pz;
            step(p, false);
            if (p.vy < 0 && prevY > p.ty && p.py <= p.ty) {
                double f = (prevY - p.ty) / (prevY - p.py);
                hit[0] = x0 + (p.px - x0) * f; hit[1] = p.ty; hit[2] = z0 + (p.pz - z0) * f;
                return true;
            }
            prevY = p.py;
        }
        return false;
    }

    // ---------------------------------------------------------------- dry run

    public static final class Plan {
        public boolean feasible;
        public String reason = "";
        public double range, apogeeAboveTarget, impactErrorAlong, impactErrorCross, maxSpeed, fuelLeft;
        public long burnTicks, flightTicks, cutoffTick, engineSepTick, warheadSepTick;
        public long ticksToImpact() { return flightTicks; }
    }

    /** Runs a complete flight offline (target plane impact) to check feasibility and predict timings. */
    public static Plan dryRun(MissileSpec spec, double padX, double padY, double padZ, double tx, double ty, double tz) {
        double maxRange = spec.maxRange;
        Plan plan = new Plan();
        plan.range = horizontalRange(padX, padZ, tx, tz);
        if (plan.range < Flight.MIN_RANGE) { plan.reason = "TOO CLOSE (min " + (int) Flight.MIN_RANGE + ")"; return plan; }
        if (plan.range > maxRange + 0.5) { plan.reason = "OUT OF RANGE (max " + (int) maxRange + ")"; return plan; }
        FlightState s = prepare(spec, padX, padY, padZ, tx, ty, tz);
        ignite(s);
        double prevY = s.py;
        for (int i = 0; i < Flight.MAX_STEPS; i++) {
            double x0 = s.px, z0 = s.pz;
            step(s, true);
            if (s.stage != Stage.IGNITION && s.vy < 0 && prevY > ty && s.py <= ty) {
                double f = (prevY - ty) / (prevY - s.py);
                double hx = x0 + (s.px - x0) * f, hz = z0 + (s.pz - z0) * f;
                double along = (hx - padX) * s.azX + (hz - padZ) * s.azZ - ((tx - padX) * s.azX + (tz - padZ) * s.azZ);
                double cross = (hx - padX) * -s.azZ + (hz - padZ) * s.azX - ((tx - padX) * -s.azZ + (tz - padZ) * s.azX);
                plan.impactErrorAlong = along; plan.impactErrorCross = cross;
                plan.flightTicks = s.tick; plan.cutoffTick = s.cutoffTick; plan.engineSepTick = s.engineSepTick;
                plan.warheadSepTick = s.warheadSepTick; plan.maxSpeed = s.maxSpeed; plan.fuelLeft = s.fuel;
                plan.burnTicks = s.cutoffTick - s.liftoffTick;
                plan.apogeeAboveTarget = s.apogeeY - ty;
                plan.feasible = !s.shortfall && s.fuel > 0.5 && Math.abs(along) < 2.0;
                if (!plan.feasible) plan.reason = (s.shortfall || s.fuel <= 0.5) ? "OUT OF RANGE (fuel)" : "NO FIRING SOLUTION (target too high for this range)";
                return plan;
            }
            prevY = s.py;
        }
        plan.reason = "NO IMPACT SOLUTION";
        return plan;
    }
}
