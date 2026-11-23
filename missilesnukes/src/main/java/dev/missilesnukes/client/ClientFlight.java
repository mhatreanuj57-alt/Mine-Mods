package dev.missilesnukes.client;

import dev.missilesnukes.common.MissileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/** Client-side lookup of linked missiles plus the derived live flight information shown by the panel and HUD. */
public final class ClientFlight {
    private ClientFlight() {}

    public static MissileEntity find(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof MissileEntity m && m.getUUID().equals(id) && !m.isRemoved()) return m;
        }
        return null;
    }

    public static String stageName(MissileEntity m) {
        return switch (m.stage()) {
            case PAD -> "ON PAD";
            case IGNITION -> "IGNITION";
            case ASCENT -> "POWERED ASCENT";
            case CUTOFF -> "ENGINE CUTOFF";
            case MIDCOURSE -> "PROPULSION SEPARATED";
            case TERMINAL -> "WARHEAD DESCENT";
            case IMPACT -> "IMPACT";
        };
    }

    public record Live(String stage, double altitude, double speedBps, double distanceToTarget, double etaSeconds, double progress, int fuelPercent, double plannedApogee) {}

    /** Live telemetry from the synced entity; ETA is exact because the flight is deterministic. */
    public static Live live(MissileEntity m) {
        double sx = m.getDeltaMovement().length() * 20.0;
        double cy = m.getY() + m.half();
        double dist = 0;
        if (m.hasTarget()) {
            double dx = m.targetBlock().getX() + 0.5 - m.getX(), dz = m.targetBlock().getZ() + 0.5 - m.getZ();
            dist = Math.hypot(dx, dz);
        }
        int plan = m.plannedTicks(), tick = m.flightTick();
        double eta = plan > 0 ? Math.max(0, plan - tick) / 20.0 : 0;
        double progress = plan > 0 ? Math.max(0, Math.min(1, (double) tick / plan)) : 0;
        return new Live(stageName(m), cy, sx, dist, eta, progress, Math.round(m.fuelFraction() * 100), m.plannedApogeeY());
    }
}
