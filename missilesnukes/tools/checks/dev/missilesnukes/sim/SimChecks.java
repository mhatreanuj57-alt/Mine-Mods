package dev.missilesnukes.sim;

import java.util.Random;

/** Self-checking test program (no JUnit): exits non-zero on failure. */
public class SimChecks {
    static int fails = 0;
    static MissileSpec SPEC = MissileSpec.LIGHT;
    static FlightSim.Plan dry(double padX, double padY, double padZ, double tx, double ty, double tz) { return FlightSim.dryRun(SPEC, padX, padY, padZ, tx, ty, tz); }
    static void check(boolean ok, String msg) { if (!ok) { fails++; System.out.println("FAIL: " + msg); } }

    public static void main(String[] args) {
        for (MissileSpec sp : MissileSpec.ALL) { SPEC = sp; System.out.println("########## " + sp.id.toUpperCase() + " ICBM (max range " + (int) sp.maxRange + ", liftoff accel " + String.format("%.4f", sp.liftoffAcceleration()) + " vs g " + Flight.G + ")"); run(); }
        System.out.println(fails == 0 ? "\nALL CHECKS PASSED (all missile types)" : "\n" + fails + " CHECK(S) FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }

    static void run() {
        final double MAX = SPEC.maxRange;
        Random rnd = new Random(12345);

        // 1. accuracy over random targets (mountains and valleys included), any direction
        int n = 400; double worst = 0; int notOk = 0; double sumErr = 0;
        for (int i = 0; i < n; i++) {
            double range = 30 + rnd.nextDouble() * (MAX - 31), az = rnd.nextDouble() * 2 * Math.PI;
            double padX = rnd.nextInt(20000) - 10000, padZ = rnd.nextInt(20000) - 10000, padY = 64 + rnd.nextInt(30);
            double ty = -30 + rnd.nextInt(280);
            FlightSim.Plan p = dry(padX, padY, padZ, padX + range * Math.cos(az), ty, padZ + range * Math.sin(az));
            if (!p.feasible) { notOk++; System.out.printf("  infeasible: range %.0f ty %.0f -> %s (err %.2f)%n", range, ty, p.reason, p.impactErrorAlong); continue; }
            double err = Math.hypot(p.impactErrorAlong, p.impactErrorCross);
            worst = Math.max(worst, err); sumErr += err;
            check(p.cutoffTick > 0 && p.engineSepTick > p.cutoffTick && p.warheadSepTick > p.engineSepTick && p.flightTicks > p.warheadSepTick,
                "event order broken at range " + range);
        }
        System.out.printf("1. random targets: %d runs, %d infeasible, worst miss %.3f blocks, mean miss %.4f%n", n, notOk, worst, sumErr / Math.max(1, n - notOk));
        check(worst < 1.0, "worst miss too large: " + worst);

        // 2. rotational symmetry: same range in 36 directions gives identical timings
        FlightSim.Plan ref = dry(0, 64, 0, 0.4 * MAX, 64, 0); boolean sym = true;
        for (int a = 0; a < 360; a += 10) {
            double r = Math.toRadians(a);
            FlightSim.Plan p = dry(0, 64, 0, 0.4 * MAX * Math.cos(r), 64, 0.4 * MAX * Math.sin(r));
            sym &= p.flightTicks == ref.flightTicks && p.cutoffTick == ref.cutoffTick && p.warheadSepTick == ref.warheadSepTick && Math.abs(p.impactErrorAlong) < 0.1;
        }
        check(sym, "timings differ with azimuth"); System.out.println("2. direction symmetry (36 azimuths): " + (sym ? "identical timings" : "MISMATCH"));

        // 3. determinism
        FlightSim.Plan a1 = dry(5, 70, 9, 900, 80, -400), a2 = dry(5, 70, 9, 900, 80, -400);
        check(a1.flightTicks == a2.flightTicks && a1.impactErrorAlong == a2.impactErrorAlong, "non-deterministic");
        System.out.println("3. determinism: ok");

        // 4. timings genuinely depend on the target (nothing is a fixed delay)
        long[] cut = new long[5], sep = new long[5], war = new long[5]; int k = 0;
        for (double r : new double[]{0.03*MAX, 0.13*MAX, 0.3*MAX, 0.6*MAX, 0.97*MAX}) {
            FlightSim.Plan p = dry(0, 64, 0, r, 64, 0);
            cut[k] = p.cutoffTick; sep[k] = p.engineSepTick - p.cutoffTick; war[k] = p.warheadSepTick; k++;
        }
        boolean distinctCut = true, distinctWar = true;
        for (int i = 1; i < 5; i++) { distinctCut &= cut[i] > cut[i - 1]; distinctWar &= war[i] > war[i - 1]; }
        check(distinctCut && distinctWar, "cutoff/warhead-separation times not monotonic with range");
        System.out.println("4. cutoff ticks " + java.util.Arrays.toString(cut) + "  cutoff->engine-sep gap " + java.util.Arrays.toString(sep) + "  warhead-sep ticks " + java.util.Arrays.toString(war));

        // 5. rejection paths
        check(!dry(0, 64, 0, 10, 64, 0).feasible, "too-close target accepted");
        check(!dry(0, 64, 0, MAX + 500, 64, 0).feasible, "out-of-range target accepted");
        FlightSim.Plan sky = dry(0, 64, 0, 200, 4000, 0);
        check(!sky.feasible, "unreachable sky target accepted");
        System.out.println("5. rejection: too close / out of range / unreachable sky -> '" + sky.reason + "'");

        // 6. live stepping reproduces the dry run (entity-style stepping, apogee/sep events reported once each)
        FlightState s = FlightSim.prepare(SPEC, 0, 64, 0, 0.33 * MAX, 64, 0); FlightSim.ignite(s);
        int[] ev = new int[5]; double prevY = s.py; long tImp = -1; double liftoffAccel = 0;
        for (int i = 0; i < 4000 && tImp < 0; i++) {
            double thrustBefore = s.thrustFrac;
            int e = FlightSim.step(s, true);
            for (int b = 0; b < 5; b++) if ((e & (1 << b)) != 0) ev[b]++;
            if ((e & FlightState.EV_LIFTOFF) != 0) liftoffAccel = s.thrustFrac * SPEC.thrust / FlightSim.mass(s);
            if (s.stage != FlightState.Stage.IGNITION && s.vy < 0 && prevY > 64 && s.py <= 64) tImp = s.tick;
            prevY = s.py;
        }
        FlightSim.Plan pl = dry(0, 64, 0, 0.33 * MAX, 64, 0);
        check(tImp == pl.flightTicks, "live stepping impact tick " + tImp + " != dry run " + pl.flightTicks);
        check(ev[0] == 1 && ev[1] == 1 && ev[2] == 1 && ev[3] == 1 && ev[4] == 1, "each event must fire exactly once: " + java.util.Arrays.toString(ev));
        check(liftoffAccel > Flight.G, "liftoff must happen when thrust exceeds weight");
        System.out.printf("6. live stepping: impact tick %d (dry run %d); events once each; liftoff at tick %d with thrust accel %.4f > g %.4f%n", tImp, pl.flightTicks, s.liftoffTick, liftoffAccel, Flight.G);

        // 7. physical sanity of the powered phase
        FlightState t = FlightSim.prepare(SPEC, 0, 64, 0, 0.66 * MAX, 64, 0); FlightSim.ignite(t);
        double heldTicks = 0; boolean monotonicSpool = true; double lastTf = 0; boolean continuousV = true; double lastSpeed = 0;
        for (int i = 0; i < 150; i++) {
            FlightSim.step(t, true);
            if (t.stage == FlightState.Stage.IGNITION) heldTicks++;
            if (t.stage == FlightState.Stage.IGNITION && t.thrustFrac < lastTf - 1e-12) monotonicSpool = false;
            lastTf = t.thrustFrac;
            if (Math.abs(t.speed() - lastSpeed) > 0.1) continuousV = false; // no velocity jumps (inertia)
            lastSpeed = t.speed();
        }
        check(monotonicSpool && continuousV && heldTicks > 10, "powered phase not smooth");
        System.out.printf("7. spool-up: held on pad for %.0f ticks until thrust > weight; velocity continuous: %b%n", heldTicks, continuousV);

    }
}
