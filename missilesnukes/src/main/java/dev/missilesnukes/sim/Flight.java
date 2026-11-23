package dev.missilesnukes.sim;

/** Physical constants of the missile. Units: blocks, ticks, and an arbitrary mass unit. */
public final class Flight {
    private Flight() {}

    public static final double G = 0.04;                    // blocks / tick^2  (per-type values live in MissileSpec)


    public static final double THROTTLE_SLEW = 1.0 / 12.0;  // max throttle change per tick while burning
    public static final double TAIL_OFF = Math.exp(-1.0 / 5.0); // thrust decay factor per tick after cutoff


    public static final double PITCH_SPEED = 0.7;           // ...and above this speed
    public static final double PITCH_RATE = Math.toRadians(0.5);
    public static final double ATT_MAX_RATE = Math.toRadians(2.0);
    public static final double ATT_BLEND = 0.15;
    public static final double TERMINAL_FLIP_RATE = Math.toRadians(2.5);

    public static final int SEP_LATCH_TICKS = 4, WARHEAD_DWELL_TICKS = 10;

    public static final double GUIDANCE_TOLERANCE = 0.02;   // bisection tolerance for the sub-tick cutoff (blocks)
    public static final double MIN_RANGE = 25.0;
    public static final int MAX_STEPS = 12000;
}
