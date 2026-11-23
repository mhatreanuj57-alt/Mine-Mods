package dev.missilesnukes.sim;

/** Complete, self-contained state of one missile's flight. Every missile owns exactly one of these. */
public final class FlightState {
    public enum Stage {
        PAD, IGNITION, ASCENT, CUTOFF, MIDCOURSE, TERMINAL, IMPACT;
        public static Stage byId(int id) { Stage[] v = values(); return v[Math.max(0, Math.min(v.length - 1, id))]; }
    }

    // event bits returned by FlightSim.step
    public static final int EV_LIFTOFF = 1, EV_CUTOFF = 2, EV_ENGINE_SEP = 4, EV_WARHEAD_SEP = 8, EV_APOGEE = 16;

    public MissileSpec spec = MissileSpec.LIGHT;   // which missile type this state belongs to
    public Stage stage = Stage.PAD;
    public long tick;                 // ticks since ignition command
    public double px, py, pz;         // position of the model origin (or of the warhead centre after warhead separation)
    public double vx, vy, vz;         // velocity, blocks per tick
    public final Quat q = new Quat(); // attitude; body +Y is the nose
    public double thrustFrac;         // actual engine output 0..1 (spools, never jumps)
    public double throttleCmd;
    public double fuel = MissileSpec.LIGHT.fuelCapacity;

    public boolean engineAttached = true, bodyAttached = true;
    public boolean pitchStarted;
    public double pitchAngle;         // commanded angle from vertical (radians)

    // mission
    public double padX, padY, padZ;
    public double tx, ty, tz;
    public double azX = 1, azZ = 0;   // unit horizontal direction launch -> target
    public double thetaBurnout;       // planned pitch from vertical at burnout (radians)

    // guidance memory
    public long nextPredictTick;
    public double cutFrac;            // >0: the engine is cut part-way through the coming tick (sub-tick cutoff trim)
    public boolean shortfall;         // fuel ran out before the guidance reached the target

    // dynamic event log
    public long cutoffTick = -1, engineSepTick = -1, warheadSepTick = -1, liftoffTick = -1, apogeeTick = -1;
    public double apogeeY = -1e9;
    public double maxSpeed;
    public boolean warheadPivot;      // true once the state describes the warhead alone (position re-based to its centre)

    // outputs describing the last separation (read by the entity to spawn the detached pieces)
    public double sepX, sepY, sepZ, sepVx, sepVy, sepVz;
    public final Quat sepQ = new Quat();

    public FlightState copy() {
        FlightState c = new FlightState();
        c.spec = spec; c.stage = stage; c.tick = tick; c.px = px; c.py = py; c.pz = pz; c.vx = vx; c.vy = vy; c.vz = vz;
        c.q.set(q); c.thrustFrac = thrustFrac; c.throttleCmd = throttleCmd; c.fuel = fuel;
        c.engineAttached = engineAttached; c.bodyAttached = bodyAttached; c.pitchStarted = pitchStarted; c.pitchAngle = pitchAngle;
        c.padX = padX; c.padY = padY; c.padZ = padZ; c.tx = tx; c.ty = ty; c.tz = tz; c.azX = azX; c.azZ = azZ;
        c.thetaBurnout = thetaBurnout; c.cutFrac = cutFrac;
        c.nextPredictTick = nextPredictTick; c.shortfall = shortfall; c.cutoffTick = cutoffTick; c.engineSepTick = engineSepTick;
        c.warheadSepTick = warheadSepTick; c.liftoffTick = liftoffTick; c.apogeeTick = apogeeTick; c.apogeeY = apogeeY;
        c.maxSpeed = maxSpeed; c.warheadPivot = warheadPivot;
        return c;
    }

    public double speed() { return Math.sqrt(vx * vx + vy * vy + vz * vz); }
}
