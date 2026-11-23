package dev.missilesnukes.sim;

/** Tuning helper: java -cp out dev.missilesnukes.sim.SimHarness heavy */
public class SimHarness {
    public static void main(String[] a) {
        MissileSpec spec = MissileSpec.byId(a.length > 0 ? a[0] : "light");
        System.out.printf("%s: wet mass %.0f, liftoff accel %.4f (g %.2f), exhaust v %.0f, max range planned %.0f%n", spec.id, spec.totalWetMass(), spec.liftoffAcceleration(), Flight.G, spec.exhaustVelocity, spec.maxRange);
        System.out.printf("%-8s %-9s %-9s %-8s %-9s %-9s %-9s %-8s %s%n", "range", "flightT", "burnT", "cutoff", "warSep", "apogeeAbv", "errAlong", "fuelLeft", "ok/reason");
        for (double f : new double[]{0.01, 0.03, 0.1, 0.2, 0.35, 0.5, 0.7, 0.85, 0.95, 1.0, 1.05, 1.15}) {
            double r = Math.max(30, f * spec.maxRange);
            FlightSim.Plan p = FlightSim.dryRun(spec, 0, 64, 0, r, 64, 0);
            System.out.printf("%-8.0f %-9d %-9d %-8d %-9d %-9.0f %-9.2f %-8.0f %s%n", r, p.flightTicks, p.burnTicks, p.cutoffTick, p.warheadSepTick, p.apogeeAboveTarget, p.impactErrorAlong, p.fuelLeft, p.feasible ? "OK" : p.reason);
        }
    }
}
