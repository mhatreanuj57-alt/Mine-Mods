package net.runictitan;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Plays the voxel meteor impact animation (converted from the GLB) using item display entities.
 * Data file: data/runictitan/vfx/meteor_impact.bin
 */
public final class MeteorVfx {
	private static final int MAGIC = 0x4D455445;
	/** Animation frame (10 fps) at which the meteor touches the ground (t = 1.5 s). */
	public static final int IMPACT_FRAME = 15;

	private static boolean loaded;
	private static boolean failed;
	private static int nodes, frames, ticksPerFrame, meteorNodes;
	private static byte[] mats;
	private static float[] centre;
	private static float[] data;

	private MeteorVfx() {}

	private static synchronized boolean load() {
		if (loaded) return true;
		if (failed) return false;
		try (InputStream in = MeteorVfx.class.getResourceAsStream("/data/runictitan/vfx/meteor_impact.bin")) {
			if (in == null) { failed = true; return false; }
			DataInputStream d = new DataInputStream(new BufferedInputStream(in, 1 << 16));
			if (d.readInt() != MAGIC) { failed = true; return false; }
			nodes = d.readInt();
			frames = d.readInt();
			ticksPerFrame = d.readInt();
			meteorNodes = d.readInt();
			mats = new byte[nodes];
			d.readFully(mats);
			centre = new float[frames * 3];
			for (int i = 0; i < centre.length; i++) centre[i] = d.readFloat();
			data = new float[frames * nodes * 10];
			for (int i = 0; i < data.length; i++) data[i] = d.readFloat();
			loaded = true;
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			failed = true;
			return false;
		}
	}

	private static ListTag floats(float... v) {
		ListTag l = new ListTag();
		for (float f : v) l.add(FloatTag.valueOf(f));
		return l;
	}

	private static boolean visible(int f, int n) {
		return data[(f * nodes + n) * 10 + 7] > 0.01f;
	}

	/** Writes the (yaw-rotated) transformation of node n at frame f into the entity data. */
	private static void setState(CompoundTag base, int f, int n, double cos, double sin, double hsin, double hcos, int duration) {
		int o = (f * nodes + n) * 10;
		double tx = data[o], ty = data[o + 1], tz = data[o + 2];
		double qx = data[o + 3], qy = data[o + 4], qz = data[o + 5], qw = data[o + 6];
		float rx = (float) (tx * cos + tz * sin);
		float rz = (float) (-tx * sin + tz * cos);
		// q' = yawQuat * q   (yawQuat = (0, hsin, 0, hcos))
		double ax = 0, ay = hsin, az = 0, aw = hcos;
		float nw = (float) (aw * qw - ax * qx - ay * qy - az * qz);
		float nx = (float) (aw * qx + ax * qw + ay * qz - az * qy);
		float ny = (float) (aw * qy - ax * qz + ay * qw + az * qx);
		float nz = (float) (aw * qz + ax * qy - ay * qx + az * qw);

		CompoundTag t = new CompoundTag();
		t.put("translation", floats(rx, (float) ty, rz));
		t.put("left_rotation", floats(nx, ny, nz, nw));
		t.put("scale", floats(data[o + 7], data[o + 8], data[o + 9]));
		t.put("right_rotation", floats(0f, 0f, 0f, 1f));
		base.put("transformation", t);
		base.putInt("interpolation_duration", duration);
		base.putInt("start_interpolation", 0);
	}

	/**
	 * Starts the animation. The meteor comes in from the side of the impact point that lies along
	 * (dirX, dirZ), so it flies in over the target towards the caster.
	 *
	 * @return false if the animation data could not be loaded (caller should use a fallback)
	 */
	public static boolean play(ServerLevel w, Vec3 origin, double dirX, double dirZ, Runnable onImpact) {
		if (!load()) return false;

		double len = Math.hypot(dirX, dirZ);
		if (len < 1.0E-4) { dirX = 1; dirZ = 0; len = 1; }
		dirX /= len;
		dirZ /= len;
		double alphaA = Math.atan2(centre[2], centre[0]);   // original start direction in the x/z plane
		double alphaU = Math.atan2(dirZ, dirX);
		double theta = alphaA - alphaU;
		final double cos = Math.cos(theta), sin = Math.sin(theta);
		final double hsin = Math.sin(theta / 2.0), hcos = Math.cos(theta / 2.0);

		final Entity[] ents = new Entity[nodes];
		final CompoundTag[] base = new CompoundTag[nodes];
		for (int n = 0; n < nodes; n++) {
			Entity e = EntityType.ITEM_DISPLAY.create(w);
			if (e == null) continue;
			e.moveTo(origin.x, origin.y, origin.z, 0f, 0f);
			CompoundTag b = new CompoundTag();
			e.saveWithoutId(b);

			CompoundTag item = new CompoundTag();
			item.putString("id", "runictitan:vfx_cube");
			item.putByte("Count", (byte) 1);
			CompoundTag tag = new CompoundTag();
			tag.putInt("CustomModelData", (mats[n] & 0xFF) + 1);
			item.put("tag", tag);
			b.put("item", item);
			b.putString("item_display", "none");
			b.putFloat("view_range", 4.0f);
			b.putFloat("shadow_radius", 0.0f);
			if (mats[n] == 1 || mats[n] == 2) {          // magma + fire core glow
				CompoundTag br = new CompoundTag();
				br.putInt("block", 15);
				br.putInt("sky", 15);
				b.put("brightness", br);
			}
			setState(b, 0, n, cos, sin, hsin, hcos, 0);
			e.load(b);
			w.addFreshEntity(e);
			ents[n] = e;
			base[n] = b;
		}

		final int tpf = ticksPerFrame;
		final double ox = origin.x, oy = origin.y, oz = origin.z;
		for (int f = 1; f < frames; f++) {
			final int ff = f;
			Scheduler.later(1 + (f - 1) * tpf, () -> {
				for (int n = 0; n < nodes; n++) {
					if (ents[n] == null) continue;
					boolean prev = visible(ff - 1, n), now = visible(ff, n);
					if (!prev && !now) continue;
					setState(base[n], ff, n, cos, sin, hsin, hcos, (prev && now) ? tpf : 0);
					ents[n].load(base[n]);
				}
				if (ff <= IMPACT_FRAME) {
					// fiery trail between the previous and the current centre of the meteor
					double ax = centre[(ff - 1) * 3], ay = centre[(ff - 1) * 3 + 1], az = centre[(ff - 1) * 3 + 2];
					double bx = centre[ff * 3], by = centre[ff * 3 + 1], bz = centre[ff * 3 + 2];
					for (int s = 0; s <= 3; s++) {
						double k = s / 3.0;
						double x = ax + (bx - ax) * k, y = ay + (by - ay) * k, z = az + (bz - az) * k;
						double wx = ox + x * cos + z * sin, wz = oz + (-x * sin + z * cos), wy = oy + y;
						Abilities.fx(w, ParticleTypes.FLAME, wx, wy, wz, 14, 1.2, 1.2, 1.2, 0.05);
						Abilities.fx(w, ParticleTypes.LARGE_SMOKE, wx, wy, wz, 8, 1.4, 1.4, 1.4, 0.03);
						Abilities.fx(w, ParticleTypes.LAVA, wx, wy, wz, 2, 1.0, 1.0, 1.0, 0.0);
					}
				}
			});
		}

		Scheduler.later(1 + IMPACT_FRAME * tpf, onImpact);
		Scheduler.later(1 + frames * tpf + 4, () -> {
			for (Entity e : ents) if (e != null) e.discard();
		});
		return true;
	}
}
