package dev.missilesnukes.common;

import dev.missilesnukes.sim.DebrisBody;
import dev.missilesnukes.sim.MissileSpec;
import dev.missilesnukes.sim.FlightState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * A stage that has physically separated from a missile (the engine section or the spent central body). It carries the
 * velocity and attitude it had at separation plus the separation impulse, then tumbles and falls on its own.
 */
public class MissilePartEntity extends Entity {
    public static final int ENGINE = 0, BODY = 1;
    public static final double HALF = 0.4;
    private static final int MAX_AGE = 20 * 300;

    private static final EntityDataAccessor<Integer> D_TYPE = SynchedEntityData.defineId(MissilePartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_SPEC = SynchedEntityData.defineId(MissilePartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Quaternionf> D_ORIENT = SynchedEntityData.defineId(MissilePartEntity.class, EntityDataSerializers.QUATERNION);

    private DebrisBody body;
    private final ChunkLoading.Tracker chunks = new ChunkLoading.Tracker();
    private final Quaternionf prevOrient = new Quaternionf(), curOrient = new Quaternionf();

    public MissilePartEntity(EntityType<? extends MissilePartEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Creates the detached stage described by the separation outputs stored in the flight state. */
    public static void spawn(ServerLevel level, int type, FlightState fs, Entity parent) {
        MissilePartEntity e = ModEntities.PART.get().create(level);
        if (e == null) return;
        e.entityData.set(D_TYPE, type);
        e.entityData.set(D_SPEC, fs.spec.index());
        double mass = type == ENGINE ? fs.spec.mEngine : fs.spec.mBody + fs.fuel;
        e.body = new DebrisBody(type == ENGINE ? fs.spec.debrisCdaEngine : fs.spec.debrisCdaBody, mass);
        e.body.px = fs.sepX; e.body.py = fs.sepY; e.body.pz = fs.sepZ;
        e.body.vx = fs.sepVx; e.body.vy = fs.sepVy; e.body.vz = fs.sepVz;
        e.body.q.set(fs.sepQ);
        RandomSource r = level.random;
        double ax = r.nextGaussian(), ay = r.nextGaussian() * 0.3, az = r.nextGaussian();
        double len = Math.sqrt(ax * ax + ay * ay + az * az);
        double spin = 0.015 + 0.05 * r.nextDouble();                 // radians per tick, from the off-centre separation impulse
        e.body.wx = ax / len * spin; e.body.wy = ay / len * spin; e.body.wz = az / len * spin;
        e.setPos(fs.sepX, fs.sepY - HALF, fs.sepZ);
        e.syncOrientation();
        level.addFreshEntity(e);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(D_TYPE, ENGINE);
        b.define(D_SPEC, 0);
        b.define(D_ORIENT, new Quaternionf());
    }

    public int partType() { return entityData.get(D_TYPE); }
    public MissileSpec spec() { return MissileSpec.byIndex(entityData.get(D_SPEC)); }
    public Quaternionf renderOrientation(float partialTick) { return new Quaternionf(prevOrient).slerp(curOrient, partialTick); }

    private void syncOrientation() {
        entityData.set(D_ORIENT, new Quaternionf((float) body.q.x, (float) body.q.y, (float) body.q.z, (float) body.q.w));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) { clientTick(); return; }
        if (body == null || !(level() instanceof ServerLevel sl)) { discard(); return; }

        Vec3 from = new Vec3(body.px, body.py, body.pz);
        body.step();
        Vec3 to = new Vec3(body.px, body.py, body.pz);

        BlockHitResult hit = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        if (hit.getType() == HitResult.Type.BLOCK) { impact(sl, hit.getLocation()); return; }

        setPos(to.x, to.y - HALF, to.z);
        setDeltaMovement(body.vx, body.vy, body.vz);
        this.hasImpulse = true;
        syncOrientation();
        chunks.update(sl, this, to.x, to.z, body.vx, body.vz);
        if (body.age > MAX_AGE || to.y < sl.getMinBuildHeight() - 256) discard();
    }

    private void impact(ServerLevel sl, Vec3 at) {
        chunks.release(sl, this);
        float power = (float) ((partType() == ENGINE ? 2.5 : 4.0) * spec().craterScale);
        sl.explode(null, at.x, at.y, at.z, power,
            Config.DEBRIS_BLOCK_DAMAGE.get() ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        discard();
    }

    private void clientTick() {
        prevOrient.set(curOrient);
        curOrient.set(entityData.get(D_ORIENT));
        if (tickCount <= 1) prevOrient.set(curOrient);
        if (tickCount < 240 && tickCount % 2 == 0) {
            level().addParticle(partType() == ENGINE ? ParticleTypes.SMOKE : ParticleTypes.LARGE_SMOKE, getX(), getY() + HALF, getZ(), 0, 0.01, 0);
        }
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double distSqr) { return distSqr < 384.0 * 384.0; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(3.5); }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && level() instanceof ServerLevel sl) chunks.release(sl, this);
        super.remove(reason);
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { }
}
