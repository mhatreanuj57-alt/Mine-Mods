package dev.missilesnukes.common;

import dev.missilesnukes.sim.Flight;
import dev.missilesnukes.sim.FlightSim;
import dev.missilesnukes.sim.FlightState;
import dev.missilesnukes.sim.FlightState.Stage;
import dev.missilesnukes.sim.MissileSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One Light ICBM. It owns its complete flight state (position, velocity, attitude, fuel, target, stage and the
 * separation event log) and advances it on the server with the deterministic FlightSim. The entity position is the
 * centre of the model (spec.halfHeight blocks above the entity's feet). Two entity types share this class: the Light
 * and the Heavy ICBM, each with its own MissileSpec.
 *
 * After the warhead separates, this entity continues as the warhead itself; the engine section and the spent central
 * body become independent {@link MissilePartEntity} objects.
 */
public class MissileEntity extends Entity {
    public static final int ATTACH_ENGINE = 1, ATTACH_BODY = 2;

    private static final EntityDataAccessor<Integer> D_STAGE = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Quaternionf> D_ORIENT = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Float> D_THRUST = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> D_FX = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> D_ATTACH = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> D_TARGET = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> D_HAS_TARGET = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> D_PLAN = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_TICK = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> D_APOGEE = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_FUEL = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> D_PIVOT = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.BOOLEAN);

    /** Authoritative flight state (valid on the server; the client only mirrors synced data). */
    public final MissileSpec spec;
    public FlightState fs;
    private final ChunkLoading.Tracker chunks = new ChunkLoading.Tracker();

    // client-side smoothing of the synced attitude
    private final Quaternionf prevOrient = new Quaternionf(), curOrient = new Quaternionf();

    public MissileEntity(EntityType<? extends MissileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.spec = type == ModEntities.HEAVY_MISSILE.get() ? MissileSpec.HEAVY : MissileSpec.LIGHT;
        this.fs = FlightSim.prepare(spec, 0, 0, 0, 0, 0, 0);
    }

    public MissileSpec spec() { return spec; }
    public double half() { return spec.halfHeight; }

    // ------------------------------------------------------------------------------------------ synced data

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(D_STAGE, 0);
        b.define(D_ORIENT, new Quaternionf());
        b.define(D_THRUST, 0f);
        b.define(D_FX, true);
        b.define(D_ATTACH, ATTACH_ENGINE | ATTACH_BODY);
        b.define(D_TARGET, BlockPos.ZERO);
        b.define(D_HAS_TARGET, false);
        b.define(D_PLAN, -1);
        b.define(D_TICK, 0);
        b.define(D_APOGEE, 0f);
        b.define(D_FUEL, 1f);
        b.define(D_PIVOT, false);
    }

    public Stage stage() { return Stage.byId(entityData.get(D_STAGE)); }
    public float thrust() { return entityData.get(D_THRUST); }
    public boolean thrusterFx() { return entityData.get(D_FX); }
    public boolean engineAttached() { return (entityData.get(D_ATTACH) & ATTACH_ENGINE) != 0; }
    public boolean bodyAttached() { return (entityData.get(D_ATTACH) & ATTACH_BODY) != 0; }
    public boolean warheadPivot() { return entityData.get(D_PIVOT); }
    public boolean hasTarget() { return entityData.get(D_HAS_TARGET); }
    public BlockPos targetBlock() { return entityData.get(D_TARGET); }
    public int plannedTicks() { return entityData.get(D_PLAN); }
    public int flightTick() { return entityData.get(D_TICK); }
    public float plannedApogeeY() { return entityData.get(D_APOGEE); }
    public float fuelFraction() { return entityData.get(D_FUEL); }
    public Vec3 center() { return new Vec3(getX(), getY() + spec.halfHeight, getZ()); }

    public void setThrusterFx(boolean on) { entityData.set(D_FX, on); }

    public Quaternionf renderOrientation(float partialTick) {
        return new Quaternionf(prevOrient).slerp(curOrient, partialTick);
    }

    private void setCenter(double x, double y, double z) { setPos(x, y - spec.halfHeight, z); }

    private void syncData() {
        entityData.set(D_STAGE, fs.stage.ordinal());
        entityData.set(D_THRUST, (float) fs.thrustFrac);
        entityData.set(D_ATTACH, (fs.engineAttached ? ATTACH_ENGINE : 0) | (fs.bodyAttached ? ATTACH_BODY : 0));
        entityData.set(D_TICK, (int) fs.tick);
        entityData.set(D_FUEL, (float) Math.max(0, Math.min(1, fs.fuel / spec.fuelCapacity)));
        entityData.set(D_PIVOT, fs.warheadPivot);
        entityData.set(D_ORIENT, new Quaternionf((float) fs.q.x, (float) fs.q.y, (float) fs.q.z, (float) fs.q.w));
    }

    // ------------------------------------------------------------------------------------------ pad / launch

    /** Stands the missile upright on the ground at (x, groundY, z). */
    public void placeOnPad(double x, double groundY, double z) {
        fs = FlightSim.prepare(spec, x, groundY + spec.halfHeight, z, x, groundY + spec.halfHeight, z);
        setCenter(x, groundY + spec.halfHeight, z);
        syncData();
    }

    /** Result of a dry run for this missile standing on its pad; null if it is not on the pad. */
    public FlightSim.Plan planFor(int tx, int ty, int tz) {
        if (fs.stage != Stage.PAD) return null;
        Vec3 c = center();
        return FlightSim.dryRun(spec, c.x, c.y, c.z, tx + 0.5, ty + 1.0, tz + 0.5);
    }

    /** Server only. Returns null on success, otherwise a short reason why the launch was refused. */
    public String tryLaunch(int tx, int ty, int tz) {
        if (level().isClientSide) return "client";
        if (fs.stage != Stage.PAD) return "already launched";
        FlightSim.Plan plan = planFor(tx, ty, tz);
        if (plan == null || !plan.feasible) return plan == null ? "not on pad" : plan.reason;
        Vec3 c = center();
        fs = FlightSim.prepare(spec, c.x, c.y, c.z, tx + 0.5, ty + 1.0, tz + 0.5);
        FlightSim.ignite(fs);
        entityData.set(D_TARGET, new BlockPos(tx, ty, tz));
        entityData.set(D_HAS_TARGET, true);
        entityData.set(D_PLAN, (int) plan.flightTicks);
        entityData.set(D_APOGEE, (float) (plan.apogeeAboveTarget + ty + 1.0));
        syncData();
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 4.0f, 0.5f);
        return null;
    }

    // ------------------------------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) clientTick();
        else serverTick();
    }

    private void serverTick() {
        ServerLevel sl = (ServerLevel) level();
        if (fs.stage == Stage.PAD) return;

        Vec3 from = center();
        int ev = FlightSim.step(fs, true);

        if (fs.stage == Stage.IGNITION) {                       // held down on the pad while thrust builds
            syncData();
            if (tickCount % 5 == 0) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 3.0f, 0.5f);
            return;
        }

        Vec3 to = new Vec3(fs.px, fs.py, fs.pz);

        if ((ev & FlightState.EV_LIFTOFF) != 0) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 6.0f, 0.4f);
        }
        if ((ev & FlightState.EV_ENGINE_SEP) != 0) {
            MissilePartEntity.spawn(sl, MissilePartEntity.ENGINE, fs, this);
            separationEffects(sl, new Vec3(fs.sepX, fs.sepY, fs.sepZ));
        }
        if ((ev & FlightState.EV_WARHEAD_SEP) != 0) {
            MissilePartEntity.spawn(sl, MissilePartEntity.BODY, fs, this);
            separationEffects(sl, new Vec3(fs.sepX, fs.sepY, fs.sepZ));
        }
        if (fs.thrustFrac > 0.15 && tickCount % 6 == 0) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 4.0f, 0.5f);
        }

        // swept collision test so that a fast warhead can never tunnel through terrain
        BlockHitResult hit = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        if (hit.getType() == HitResult.Type.BLOCK) {
            impact(hit.getLocation());
            return;
        }

        setCenter(to.x, to.y, to.z);
        setDeltaMovement(fs.vx, fs.vy, fs.vz);
        this.hasImpulse = true;
        syncData();
        chunks.update(sl, this, to.x, to.z, fs.vx, fs.vz);

        if (to.y < sl.getMinBuildHeight() - 256) discard();
    }

    private void separationEffects(ServerLevel sl, Vec3 at) {
        sl.sendParticles(ParticleTypes.FIREWORK, at.x, at.y, at.z, 24, 0.3, 0.3, 0.3, 0.08);
        sl.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.02);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.DISPENSER_LAUNCH, SoundSource.HOSTILE, 6.0f, 0.6f);
    }

    /** The warhead hit something: detonate. */
    private void impact(Vec3 at) {
        if (level() instanceof ServerLevel sl) {
            fs.stage = Stage.IMPACT;
            chunks.release(sl, this);
            ImpactManager.detonate(sl, at, this, spec);
        }
        discard();
    }

    private void clientTick() {
        prevOrient.set(curOrient);
        curOrient.set(entityData.get(D_ORIENT));
        if (tickCount <= 1) prevOrient.set(curOrient);

        Stage st = stage();
        float thrust = thrust();
        if (thrusterFx() && engineAttached() && thrust > 0.05f && st.ordinal() >= Stage.IGNITION.ordinal() && st.ordinal() <= Stage.CUTOFF.ordinal()) {
            Vector3f nozzle = new Vector3f(0f, (float) (-spec.halfHeight - 0.3), 0f);
            curOrient.transform(nozzle);
            Vector3f dir = new Vector3f(0f, -1f, 0f);
            curOrient.transform(dir);
            Vec3 c = center();
            int n = 1 + (int) (thrust * 3);
            for (int i = 0; i < n; i++) {
                double jx = (random.nextDouble() - 0.5) * 0.4, jz = (random.nextDouble() - 0.5) * 0.4;
                level().addParticle(i % 2 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    c.x + nozzle.x + jx, c.y + nozzle.y, c.z + nozzle.z + jz,
                    dir.x * 0.25 + jx * 0.1, dir.y * 0.25, dir.z * 0.25 + jz * 0.1);
            }
            if (st == Stage.IGNITION || (st == Stage.ASCENT && flightTick() < 70)) {
                level().addParticle(ParticleTypes.CLOUD, c.x + (random.nextDouble() - 0.5) * 3, getY() + 0.2, c.z + (random.nextDouble() - 0.5) * 3,
                    (random.nextDouble() - 0.5) * 0.4, 0.02, (random.nextDouble() - 0.5) * 0.4);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ interaction

    @Override public boolean isPickable() { return !isRemoved() && stage() == Stage.PAD; }
    @Override public boolean canBeCollidedWith() { return stage() == Stage.PAD; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double distSqr) { return distSqr < 384.0 * 384.0; }

    @Override
    public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(4.0, 3.0, 4.0); }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ModItems.LAUNCH_REMOTE.get())) {
            if (!level().isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp) RemoteLogic.linkFromClick(sp, held, this);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (stage() == Stage.PAD && player.isSecondaryUseActive() && held.isEmpty()) {
            if (!level().isClientSide) {
                spawnAtLocation(spec == MissileSpec.HEAVY ? ModItems.HEAVY_MISSILE.get() : ModItems.COMPLETE_MISSILE.get());
                discard();
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    // ------------------------------------------------------------------------------------------ lifecycle

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && level() instanceof ServerLevel sl) chunks.release(sl, this);
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        FlightNbt.save(fs, tag);
        tag.putBoolean("fx", thrusterFx());
        tag.putBoolean("hasTarget", hasTarget());
        tag.putInt("tx", targetBlock().getX()); tag.putInt("ty", targetBlock().getY()); tag.putInt("tz", targetBlock().getZ());
        tag.putInt("plan", plannedTicks());
        tag.putFloat("apogee", plannedApogeeY());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        FlightNbt.load(fs, tag);
        if (fs.stage == Stage.PAD) { fs.px = getX(); fs.py = getY() + spec.halfHeight; fs.pz = getZ(); fs.padX = fs.px; fs.padY = fs.py; fs.padZ = fs.pz; }
        entityData.set(D_FX, !tag.contains("fx") || tag.getBoolean("fx"));
        entityData.set(D_HAS_TARGET, tag.getBoolean("hasTarget"));
        entityData.set(D_TARGET, new BlockPos(tag.getInt("tx"), tag.getInt("ty"), tag.getInt("tz")));
        entityData.set(D_PLAN, tag.contains("plan") ? tag.getInt("plan") : -1);
        entityData.set(D_APOGEE, tag.getFloat("apogee"));
        syncData();
    }
}
