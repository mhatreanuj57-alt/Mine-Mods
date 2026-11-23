package dev.missilesnukes.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the chunks along a missile's immediate path ticking so that it keeps flying when no player is near.
 * Uses NeoForge's ticket controller; every ticket is owned by the entity and released when it is removed.
 */
public final class ChunkLoading {
    public static final TicketController CONTROLLER = new TicketController(MissilesNukes.id("flight"), (level, helper) -> {
        // Tickets left over from a previous session are never valid: flying entities re-create what they need.
        for (UUID owner : new ArrayList<>(helper.getEntityTickets().keySet())) helper.removeAllTickets(owner);
    });

    private ChunkLoading() {}

    public static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    /** Per-entity set of forced chunks. */
    public static final class Tracker {
        private static final int LOOKAHEAD_TICKS = 10;
        private static final double MAX_LOOKAHEAD_BLOCKS = 176.0;
        private final Set<Long> held = new HashSet<>();

        public void update(ServerLevel level, Entity owner, double x, double z, double vx, double vz) {
            if (!Config.CHUNK_LOADING.get()) { release(level, owner); return; }
            Set<Long> want = new HashSet<>();
            double len = Math.hypot(vx, vz) * LOOKAHEAD_TICKS;
            double scale = len > MAX_LOOKAHEAD_BLOCKS ? MAX_LOOKAHEAD_BLOCKS / len : 1.0;
            double dx = vx * LOOKAHEAD_TICKS * scale, dz = vz * LOOKAHEAD_TICKS * scale;
            int samples = Math.max(1, (int) Math.ceil(Math.hypot(dx, dz) / 12.0));
            for (int i = 0; i <= samples; i++) {
                double f = (double) i / samples;
                int cx = ((int) Math.floor(x + dx * f)) >> 4, cz = ((int) Math.floor(z + dz * f)) >> 4;
                for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) want.add(pack(cx + ox, cz + oz));
            }
            for (Long key : new ArrayList<>(held)) {
                if (!want.contains(key)) { CONTROLLER.forceChunk(level, owner, unpackX(key), unpackZ(key), false, true); held.remove(key); }
            }
            for (Long key : want) {
                if (held.add(key)) CONTROLLER.forceChunk(level, owner, unpackX(key), unpackZ(key), true, true);
            }
        }

        public void release(ServerLevel level, Entity owner) {
            for (Long key : held) CONTROLLER.forceChunk(level, owner, unpackX(key), unpackZ(key), false, true);
            held.clear();
        }

        private static long pack(int x, int z) { return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32); }
        private static int unpackX(long k) { return (int) k; }
        private static int unpackZ(long k) { return (int) (k >> 32); }
    }
}
