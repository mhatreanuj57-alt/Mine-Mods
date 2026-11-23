package dev.missilesnukes.common;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Visual detonation: drives the explosion GLB animation and the client-side shockwave ring / smoke particles. */
public class ExplosionFxEntity extends Entity {
    public static final int LIFETIME_TICKS = 20 * 7;
    public static final double SHOCK_SPEED = 2.5;

    private static final EntityDataAccessor<Float> D_SCALE = SynchedEntityData.defineId(ExplosionFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_RING = SynchedEntityData.defineId(ExplosionFxEntity.class, EntityDataSerializers.FLOAT);

    public ExplosionFxEntity(EntityType<? extends ExplosionFxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void configure(float modelScale, float shockwaveRadius) {
        entityData.set(D_SCALE, modelScale);
        entityData.set(D_RING, shockwaveRadius);
    }

    public float modelScale() { return entityData.get(D_SCALE); }
    public float shockwaveRadius() { return entityData.get(D_RING); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(D_SCALE, 4.0f);
        b.define(D_RING, 80.0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (tickCount > LIFETIME_TICKS) discard();
            return;
        }
        float s = modelScale();
        if (tickCount == 1) level().addParticle(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 0, 0, 0);

        // expanding ground-level shockwave ring, in step with the server-side damage wave
        double r = SHOCK_SPEED * tickCount;
        if (r < shockwaveRadius() && tickCount > 0) {
            int n = Math.min(240, 24 + (int) (r * 2.5));
            for (int i = 0; i < n; i++) {
                double a = (Math.PI * 2 * i) / n + random.nextDouble() * 0.05;
                double dx = Math.cos(a), dz = Math.sin(a);
                level().addParticle(i % 3 == 0 ? ParticleTypes.POOF : ParticleTypes.CLOUD,
                    getX() + dx * r, getY() + 0.8, getZ() + dz * r, dx * 0.35, 0.03, dz * 0.35);
            }
        }
        if (tickCount < 90) {
            int n = Math.max(2, (int) (s * 1.6));
            for (int i = 0; i < n; i++) {
                double ox = (random.nextDouble() - 0.5) * s * 3.0, oz = (random.nextDouble() - 0.5) * s * 3.0;
                double oy = random.nextDouble() * s * Math.min(6.0, 1.0 + tickCount * 0.12);
                level().addParticle(ParticleTypes.LARGE_SMOKE, getX() + ox, getY() + oy, getZ() + oz, 0, 0.12, 0);
                if (tickCount < 45) {
                    level().addParticle(ParticleTypes.FLAME, getX() + ox * 0.6, getY() + oy * 0.7, getZ() + oz * 0.6, 0, 0.18, 0);
                    if (i % 3 == 0) level().addParticle(ParticleTypes.LAVA, getX() + ox * 0.5, getY() + oy * 0.5, getZ() + oz * 0.5, 0, 0, 0);
                }
            }
        }
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double distSqr) { return distSqr < 512.0 * 512.0; }
    @Override public AABB getBoundingBoxForCulling() { float r = modelScale() * 9.0f; return new AABB(getX() - r, getY() - 2, getZ() - r, getX() + r, getY() + r * 1.4, getZ() + r); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { }
}
