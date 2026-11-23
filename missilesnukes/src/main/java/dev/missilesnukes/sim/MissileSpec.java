package dev.missilesnukes.sim;

/**
 * Everything that makes one missile type different from another: masses, propellant, engine, drag, size, stage
 * centres, separation springs, range and blast scale. The simulator itself is type-independent and reads these
 * values from the flight state, so every type is simulated by exactly the same physics.
 * Units: blocks, ticks, arbitrary mass units. Pure Java.
 */
public final class MissileSpec {
    public final String id;
    public final double mEngine, mBody, mWarhead;           // dry masses of the three stages
    public final double fuelCapacity;                        // propellant in the tank inside the central body
    public final double thrust, exhaustVelocity;             // engine thrust and exhaust speed (mass flow = thrust / exhaustVelocity)
    public final double ignitionTicks;                       // time for thrust to spool from 0 to 100 %
    public final double cdaStack, cdaBody, cdaWarhead;       // drag area while stacked / body + warhead / warhead alone
    public final double towerClear;                          // height over the pad before pitch-over is allowed
    public final double sepVrelEngine, sepVrelWarhead;       // spring separation speeds
    public final double sepMaxDynamicPressure;               // the engine section may only unlatch below this q = 1/2 rho v^2
    public final double maxRange;                            // planning limit (the real limit is the propellant)
    public final double halfHeight;                          // model centre to nozzle / nose distance (model is symmetric about 0)
    public final double width;                               // collision / pad footprint
    public final double[] centroidEngine, centroidBody, centroidWarhead;   // stage centres in model space
    public final double debrisCdaEngine, debrisCdaBody;      // drag area of the detached pieces
    public final double craterScale;                         // multiplies crater radius, shockwave reach and debris blast

    private MissileSpec(String id, double[] masses, double fuel, double thrust, double ve, double ignition, double[] cda,
                        double tower, double[] sepV, double sepMaxQ, double maxRange, double halfHeight, double width,
                        double[] cEngine, double[] cBody, double[] cWarhead, double[] debrisCda, double craterScale) {
        this.id = id;
        this.mEngine = masses[0]; this.mBody = masses[1]; this.mWarhead = masses[2];
        this.fuelCapacity = fuel; this.thrust = thrust; this.exhaustVelocity = ve; this.ignitionTicks = ignition;
        this.cdaStack = cda[0]; this.cdaBody = cda[1]; this.cdaWarhead = cda[2];
        this.towerClear = tower; this.sepVrelEngine = sepV[0]; this.sepVrelWarhead = sepV[1]; this.sepMaxDynamicPressure = sepMaxQ;
        this.maxRange = maxRange; this.halfHeight = halfHeight; this.width = width;
        this.centroidEngine = cEngine; this.centroidBody = cBody; this.centroidWarhead = cWarhead;
        this.debrisCdaEngine = debrisCda[0]; this.debrisCdaBody = debrisCda[1];
        this.craterScale = craterScale;
    }

    public double totalWetMass() { return mEngine + mBody + mWarhead + fuelCapacity; }
    public double liftoffAcceleration() { return thrust / totalWetMass(); }

    /** The original Light ICBM (light_icbm_* models). */
    public static final MissileSpec LIGHT = new MissileSpec("light",
        new double[]{90, 260, 200}, 640, 86, 22, 40, new double[]{0.30, 0.26, 0.10},
        24.0, new double[]{0.18, 0.22}, 2.0, 3000.0, 2.0, 0.7,
        PieceGeometry.CENTROID_ENGINE, PieceGeometry.CENTROID_BODY, PieceGeometry.CENTROID_WARHEAD,
        new double[]{0.12, 0.25}, 1.0);

    /** The Heavy ICBM (heavy_icbm_* models): bigger, heavier, longer ranged, with a larger warhead. */
    public static final MissileSpec HEAVY = new MissileSpec("heavy",
        new double[]{160, 520, 420}, 1500, 170, 26, 60, new double[]{0.42, 0.36, 0.14},
        32.0, new double[]{0.16, 0.20}, 1.4, 5000.0, 3.196, 1.0,
        new double[]{0.0, -2.5608, 0.0}, new double[]{0.0, -0.4729, 0.0}, new double[]{0.0, 1.9267, 0.0},
        new double[]{0.20, 0.40}, 1.6);

    public static final MissileSpec[] ALL = {LIGHT, HEAVY};

    public int index() { for (int i = 0; i < ALL.length; i++) if (ALL[i] == this) return i; return 0; }
    public static MissileSpec byIndex(int i) { return ALL[Math.max(0, Math.min(ALL.length - 1, i))]; }

    public static MissileSpec byId(String id) {
        for (MissileSpec s : ALL) if (s.id.equals(id)) return s;
        return LIGHT;
    }
}
