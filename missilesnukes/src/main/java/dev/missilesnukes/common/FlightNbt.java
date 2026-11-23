package dev.missilesnukes.common;

import dev.missilesnukes.sim.FlightState;
import net.minecraft.nbt.CompoundTag;

/** Saves and restores a missile's complete flight state so that flights survive world saves and chunk unloads. */
public final class FlightNbt {
    private FlightNbt() {}

    public static void save(FlightState s, CompoundTag t) {
        t.putInt("stage", s.stage.ordinal());
        t.putLong("tick", s.tick);
        t.putDouble("px", s.px); t.putDouble("py", s.py); t.putDouble("pz", s.pz);
        t.putDouble("vx", s.vx); t.putDouble("vy", s.vy); t.putDouble("vz", s.vz);
        t.putDouble("qx", s.q.x); t.putDouble("qy", s.q.y); t.putDouble("qz", s.q.z); t.putDouble("qw", s.q.w);
        t.putDouble("thrust", s.thrustFrac); t.putDouble("throttle", s.throttleCmd); t.putDouble("fuel", s.fuel);
        t.putBoolean("engine", s.engineAttached); t.putBoolean("body", s.bodyAttached);
        t.putBoolean("pitchStarted", s.pitchStarted); t.putBoolean("warheadPivot", s.warheadPivot); t.putBoolean("shortfall", s.shortfall);
        t.putDouble("pitch", s.pitchAngle);
        t.putDouble("padX", s.padX); t.putDouble("padY", s.padY); t.putDouble("padZ", s.padZ);
        t.putDouble("tx", s.tx); t.putDouble("ty", s.ty); t.putDouble("tz", s.tz);
        t.putDouble("azX", s.azX); t.putDouble("azZ", s.azZ); t.putDouble("thetaBo", s.thetaBurnout);
        t.putLong("nextPredict", s.nextPredictTick); t.putDouble("cutFrac", s.cutFrac);
        t.putLong("cutoffTick", s.cutoffTick); t.putLong("engineSepTick", s.engineSepTick);
        t.putLong("warheadSepTick", s.warheadSepTick); t.putLong("liftoffTick", s.liftoffTick); t.putLong("apogeeTick", s.apogeeTick);
        t.putDouble("apogeeY", s.apogeeY); t.putDouble("maxSpeed", s.maxSpeed);
    }

    public static void load(FlightState s, CompoundTag t) {
        s.stage = FlightState.Stage.byId(t.getInt("stage"));
        s.tick = t.getLong("tick");
        s.px = t.getDouble("px"); s.py = t.getDouble("py"); s.pz = t.getDouble("pz");
        s.vx = t.getDouble("vx"); s.vy = t.getDouble("vy"); s.vz = t.getDouble("vz");
        if (t.contains("qw")) { s.q.x = t.getDouble("qx"); s.q.y = t.getDouble("qy"); s.q.z = t.getDouble("qz"); s.q.w = t.getDouble("qw"); s.q.normalize(); }
        s.thrustFrac = t.getDouble("thrust"); s.throttleCmd = t.getDouble("throttle");
        s.fuel = t.contains("fuel") ? t.getDouble("fuel") : s.fuel;
        s.engineAttached = !t.contains("engine") || t.getBoolean("engine");
        s.bodyAttached = !t.contains("body") || t.getBoolean("body");
        s.pitchStarted = t.getBoolean("pitchStarted"); s.warheadPivot = t.getBoolean("warheadPivot"); s.shortfall = t.getBoolean("shortfall");
        s.pitchAngle = t.getDouble("pitch");
        s.padX = t.getDouble("padX"); s.padY = t.getDouble("padY"); s.padZ = t.getDouble("padZ");
        s.tx = t.getDouble("tx"); s.ty = t.getDouble("ty"); s.tz = t.getDouble("tz");
        s.azX = t.contains("azX") ? t.getDouble("azX") : 1; s.azZ = t.getDouble("azZ"); s.thetaBurnout = t.getDouble("thetaBo");
        s.nextPredictTick = t.getLong("nextPredict"); s.cutFrac = t.getDouble("cutFrac");
        s.cutoffTick = t.contains("cutoffTick") ? t.getLong("cutoffTick") : -1;
        s.engineSepTick = t.contains("engineSepTick") ? t.getLong("engineSepTick") : -1;
        s.warheadSepTick = t.contains("warheadSepTick") ? t.getLong("warheadSepTick") : -1;
        s.liftoffTick = t.contains("liftoffTick") ? t.getLong("liftoffTick") : -1;
        s.apogeeTick = t.contains("apogeeTick") ? t.getLong("apogeeTick") : -1;
        s.apogeeY = t.contains("apogeeY") ? t.getDouble("apogeeY") : -1e9; s.maxSpeed = t.getDouble("maxSpeed");
    }
}
