package net.runictitan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Abilities {
	/** Set to false if you do not want the meteor explosion to destroy blocks / light fires. */
	public static final boolean METEOR_BREAKS_BLOCKS = true;
	public static final boolean METEOR_LIGHTS_FIRE = true;

	private static final Random RNG = new Random();

	private Abilities() {}

	// ------------------------------------------------------------------ helpers

	/** Spawns particles for every player, even far away (override limiter = long range). */
	static void fx(ServerLevel w, ParticleOptions type, double x, double y, double z,
				   int n, double dx, double dy, double dz, double speed) {
		for (ServerPlayer sp : w.players()) {
			w.sendParticles(sp, type, true, x, y, z, n, dx, dy, dz, speed);
		}
	}

	static void sound(ServerLevel w, Vec3 p, SoundEvent ev, float vol, float pitch) {
		w.playSound(null, p.x, p.y, p.z, ev, SoundSource.PLAYERS, vol, pitch);
	}

	static List<LivingEntity> targets(ServerLevel w, Player caster, Vec3 c, double r) {
		AABB box = new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
		return w.getEntitiesOfClass(LivingEntity.class, box,
				e -> e != caster && e.isAlive() && !(e instanceof ArmorStand) && e.distanceToSqr(c) <= r * r);
	}

	static void hurt(ServerLevel w, Player caster, LivingEntity e, float amount) {
		e.hurt(w.damageSources().playerAttack(caster), amount);
	}

	static void knock(LivingEntity e, Vec3 from, double strength, double up) {
		Vec3 d = new Vec3(e.getX() - from.x, 0, e.getZ() - from.z);
		if (d.lengthSqr() < 1.0E-4) d = new Vec3(RNG.nextDouble() - 0.5, 0, RNG.nextDouble() - 0.5);
		d = d.normalize().scale(strength);
		e.push(d.x, up, d.z);
		e.hurtMarked = true;
	}

	static Vec3 aim(Player p, double max) {
		return p.pick(max, 1.0F, false).getLocation();
	}

	/** Finds the top of the ground at/below the given point. */
	static Vec3 groundAt(ServerLevel w, Vec3 v) {
		BlockPos.MutableBlockPos m = BlockPos.containing(v).mutable();
		int steps = 0;
		while (steps < 80 && m.getY() > w.getMinBuildHeight() && w.getBlockState(m).getCollisionShape(w, m).isEmpty()) {
			m.move(0, -1, 0);
			steps++;
		}
		return new Vec3(v.x, m.getY() + 1.0, v.z);
	}

	static BlockState floorState(ServerLevel w, BlockPos below) {
		BlockState s = w.getBlockState(below);
		if (s.isAir() || !s.getFluidState().isEmpty()) return Blocks.STONE.defaultBlockState();
		return s;
	}

	static void ring(ServerLevel w, Vec3 c, double r, ParticleOptions type, int perBlock, double dy, double speed) {
		int n = Math.max(8, (int) (r * perBlock));
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			fx(w, type, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 1, 0.1, dy, 0.1, speed);
		}
	}

	// ------------------------------------------------------------------ 1. Ground Smash

	public static void groundSmash(ServerLevel w, Player p) {
		Vec3 c = p.position();
		BlockState floor = floorState(w, p.blockPosition().below());
		BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, floor);

		sound(w, c, SoundEvents.ANVIL_LAND, 1.5f, 0.5f);
		sound(w, c, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.7f);
		fx(w, debris, c.x, c.y + 0.2, c.z, 80, 0.8, 0.2, 0.8, 0.25);
		fx(w, ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);

		final int waves = 9;
		for (int i = 0; i < waves; i++) {
			final double r = 1.5 + i * 1.1;
			Scheduler.later(i, () -> {
				ring(w, c, r, debris, 6, 0.5, 0.18);
				ring(w, c, r, ParticleTypes.CLOUD, 3, 0.05, 0.05);
				for (LivingEntity e : targets(w, p, c, r + 1.0)) {
					double d = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
					if (d >= r - 1.1 && d <= r + 0.2 && Math.abs(e.getY() - c.y) < 3.5) {
						hurt(w, p, e, 10.0f);
						knock(e, c, 0.9, 0.7);
					}
				}
			});
		}
	}

	// ------------------------------------------------------------------ 2. Meteor Strike

	public static void meteorStrike(ServerLevel w, Player p) {
		final Vec3 target = groundAt(w, aim(p, 55));

		// Voxel meteor animation (from the GLB). It flies in over the target, towards the caster.
		Vec3 look = p.getLookAngle();
		Vec3 dir = new Vec3(target.x - p.getX(), 0, target.z - p.getZ());
		if (dir.lengthSqr() < 1.0) dir = new Vec3(look.x, 0, look.z);
		if (MeteorVfx.play(w, target, dir.x, dir.z, () -> meteorImpact(w, p, target))) {
			sound(w, target, SoundEvents.BLAZE_SHOOT, 3.0f, 0.4f);
			sound(w, target, SoundEvents.WITHER_SPAWN, 0.6f, 1.6f);
			for (int i = 0; i < 30; i += 5) {
				Scheduler.later(i, () -> sound(w, target, SoundEvents.FIRECHARGE_USE, 2.5f, 0.5f));
			}
			return;
		}

		// Fallback if the animation data could not be loaded: a plain particle meteor.
		final Vec3 start = target.add(16, 40, 10);
		final int flight = 28;
		sound(w, target, SoundEvents.BLAZE_SHOOT, 3.0f, 0.4f);
		for (int t = 1; t <= flight; t++) {
			final int tick = t;
			Scheduler.later(t, () -> {
				double f = Math.pow((double) tick / flight, 1.5);
				Vec3 pos = start.lerp(target, f);
				fx(w, ParticleTypes.FLAME, pos.x, pos.y, pos.z, 40, 1.0, 1.0, 1.0, 0.05);
				fx(w, ParticleTypes.LAVA, pos.x, pos.y, pos.z, 8, 0.8, 0.8, 0.8, 0.0);
				fx(w, ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 20, 1.2, 1.2, 1.2, 0.03);
				if (tick == flight) meteorImpact(w, p, target);
			});
		}
	}

	private static void meteorImpact(ServerLevel w, Player p, Vec3 at) {
		BlockState floor = floorState(w, BlockPos.containing(at).below());
		BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, floor);

		w.explode(p, at.x, at.y, at.z, 4.0f, METEOR_LIGHTS_FIRE,
				METEOR_BREAKS_BLOCKS ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
		sound(w, at, SoundEvents.GENERIC_EXPLODE, 4.0f, 0.5f);
		sound(w, at, SoundEvents.DRAGON_FIREBALL_EXPLODE, 3.0f, 0.6f);

		fx(w, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
		fx(w, ParticleTypes.FLAME, at.x, at.y + 1, at.z, 150, 2.5, 0.8, 2.5, 0.35);
		fx(w, ParticleTypes.LAVA, at.x, at.y + 1, at.z, 50, 2.0, 0.5, 2.0, 0.0);
		fx(w, ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 80, 3.0, 1.5, 3.0, 0.12);
		fx(w, debris, at.x, at.y + 0.5, at.z, 120, 2.0, 0.5, 2.0, 0.5);

		for (LivingEntity e : targets(w, p, at, 9.0)) {
			double d = Math.sqrt(e.distanceToSqr(at));
			float dmg = (float) (24.0 * (1.0 - d / 9.0)) + 4.0f;
			hurt(w, p, e, dmg);
			e.setSecondsOnFire(8);
			knock(e, at, 1.6, 0.9);
		}
	}

	// ------------------------------------------------------------------ 3. Gravity Well

	public static void gravityWell(ServerLevel w, Player p) {
		final Vec3 centre = groundAt(w, aim(p, 35)).add(0, 1.6, 0);
		final double radius = 10.0;
		final int duration = 45;

		sound(w, centre, SoundEvents.END_PORTAL_SPAWN, 1.0f, 1.8f);
		sound(w, centre, SoundEvents.ENDERMAN_TELEPORT, 2.0f, 0.5f);

		for (int t = 0; t <= duration; t++) {
			final int tick = t;
			Scheduler.later(t, () -> {
				double shrink = 1.0 - (double) tick / duration;
				for (int arm = 0; arm < 3; arm++) {
					for (int k = 0; k < 14; k++) {
						double rr = (0.6 + k * 0.45) * (0.4 + shrink);
						double a = tick * 0.45 + k * 0.5 + arm * (Math.PI * 2 / 3);
						fx(w, ParticleTypes.REVERSE_PORTAL, centre.x + Math.cos(a) * rr, centre.y + (k - 7) * 0.05,
								centre.z + Math.sin(a) * rr, 1, 0, 0, 0, 0.02);
					}
				}
				fx(w, ParticleTypes.LARGE_SMOKE, centre.x, centre.y, centre.z, 4, 0.3, 0.3, 0.3, 0.01);
				fx(w, ParticleTypes.SQUID_INK, centre.x, centre.y, centre.z, 3, 0.2, 0.2, 0.2, 0.02);
				if (tick % 8 == 0) ring(w, centre.add(0, -1.5, 0), 2.0 + shrink * (radius - 2.0), ParticleTypes.SOUL_FIRE_FLAME, 3, 0.2, 0.0);

				for (LivingEntity e : targets(w, p, centre, radius)) {
					Vec3 mid = e.position().add(0, e.getBbHeight() / 2.0, 0);
					Vec3 dirTo = centre.subtract(mid);
					if (dirTo.lengthSqr() < 0.25) continue;
					Vec3 pull = dirTo.normalize().scale(0.22);
					e.setDeltaMovement(e.getDeltaMovement().scale(0.85).add(pull));
					e.hurtMarked = true;
					e.fallDistance = 0;
					if (tick % 10 == 0) hurt(w, p, e, 2.0f);
				}
			});
		}

		Scheduler.later(duration + 1, () -> {
			sound(w, centre, SoundEvents.GENERIC_EXPLODE, 3.0f, 0.6f);
			sound(w, centre, SoundEvents.WARDEN_SONIC_BOOM, 2.0f, 1.0f);
			fx(w, ParticleTypes.EXPLOSION_EMITTER, centre.x, centre.y, centre.z, 1, 0, 0, 0, 0);
			fx(w, ParticleTypes.ELECTRIC_SPARK, centre.x, centre.y, centre.z, 200, 3.0, 3.0, 3.0, 0.6);
			fx(w, ParticleTypes.REVERSE_PORTAL, centre.x, centre.y, centre.z, 200, 1.0, 1.0, 1.0, 0.8);
			for (LivingEntity e : targets(w, p, centre, 7.0)) {
				hurt(w, p, e, 14.0f);
				Vec3 away = e.position().add(0, 0.5, 0).subtract(centre);
				Vec3 v = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : away.normalize().scale(1.4);
				e.push(v.x, 0.9, v.z);
				e.hurtMarked = true;
			}
		});
	}

	// ------------------------------------------------------------------ 4. Thunder Crash (real lightning)

	public static void thunderCrash(ServerLevel w, Player p) {
		Vec3 c = p.position();
		List<LivingEntity> found = targets(w, p, c, 16.0);
		List<Vec3> strikes = new ArrayList<>();
		for (LivingEntity e : found) {
			if (strikes.size() >= 8) break;
			strikes.add(e.position());
		}
		if (strikes.isEmpty()) {
			Vec3 g = groundAt(w, aim(p, 40));
			strikes.add(g);
			for (int i = 0; i < 3; i++) {
				strikes.add(groundAt(w, g.add((RNG.nextDouble() - 0.5) * 10, 0, (RNG.nextDouble() - 0.5) * 10)));
			}
		}

		sound(w, c, SoundEvents.TRIDENT_THUNDER, 2.0f, 0.8f);
		ring(w, c, 2.5, ParticleTypes.ELECTRIC_SPARK, 6, 1.0, 0.3);

		for (int i = 0; i < strikes.size(); i++) {
			final Vec3 pos = strikes.get(i);
			Scheduler.later(i * 4, () -> lightning(w, p, pos));
		}
	}

	private static void lightning(ServerLevel w, Player p, Vec3 pos) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(w);
		if (bolt == null) return;
		bolt.moveTo(pos);
		if (p instanceof ServerPlayer sp) bolt.setCause(sp);
		w.addFreshEntity(bolt);

		fx(w, ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 1, pos.z, 80, 1.5, 1.5, 1.5, 0.5);
		fx(w, ParticleTypes.FLASH, pos.x, pos.y + 1, pos.z, 1, 0, 0, 0, 0);
		for (LivingEntity e : targets(w, p, pos, 3.5)) {
			hurt(w, p, e, 8.0f);
		}
	}

	// ------------------------------------------------------------------ 5. Earthquake

	public static void earthquake(ServerLevel w, Player p) {
		final Vec3 c = p.position();
		final double radius = 13.0;
		final int duration = 100;
		BlockState floor = floorState(w, p.blockPosition().below());
		BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, floor);

		sound(w, c, SoundEvents.GENERIC_EXPLODE, 2.0f, 0.4f);

		final int cracks = 8;
		final double[] angle = new double[cracks];
		for (int i = 0; i < cracks; i++) angle[i] = Math.PI * 2 * i / cracks + (RNG.nextDouble() - 0.5) * 0.4;

		for (int t = 0; t < duration; t++) {
			final int tick = t;
			Scheduler.later(t, () -> {
				double grown = radius * Math.min(1.0, tick / 50.0);

				for (int i = 0; i < cracks; i++) {
					for (double d = 1.5; d <= grown; d += 1.0) {
						double wob = Math.sin(d * 1.7 + i) * 0.4;
						double x = c.x + Math.cos(angle[i]) * d - Math.sin(angle[i]) * wob;
						double z = c.z + Math.sin(angle[i]) * d + Math.cos(angle[i]) * wob;
						fx(w, ParticleTypes.SMOKE, x, c.y + 0.1, z, 1, 0.1, 0.05, 0.1, 0.01);
						if (tick % 4 == 0) fx(w, ParticleTypes.LAVA, x, c.y + 0.1, z, 1, 0.1, 0.0, 0.1, 0.0);
					}
				}

				for (int i = 0; i < 18; i++) {
					double a = RNG.nextDouble() * Math.PI * 2;
					double d = Math.sqrt(RNG.nextDouble()) * grown;
					fx(w, debris, c.x + Math.cos(a) * d, c.y + 0.1, c.z + Math.sin(a) * d, 4, 0.25, 0.3, 0.25, 0.25);
				}
				if (tick % 5 == 0) {
					double a = RNG.nextDouble() * Math.PI * 2;
					double d = RNG.nextDouble() * grown;
					fx(w, ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x + Math.cos(a) * d, c.y + 0.2, c.z + Math.sin(a) * d, 1, 0.2, 0.0, 0.2, 0.02);
				}

				if (tick % 3 == 0) sound(w, c, SoundEvents.DEEPSLATE_BREAK, 2.0f, 0.5f + RNG.nextFloat() * 0.3f);
				if (tick % 10 == 0) sound(w, c, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.4f);

				for (LivingEntity e : targets(w, p, c, radius)) {
					if (Math.abs(e.getDeltaMovement().y) < 0.1) {
						e.push((RNG.nextDouble() - 0.5) * 0.25, 0.18, (RNG.nextDouble() - 0.5) * 0.25);
						e.hurtMarked = true;
					}
					if (tick % 10 == 0) {
						hurt(w, p, e, 3.0f);
						e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
					}
				}
			});
		}

		Scheduler.later(duration, () -> {
			sound(w, c, SoundEvents.GENERIC_EXPLODE, 3.0f, 0.5f);
			fx(w, debris, c.x, c.y + 0.3, c.z, 300, radius / 2, 0.5, radius / 2, 0.6);
			for (LivingEntity e : targets(w, p, c, radius)) {
				hurt(w, p, e, 6.0f);
				knock(e, c, 0.7, 0.9);
			}
		});
	}
}
