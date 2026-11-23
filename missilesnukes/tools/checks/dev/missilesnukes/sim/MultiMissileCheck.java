package dev.missilesnukes.sim;

/** Eight missiles of MIXED types (light and heavy) launched in the same tick and stepped in lockstep like eight server entities. */
public class MultiMissileCheck {
    public static void main(String[] args) {
        int n = 8;
        MissileSpec[] spec = new MissileSpec[n];
        double[][] pads = new double[n][3], t = new double[n][3];
        double[] frac = {0.04, 0.16, 0.33, 0.7, 0.75, 0.95, 0.35, 0.05};
        double[] az = {0, 40, 95, 150, 200, 250, 300, 340};
        for (int i = 0; i < n; i++) {
            spec[i] = i % 2 == 0 ? MissileSpec.LIGHT : MissileSpec.HEAVY;
            pads[i] = new double[]{i * 12, 64 + (i % 3) * 4, i * 9};
            double r = frac[i] * spec[i].maxRange, a = Math.toRadians(az[i]);
            t[i] = new double[]{pads[i][0] + r * Math.cos(a), 64 + (i * 7) % 40, pads[i][2] + r * Math.sin(a)};
        }
        FlightState[] s = new FlightState[n]; FlightSim.Plan[] plan = new FlightSim.Plan[n];
        long[] impactTick = new long[n]; double[] errAlong = new double[n], errCross = new double[n], prevY = new double[n];
        boolean[] done = new boolean[n]; int[][] events = new int[n][5];
        for (int i = 0; i < n; i++) {
            plan[i] = FlightSim.dryRun(spec[i], pads[i][0], pads[i][1], pads[i][2], t[i][0], t[i][1] + 1.0, t[i][2]);
            s[i] = FlightSim.prepare(spec[i], pads[i][0], pads[i][1], pads[i][2], t[i][0], t[i][1] + 1.0, t[i][2]);
            FlightSim.ignite(s[i]); prevY[i] = s[i].py;
        }
        long steps = 0, nanos = 0; int remaining = n; int tick = 0;
        while (remaining > 0 && tick < 8000) {
            tick++;
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) {
                if (done[i]) continue;
                double x0 = s[i].px, z0 = s[i].pz;
                int e = FlightSim.step(s[i], true); steps++;
                for (int b = 0; b < 5; b++) if ((e & (1 << b)) != 0) events[i][b]++;
                double ty = t[i][1] + 1.0;
                if (s[i].stage != FlightState.Stage.IGNITION && s[i].vy < 0 && prevY[i] > ty && s[i].py <= ty) {
                    double f = (prevY[i] - ty) / (prevY[i] - s[i].py);
                    double hx = x0 + (s[i].px - x0) * f, hz = z0 + (s[i].pz - z0) * f;
                    double azx = s[i].azX, azz = s[i].azZ;
                    errAlong[i] = (hx - pads[i][0]) * azx + (hz - pads[i][2]) * azz - ((t[i][0] - pads[i][0]) * azx + (t[i][2] - pads[i][2]) * azz);
                    errCross[i] = (hx - pads[i][0]) * -azz + (hz - pads[i][2]) * azx - ((t[i][0] - pads[i][0]) * -azz + (t[i][2] - pads[i][2]) * azx);
                    impactTick[i] = s[i].tick; done[i] = true; remaining--;
                }
                prevY[i] = s[i].py;
            }
            nanos += System.nanoTime() - t0;
        }
        boolean ok = remaining == 0;
        System.out.printf("%-3s %-6s %-8s %-8s %-9s %-9s %-9s %-12s %s%n", "#", "type", "range", "flight", "dry-run", "alongErr", "crossErr", "events", "result");
        for (int i = 0; i < n; i++) {
            double range = Math.hypot(t[i][0] - pads[i][0], t[i][2] - pads[i][2]);
            boolean good = plan[i].feasible && impactTick[i] == plan[i].flightTicks && Math.abs(errAlong[i]) < 1 && Math.abs(errCross[i]) < 1
                && events[i][0] == 1 && events[i][1] == 1 && events[i][2] == 1 && events[i][3] == 1 && events[i][4] == 1;
            ok &= good;
            System.out.printf("%-3d %-6s %-8.0f %-8d %-9d %-9.3f %-9.3f %-12s %s%n", i + 1, spec[i].id, range, impactTick[i], plan[i].flightTicks, errAlong[i], errCross[i],
                java.util.Arrays.toString(events[i]).replace(" ", ""), good ? "OK" : "FAIL " + plan[i].reason);
        }
        System.out.printf("%n%d missile-steps in %.1f ms -> %.4f ms per missile per tick (all 8 flying: %.3f ms of the 50 ms tick)%n", steps, nanos / 1e6, nanos / 1e6 / steps, nanos / 1e6 / tick);
        System.out.println(ok ? "ALL 8 INDEPENDENT MIXED-TYPE FLIGHTS CORRECT" : "FAILURE");
        System.exit(ok ? 0 : 1);
    }
}
