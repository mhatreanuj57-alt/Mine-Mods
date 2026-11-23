package dev.missilesnukes.common;

import dev.missilesnukes.sim.MissileSpec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Server-side warhead detonation: a visual/audio effect, a crater that is carved layer by layer over several ticks
 * (so a huge crater never stalls the server), and an expanding shockwave that damages and throws entities.
 */
public final class ImpactManager {
    private interface Task { boolean tick(); }   // returns true when finished

    private static final List<Task> TASKS = new ArrayList<>();

    private ImpactManager() {}

    public static void detonate(ServerLevel level, Vec3 pos, @Nullable Entity source, MissileSpec spec) {
        double radius = Config.CRATER_RADIUS.get() * spec.craterScale;
        double shock = radius * Config.SHOCKWAVE_RADIUS_FACTOR.get();

        ExplosionFxEntity fx = ModEntities.EXPLOSION_FX.get().create(level);
        if (fx != null) {
            fx.setPos(pos.x, pos.y, pos.z);
            fx.configure((float) (radius / 7.0), (float) shock);
            level.addFreshEntity(fx);
        }
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, (float) (14.0 * spec.craterScale), 0.45f);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, (float) (12.0 * spec.craterScale), 0.5f);

        boolean grief = Config.BLOCK_DAMAGE.get()
            && (!Config.RESPECT_MOB_GRIEFING.get() || level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING));
        if (grief) TASKS.add(new CraterTask(level, BlockPos.containing(pos), radius));
        TASKS.add(new ShockwaveTask(level, pos, radius, shock, source));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (TASKS.isEmpty()) return;
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            if (it.next().tick()) it.remove();
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        TASKS.clear();
    }

    // ------------------------------------------------------------------------------------------ crater

    private static final class CraterTask implements Task {
        private final ServerLevel level;
        private final BlockPos centre;
        private final double radius, up, down;
        private int dy;
        private final int bottom;

        CraterTask(ServerLevel level, BlockPos centre, double radius) {
            this.level = level;
            this.centre = centre;
            this.radius = radius;
            this.up = radius * 0.55;          // everything above the impact point is cleared out to this height
            this.down = radius * 0.70;        // bowl depth
            this.dy = (int) Math.ceil(up);
            this.bottom = -(int) Math.ceil(down);
        }

        @Override
        public boolean tick() {
            int budget = Config.CRATER_BLOCKS_PER_TICK.get();
            while (budget > 0 && dy >= bottom) {
                budget -= processLayer(dy);
                dy--;
            }
            return dy < bottom;
        }

        private int processLayer(int dy) {
            double ry = dy >= 0 ? up : down;
            double t = dy / ry;
            double rr2 = radius * radius * (1.0 - t * t);
            if (rr2 <= 0) return 1;
            int rr = (int) Math.ceil(Math.sqrt(rr2));
            double maxResistance = Config.CRATER_MAX_RESISTANCE.get();
            int visited = 0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dx = -rr; dx <= rr; dx++) {
                for (int dz = -rr; dz <= rr; dz++) {
                    double dist2 = dx * dx + dz * dz;
                    if (dist2 > rr2) continue;
                    visited++;
                    p.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (p.getY() < level.getMinBuildHeight() || p.getY() >= level.getMaxBuildHeight() || !level.isLoaded(p)) continue;
                    BlockState state = level.getBlockState(p);
                    if (state.isAir() || state.getDestroySpeed(level, p) < 0) continue;     // air and unbreakable (bedrock...)
                    double d2 = dist2 / (radius * radius) + t * t;                          // 0 at the centre, 1 at the rim
                    if (state.getBlock().getExplosionResistance() > maxResistance * (1.0 - d2)) continue;
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    if (d2 > 0.8) level.updateNeighborsAt(p.immutable(), Blocks.AIR);       // let water / sand react at the rim
                }
            }
            return Math.max(visited, 1);
        }
    }

    // ------------------------------------------------------------------------------------------ shockwave

    private static final class ShockwaveTask implements Task {
        private final ServerLevel level;
        private final Vec3 centre;
        private final double craterRadius, maxRadius;
        @Nullable private final Entity source;
        private double r;

        ShockwaveTask(ServerLevel level, Vec3 centre, double craterRadius, double maxRadius, @Nullable Entity source) {
            this.level = level;
            this.centre = centre;
            this.craterRadius = craterRadius;
            this.maxRadius = maxRadius;
            this.source = source;
        }

        @Override
        public boolean tick() {
            double previous = r;
            r += ExplosionFxEntity.SHOCK_SPEED;
            AABB box = new AABB(centre, centre).inflate(r);
            double maxDamage = Config.SHOCKWAVE_MAX_DAMAGE.get();
            for (Entity e : level.getEntities((Entity) null, box, x -> true)) {
                if (e instanceof ExplosionFxEntity || e instanceof MissilePartEntity) continue;
                Vec3 c = e.getBoundingBox().getCenter();
                double dist = c.distanceTo(centre);
                if (dist > r || dist <= previous) continue;
                if (e instanceof MissileEntity m) {                                   // pads inside the blast are vaporised
                    if (m.stage() == dev.missilesnukes.sim.FlightState.Stage.PAD && dist < craterRadius * 1.1) m.discard();
                    continue;
                }
                double f = 1.0 - dist / maxRadius;
                if (f <= 0) continue;
                e.hurt(level.damageSources().explosion(null, source), (float) (maxDamage * f * f));
                if (e instanceof Player pl && pl.getAbilities().flying) continue;
                Vec3 dir = c.subtract(centre);
                dir = dir.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : dir.normalize();
                double k = 2.2 * f + 0.1;
                e.setDeltaMovement(e.getDeltaMovement().add(dir.x * k, 0.45 * k + 0.1, dir.z * k));
                e.hurtMarked = true;
            }
            return r > maxRadius;
        }
    }
}
