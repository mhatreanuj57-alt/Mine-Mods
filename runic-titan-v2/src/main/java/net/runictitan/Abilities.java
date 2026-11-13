package net.runictitan;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

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

	/** Spawns particles for every player, even far away (force = long range). */
	static void fx(ServerWorld w, ParticleEffect type, double x, double y, double z,
				   int n, double dx, double dy, double dz, double speed) {
		for (ServerPlayerEntity sp : w.getPlayers()) {
			w.spawnParticles(sp, type, true, x, y, z, n, dx, dy, dz, speed);
		}
	}

	static void sound(ServerWorld w, Vec3d p, SoundEvent ev, float vol, float pitch) {
		w.playSound(null, p.x, p.y, p.z, ev, SoundCategory.PLAYERS, vol, pitch);
	}

	static List<LivingEntity> targets(ServerWorld w, PlayerEntity caster, Vec3d c, double r) {
		Box box = new Box(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
		return w.getEntitiesByClass(LivingEntity.class, box,
				e -> e != caster && e.isAlive() && !(e instanceof ArmorStandEntity) && e.squaredDistanceTo(c) <= r * r);
	}

	static void hurt(ServerWorld w, PlayerEntity caster, LivingEntity e, float amount) {
		e.damage(w.getDamageSources().playerAttack(caster), amount);
	}

	static void knock(LivingEntity e, Vec3d from, double strength, double up) {
		Vec3d d = new Vec3d(e.getX() - from.x, 0, e.getZ() - from.z);
		if (d.lengthSquared() < 1.0E-4) d = new Vec3d(RNG.nextDouble() - 0.5, 0, RNG.nextDouble() - 0.5);
		d = d.normalize().multiply(strength);
		e.addVelocity(d.x, up, d.z);
		e.velocityModified = true;
	}

	static Vec3d aim(PlayerEntity p, double max) {
		HitResult h = p.raycast(max, 1.0f, false);
		return h.getPos();
	}

	/** Finds the top of the ground at/below the given point. */
	static Vec3d groundAt(ServerWorld w, Vec3d v) {
		BlockPos.Mutable m = BlockPos.ofFloored(v).mutableCopy();
		int steps = 0;
		while (steps < 80 && m.getY() > w.getBottomY() && !w.getBlockState(m).isSolidBlock(w, m)) {
			m.move(0, -1, 0);
			steps++;
		}
		return new Vec3d(v.x, m.getY() + 1.0, v.z);
	}

	static BlockState floorState(ServerWorld w, BlockPos below) {
		BlockState s = w.getBlockState(below);
		if (s.isAir() || !s.getFluidState().isEmpty()) return Blocks.STONE.getDefaultState();
		return s;
	}

	static void ring(ServerWorld w, Vec3d c, double r, ParticleEffect type, int perBlock, double dy, double speed) {
		int n = Math.max(8, (int) (r * perBlock));
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			fx(w, type, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 1, 0.1, dy, 0.1, speed);
		}
	}

	// ------------------------------------------------------------------ 1. Ground Smash

	public static void groundSmash(ServerWorld w, PlayerEntity p) {
		Vec3d c = p.getPos();
		BlockState floor = floorState(w, p.getBlockPos().down());
		BlockStateParticleEffect debris = new BlockStateParticleEffect(ParticleTypes.BLOCK, floor);

		sound(w, c, SoundEvents.BLOCK_ANVIL_LAND, 1.5f, 0.5f);
		sound(w, c, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
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

	public static void meteorStrike(ServerWorld w, PlayerEntity p) {
		Vec3d target = groundAt(w, aim(p, 55));

		// pick a launch point high in the sky that is actually air
		Vec3d start = null;
		for (int h = 50; h >= 15; h -= 5) {
			double y = Math.min(target.y + h, w.getTopY() - 3);
			Vec3d cand = new Vec3d(target.x + 16, y, target.z + 10);
			if (w.getBlockState(BlockPos.ofFloored(cand)).isAir()) { start = cand; break; }
		}
		if (start == null) start = new Vec3d(target.x + 6, target.y + 14, target.z + 4);
		final Vec3d from = start;

		// the rock itself: a small cluster of glowing / dark blocks
		final Vec3d[] offsets = { new Vec3d(0, 0, 0), new Vec3d(0.9, 0.5, 0.1), new Vec3d(-0.7, 0.2, 0.8), new Vec3d(0.1, 0.9, -0.8) };
		final BlockState[] states = { Blocks.MAGMA_BLOCK.getDefaultState(), Blocks.BLACKSTONE.getDefaultState(),
				Blocks.MAGMA_BLOCK.getDefaultState(), Blocks.OBSIDIAN.getDefaultState() };
		final List<FallingBlockEntity> rocks = new ArrayList<>();
		for (int i = 0; i < offsets.length; i++) {
			FallingBlockEntity fb = FallingBlockEntity.spawnFromBlock(w, BlockPos.ofFloored(from), states[i]);
			fb.dropItem = false;
			fb.setNoGravity(true);
			fb.setInvulnerable(true);
			fb.setHurtEntities(0.0f, 0);
			fb.refreshPositionAndAngles(from.x + offsets[i].x, from.y + offsets[i].y, from.z + offsets[i].z, 0, 0);
			rocks.add(fb);
		}

		sound(w, target, SoundEvents.ENTITY_BLAZE_SHOOT, 3.0f, 0.4f);
		sound(w, target, SoundEvents.ENTITY_WITHER_SPAWN, 0.6f, 1.6f);

		final int flight = 28;
		final Vec3d[] last = { from };
		for (int t = 1; t <= flight; t++) {
			final int tick = t;
			Scheduler.later(t, () -> {
				double f = Math.pow((double) tick / flight, 1.5);
				Vec3d pos = from.lerp(target, f);

				for (int i = 0; i < rocks.size(); i++) {
					FallingBlockEntity fb = rocks.get(i);
					if (!fb.isRemoved()) {
						fb.refreshPositionAndAngles(pos.x + offsets[i].x, pos.y + offsets[i].y, pos.z + offsets[i].z, 0, 0);
					}
				}

				// flaming trail between last and current position
				double dist = last[0].distanceTo(pos);
				int steps = Math.max(1, (int) (dist / 1.0));
				for (int s = 0; s <= steps; s++) {
					Vec3d q = last[0].lerp(pos, (double) s / steps);
					fx(w, ParticleTypes.FLAME, q.x, q.y + 0.5, q.z, 12, 0.7, 0.7, 0.7, 0.04);
					fx(w, ParticleTypes.LAVA, q.x, q.y + 0.5, q.z, 2, 0.5, 0.5, 0.5, 0.0);
					fx(w, ParticleTypes.LARGE_SMOKE, q.x, q.y + 0.5, q.z, 6, 0.8, 0.8, 0.8, 0.02);
				}
				last[0] = pos;

				// warning ring on the ground where it will land
				ring(w, target, 4.0, ParticleTypes.FLAME, 2, 0.0, 0.0);
				if (tick % 6 == 0) sound(w, pos, SoundEvents.ITEM_FIRECHARGE_USE, 2.0f, 0.5f);

				if (tick == flight) {
					for (FallingBlockEntity fb : rocks) fb.discard();
					meteorImpact(w, p, target);
				}
			});
		}
	}

	private static void meteorImpact(ServerWorld w, PlayerEntity p, Vec3d at) {
		BlockState floor = floorState(w, BlockPos.ofFloored(at).down());
		BlockStateParticleEffect debris = new BlockStateParticleEffect(ParticleTypes.BLOCK, floor);

		w.createExplosion(p, at.x, at.y, at.z, 4.0f, METEOR_LIGHTS_FIRE,
				METEOR_BREAKS_BLOCKS ? World.ExplosionSourceType.TNT : World.ExplosionSourceType.NONE);
		sound(w, at, SoundEvents.ENTITY_GENERIC_EXPLODE, 4.0f, 0.5f);
		sound(w, at, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, 3.0f, 0.6f);

		fx(w, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
		fx(w, ParticleTypes.FLAME, at.x, at.y + 1, at.z, 300, 2.5, 0.8, 2.5, 0.35);
		fx(w, ParticleTypes.LAVA, at.x, at.y + 1, at.z, 80, 2.0, 0.5, 2.0, 0.0);
		fx(w, ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 150, 3.0, 1.5, 3.0, 0.12);
		fx(w, debris, at.x, at.y + 0.5, at.z, 200, 2.0, 0.5, 2.0, 0.5);

		// expanding fire shockwave
		for (int i = 0; i < 8; i++) {
			final double r = 2.0 + i * 1.3;
			Scheduler.later(i, () -> {
				ring(w, at, r, ParticleTypes.FLAME, 5, 0.3, 0.15);
				ring(w, at, r, ParticleTypes.SMOKE, 3, 0.1, 0.05);
			});
		}

		for (LivingEntity e : targets(w, p, at, 9.0)) {
			double d = Math.sqrt(e.squaredDistanceTo(at));
			float dmg = (float) (24.0 * (1.0 - d / 9.0)) + 4.0f;
			hurt(w, p, e, dmg);
			e.setOnFireFor(8);
			knock(e, at, 1.6, 0.9);
		}
	}

	// ------------------------------------------------------------------ 3. Gravity Well

	public static void gravityWell(ServerWorld w, PlayerEntity p) {
		Vec3d centre = groundAt(w, aim(p, 35)).add(0, 1.6, 0);
		final double radius = 10.0;
		final int duration = 45;

		sound(w, centre, SoundEvents.BLOCK_END_PORTAL_SPAWN, 1.0f, 1.8f);
		sound(w, centre, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.5f);

		for (int t = 0; t <= duration; t++) {
			final int tick = t;
			Scheduler.later(t, () -> {
				// swirling inward spiral
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
					Vec3d mid = e.getPos().add(0, e.getHeight() / 2.0, 0);
					Vec3d dir = centre.subtract(mid);
					if (dir.lengthSquared() < 0.25) continue;
					Vec3d pull = dir.normalize().multiply(0.22);
					e.setVelocity(e.getVelocity().multiply(0.85).add(pull));
					e.velocityModified = true;
					e.fallDistance = 0;
					if (tick % 10 == 0) hurt(w, p, e, 2.0f);
				}
			});
		}

		// final collapse
		Scheduler.later(duration + 1, () -> {
			sound(w, centre, SoundEvents.ENTITY_GENERIC_EXPLODE, 3.0f, 0.6f);
			sound(w, centre, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 1.0f);
			fx(w, ParticleTypes.EXPLOSION_EMITTER, centre.x, centre.y, centre.z, 1, 0, 0, 0, 0);
			fx(w, ParticleTypes.ELECTRIC_SPARK, centre.x, centre.y, centre.z, 200, 3.0, 3.0, 3.0, 0.6);
			fx(w, ParticleTypes.REVERSE_PORTAL, centre.x, centre.y, centre.z, 200, 1.0, 1.0, 1.0, 0.8);
			for (LivingEntity e : targets(w, p, centre, 7.0)) {
				hurt(w, p, e, 14.0f);
				Vec3d away = e.getPos().add(0, 0.5, 0).subtract(centre);
				Vec3d v = away.lengthSquared() < 1.0E-4 ? new Vec3d(0, 1, 0) : away.normalize().multiply(1.4);
				e.addVelocity(v.x, 0.9, v.z);
				e.velocityModified = true;
			}
		});
	}

	// ------------------------------------------------------------------ 4. Thunder Crash (real lightning)

	public static void thunderCrash(ServerWorld w, PlayerEntity p) {
		Vec3d c = p.getPos();
		List<LivingEntity> found = targets(w, p, c, 16.0);
		List<Vec3d> strikes = new ArrayList<>();
		for (LivingEntity e : found) {
			if (strikes.size() >= 8) break;
			strikes.add(e.getPos());
		}
		if (strikes.isEmpty()) {
			Vec3d g = groundAt(w, aim(p, 40));
			strikes.add(g);
			for (int i = 0; i < 3; i++) {
				strikes.add(groundAt(w, g.add((RNG.nextDouble() - 0.5) * 10, 0, (RNG.nextDouble() - 0.5) * 10)));
			}
		}

		sound(w, c, SoundEvents.ITEM_TRIDENT_THUNDER, 2.0f, 0.8f);
		ring(w, c, 2.5, ParticleTypes.ELECTRIC_SPARK, 6, 1.0, 0.3);

		for (int i = 0; i < strikes.size(); i++) {
			final Vec3d pos = strikes.get(i);
			Scheduler.later(i * 4, () -> lightning(w, p, pos));
		}
	}

	private static void lightning(ServerWorld w, PlayerEntity p, Vec3d pos) {
		LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
		if (bolt == null) return;
		bolt.refreshPositionAfterTeleport(pos);
		if (p instanceof ServerPlayerEntity sp) bolt.setChanneler(sp);
		w.spawnEntity(bolt);

		fx(w, ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 1, pos.z, 80, 1.5, 1.5, 1.5, 0.5);
		fx(w, ParticleTypes.FLASH, pos.x, pos.y + 1, pos.z, 1, 0, 0, 0, 0);
		for (LivingEntity e : targets(w, p, pos, 3.5)) {
			hurt(w, p, e, 8.0f);
		}
	}

	// ------------------------------------------------------------------ 5. Earthquake

	public static void earthquake(ServerWorld w, PlayerEntity p) {
		final Vec3d c = p.getPos();
		final double radius = 13.0;
		final int duration = 100;
		BlockState floor = floorState(w, p.getBlockPos().down());
		BlockStateParticleEffect debris = new BlockStateParticleEffect(ParticleTypes.BLOCK, floor);

		sound(w, c, SoundEvents.ENTITY_GENERIC_EXPLODE, 2.0f, 0.4f);

		// radial cracks that grow outward
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

				// flying chunks of ground
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

				if (tick % 3 == 0) sound(w, c, SoundEvents.BLOCK_DEEPSLATE_BREAK, 2.0f, 0.5f + RNG.nextFloat() * 0.3f);
				if (tick % 10 == 0) sound(w, c, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0f, 0.4f);

				// shake, throw and hurt everything in range
				for (LivingEntity e : targets(w, p, c, radius)) {
					if (e.isOnGround()) {
						e.addVelocity((RNG.nextDouble() - 0.5) * 0.25, 0.18, (RNG.nextDouble() - 0.5) * 0.25);
						e.velocityModified = true;
					}
					if (tick % 10 == 0) {
						hurt(w, p, e, 3.0f);
						e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2));
					}
				}
			});
		}

		// final aftershock
		Scheduler.later(duration, () -> {
			sound(w, c, SoundEvents.ENTITY_GENERIC_EXPLODE, 3.0f, 0.5f);
			fx(w, debris, c.x, c.y + 0.3, c.z, 300, radius / 2, 0.5, radius / 2, 0.6);
			for (LivingEntity e : targets(w, p, c, radius)) {
				hurt(w, p, e, 6.0f);
				knock(e, c, 0.7, 0.9);
			}
		});
	}
}
