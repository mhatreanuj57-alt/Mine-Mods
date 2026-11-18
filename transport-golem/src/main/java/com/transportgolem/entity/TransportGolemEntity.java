package com.transportgolem.entity;

import com.transportgolem.GolemConfig;
import com.transportgolem.client.ClientShake;
import com.transportgolem.model.GolemModel;
import com.transportgolem.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Golem built from 2-iron-block-tall torso pattern. Without seats it is a plain (but huge) iron golem;
 * right-click it with a Seating Arrangement to attach the saddle rig, after which it is rideable (3 seats + 27-slot chest).
 * Animations are purely visual and driven from state that both sides compute identically:
 *  - walk: phase advances with distance travelled
 *  - ground_smash: started by server (entity event 70), impact damage/particles at tick 18
 */
public class TransportGolemEntity extends IronGolem {
    private static final EntityDataAccessor<Boolean> DATA_SEATS =
            SynchedEntityData.defineId(TransportGolemEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_SEAT_0 = SynchedEntityData.defineId(TransportGolemEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_SEAT_1 = SynchedEntityData.defineId(TransportGolemEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_SEAT_2 = SynchedEntityData.defineId(TransportGolemEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<?>[] DATA_SEATS_OCCUPANTS = {DATA_SEAT_0, DATA_SEAT_1, DATA_SEAT_2};

    private static final byte EVENT_SMASH_START = 70;
    private static final byte EVENT_SMASH_IMPACT = 71;
    private static final byte EVENT_JUMP_START = 72;
    private static final byte EVENT_JUMP_LAND = 73;

    private final SimpleContainer chest = new SimpleContainer(27);

    /** -1 = not smashing, otherwise ticks since the smash started. Ticked on both sides. */
    public int smashTicks = -1;
    private int smashCooldown;
    private int jumpTicks = -1;
    private int ticksSinceGrounded;
    private boolean jumpUsed;
    private boolean jumpWasAirborne;
    private boolean jumpLanded;

    private float smoothSpeed;
    private float walkWeight, walkWeightO;
    private double walkPhase, walkPhaseO;

    public TransportGolemEntity(EntityType<? extends TransportGolemEntity> type, Level level) {
        super(type, level);
        this.setMaxUpStep(2.0F);
    }

    public static AttributeSupplier.Builder createGolemAttributes() {
        return net.minecraft.world.entity.Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 2000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ATTACK_DAMAGE, 2000.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SEATS, false);
        this.entityData.define(DATA_SEAT_0, "");
        this.entityData.define(DATA_SEAT_1, "");
        this.entityData.define(DATA_SEAT_2, "");
    }

    public boolean hasSeats() {
        return this.entityData.get(DATA_SEATS);
    }

    public void setHasSeats(boolean v) {
        this.entityData.set(DATA_SEATS, v);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("HasSeats", hasSeats());
        tag.put("ChestItems", chest.createTag());
        for (int i = 0; i < 3; i++) tag.putString("Seat" + i, seatOccupant(i));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setHasSeats(tag.getBoolean("HasSeats"));
        chest.fromTag(tag.getList("ChestItems", Tag.TAG_COMPOUND));
        for (int i = 0; i < 3; i++) setSeatOccupant(i, tag.getString("Seat" + i));
    }

    // ------------------------------------------------------------------ interaction / seating

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!hasSeats()) {
            if (player.getMainHandItem().is(ModItems.SEATING_ARRANGEMENT.get())) {
                if (!level().isClientSide) {
                    setHasSeats(true);
                    if (!player.getAbilities().instabuild) player.getMainHandItem().shrink(1);
                    playSound(SoundEvents.ANVIL_USE, 1.0F, 0.8F);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            return super.mobInteract(player, hand); // vanilla: iron ingot repairs
        }
        if (stack.is(Items.IRON_INGOT) && getHealth() < getMaxHealth()) {
            return super.mobInteract(player, hand);
        }
        if (player.isSecondaryUseActive()) {
            if (!level().isClientSide) {
                player.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> ChestMenu.threeRows(id, inv, chest),
                        Component.translatable("entity.transportgolem.transport_golem")));
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide && hasFreeSeat()) {
            player.startRiding(this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean canAddPassenger(Entity passenger) {
        return hasSeats() && hasFreeSeat();
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        if (!hasSeats()) return null;
        for (Entity passenger : getPassengers()) {
            if (seatOf(passenger) == 0 && passenger instanceof Player player) return player;
        }
        return null;
    }

    private String seatOccupant(int seat) {
        return this.entityData.get(seatData(seat));
    }

    @SuppressWarnings("unchecked")
    private EntityDataAccessor<String> seatData(int seat) {
        return (EntityDataAccessor<String>) DATA_SEATS_OCCUPANTS[seat];
    }

    private void setSeatOccupant(int seat, String uuid) {
        this.entityData.set(seatData(seat), uuid);
    }

    private int seatOf(Entity passenger) {
        String uuid = passenger.getStringUUID();
        for (int i = 0; i < 3; i++) if (seatOccupant(i).equals(uuid)) return i;
        return -1;
    }

    private int freeSeat() {
        for (int i = 0; i < 3; i++) if (seatOccupant(i).isEmpty()) return i;
        return -1;
    }

    private boolean hasFreeSeat() { return freeSeat() >= 0; }

    /** Server-side seat assignment used by the switch packet. */
    public boolean switchSeat(Player player, int seat) {
        if (!hasSeats() || seat < 0 || seat > 2 || !hasPassenger(player)) return false;
        String uuid = player.getStringUUID();
        String occupant = seatOccupant(seat);
        if (!occupant.isEmpty() && !occupant.equals(uuid)) return false;
        int oldSeat = seatOf(player);
        if (oldSeat < 0) return false;
        setSeatOccupant(oldSeat, "");
        setSeatOccupant(seat, uuid);
        return true;
    }

    @Override
    public void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (seatOf(passenger) >= 0) return;
        int seat = freeSeat();
        if (seat >= 0) setSeatOccupant(seat, passenger.getStringUUID());
    }

    @Override
    protected void removePassenger(Entity passenger) {
        int seat = seatOf(passenger);
        super.removePassenger(passenger);
        if (seat >= 0) setSeatOccupant(seat, "");
    }

    /** World position of the top surface of a seat (0 = driver, 1 = left, 2 = right). */
    public Vec3 getSeatPosition(int seat, float partial) {
        GolemModel m = GolemModel.get();
        GolemModel.Pose pose = computePose(partial);
        float[] s = GolemConfig.SEATS[Mth.clamp(seat, 0, 2)];
        Vector3f p = m.toModelSpace(m.nodeIndex("Torso_Pivot"), pose, s[0], s[1], s[2]);
        return modelToWorld(p.x, p.y, p.z, partial);
    }

    private Vec3 modelToWorld(float mx, float my, float mz, float partial) {
        double lx = mx / GolemModel.UNITS_PER_BLOCK;
        double ly = my / GolemModel.UNITS_PER_BLOCK;
        double lz = mz / GolemModel.UNITS_PER_BLOCK;
        // Renderer does mulPose(YP(180 - bodyYaw)); model front is -Z.
        double th = Math.toRadians(180.0D - Mth.rotLerp(partial, this.yBodyRotO, this.yBodyRot));
        double c = Math.cos(th);
        double sn = Math.sin(th);
        double wx = lx * c + lz * sn;
        double wz = -lx * sn + lz * c;
        return new Vec3(Mth.lerp((double) partial, this.xo, getX()) + wx,
                Mth.lerp((double) partial, this.yo, getY()) + ly,
                Mth.lerp((double) partial, this.zo, getZ()) + wz);
    }

    @Override
    public void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (!hasPassenger(passenger)) return;
        int seat = seatOf(passenger);
        if (seat < 0) return;
        Vec3 p = getSeatPosition(seat, 1.0F);
        callback.accept(passenger, p.x, p.y - GolemConfig.SIT_DROP, p.z);
        if (passenger instanceof LivingEntity) clampRotation(passenger);
    }

    protected void clampRotation(Entity e) {
        e.setYBodyRot(this.yBodyRot);
        float f = Mth.wrapDegrees(e.getYRot() - this.yBodyRot);
        float f1 = Mth.clamp(f, -105.0F, 105.0F);
        e.yRotO += f1 - f;
        e.setYRot(e.getYRot() + f1 - f);
        e.setYHeadRot(e.getYRot());
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        clampRotation(passenger);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        float[] angles = {90.0F, -90.0F, 180.0F, 0.0F};
        double[] dists = {3.4D, 4.4D};
        for (double d : dists) {
            for (float a : angles) {
                Vec3 dir = Vec3.directionFromRotation(0.0F, this.yBodyRot + a);
                BlockPos bp = BlockPos.containing(getX() + dir.x * d, getY(), getZ() + dir.z * d);
                Vec3 v = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), bp, false);
                if (v != null) return v;
            }
        }
        Vec3 dir = Vec3.directionFromRotation(0.0F, this.yBodyRot + 90.0F);
        return new Vec3(getX() + dir.x * 3.4D, getY(), getZ() + dir.z * 3.4D);
    }

    // ------------------------------------------------------------------ riding controls

    @Override
    public void travel(Vec3 travelVector) {
        if (jumpTicks >= 0) {
            Vec3 movement = getDeltaMovement();
            double yMovement = jumpHeightAt(jumpTicks + 1) - jumpHeightAt(jumpTicks);
            setDeltaMovement(movement.x, yMovement, movement.z);
        }
        LivingEntity rider = getControllingPassenger();
        if (isAlive() && isVehicle() && rider != null) {
            setYRot(rider.getYRot());
            this.yRotO = getYRot();
            setXRot(0.0F);
            setRot(getYRot(), getXRot());
            this.yBodyRot = getYRot();
            this.yHeadRot = this.yBodyRot;
            float strafe = rider.xxa * 0.5F;
            float forward = rider.zza;
            if (forward <= 0.0F) forward *= 0.25F;
            if (smashTicks >= 0) {
                strafe = 0.0F;
                forward = 0.0F;
            }
            if (isControlledByLocalInstance()) {
                setSpeed((float) getAttributeValue(Attributes.MOVEMENT_SPEED));
                super.travel(new Vec3(strafe, travelVector.y, forward));
            } else if (jumpTicks >= 0 && !level().isClientSide) {
                setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
                super.travel(Vec3.ZERO);
            } else {
                setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            }
        } else {
            super.travel(travelVector);
        }
    }

    /** Jump request from the driver, applied locally for prediction and on the server for authority. */
    public void jumpFromRider() {
        // A rider's movement packet can reach the server just after it records the golem leaving the ground.
        if (hasSeats() && (onGround() || ticksSinceGrounded <= 3) && !jumpUsed && smashTicks < 0
                && getControllingPassenger() != null) {
            jumpUsed = true;
            Vec3 movement = getDeltaMovement();
            jumpFromGround();
            if (isControlledByLocalInstance() || !level().isClientSide) {
                setDeltaMovement(movement.x, GolemConfig.JUMP_VELOCITY, movement.z);
                hasImpulse = true;
            }
            jumpTicks = 0;
            jumpWasAirborne = false;
            jumpLanded = false;
            setNoGravity(true);
            if (!level().isClientSide) level().broadcastEntityEvent(this, EVENT_JUMP_START);
        }
    }

    private double jumpHeightAt(int tick) {
        int ascent = GolemConfig.JUMP_ASCENT_TICKS;
        if (tick <= ascent) {
            double t = Math.max(0, tick);
            double deceleration = 2.0D * (GolemConfig.JUMP_VELOCITY * ascent - GolemConfig.JUMP_HEIGHT)
                    / (ascent * (ascent - 1.0D));
            return GolemConfig.JUMP_VELOCITY * t - deceleration * t * (t - 1.0D) * 0.5D;
        }
        double t = Math.min(tick - ascent, GolemConfig.JUMP_ANIMATION_TICKS - ascent)
                / (double) (GolemConfig.JUMP_ANIMATION_TICKS - ascent);
        return GolemConfig.JUMP_HEIGHT * (1.0D - t * t);
    }

    // ------------------------------------------------------------------ tick / animation state

    @Override
    public void tick() {
        super.tick();

        boolean grounded = onGround();
        if (grounded) {
            ticksSinceGrounded = 0;
            jumpUsed = false;
        } else {
            ticksSinceGrounded++;
        }
        if (jumpTicks >= 0) {
            if (!grounded) jumpWasAirborne = true;
            else if (jumpWasAirborne && !jumpLanded) {
                jumpLanded = true;
                if (!level().isClientSide) level().broadcastEntityEvent(this, EVENT_JUMP_LAND);
            }
            if (jumpLanded || jumpTicks >= GolemConfig.JUMP_ANIMATION_TICKS) {
                jumpTicks = -1;
                setNoGravity(false);
            } else {
                jumpTicks++;
            }
        }

        double dx = getX() - this.xo;
        double dz = getZ() - this.zo;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        smoothSpeed += (dist * 20.0F - smoothSpeed) * 0.25F;
        walkWeightO = walkWeight;
        walkWeight = Mth.clamp(smoothSpeed / 1.0F, 0.0F, 1.0F);
        walkPhaseO = walkPhase;
        walkPhase += dist / GolemConfig.WALK_STRIDE_BLOCKS * 2.6737F; // 2.6737 s = walk clip length

        if (smashTicks >= 0) {
            smashTicks++;
            Vec3 d = getDeltaMovement();
            setDeltaMovement(0.0D, d.y, 0.0D);
            if (!level().isClientSide && smashTicks == GolemConfig.SMASH_IMPACT_TICK) doImpact();
            if (smashTicks >= GolemConfig.SMASH_TOTAL_TICKS) {
                smashTicks = -1;
                smashCooldown = GolemConfig.SMASH_COOLDOWN_TICKS;
            }
        } else if (smashCooldown > 0) {
            smashCooldown--;
        }

        if (!level().isClientSide && isVehicle()) {
            setTarget(null);
            getNavigation().stop();
        }
    }

    public GolemModel.Pose computePose(float partial) {
        GolemModel m = GolemModel.get();
        GolemModel.Pose pose = m.createRestPose();
        if (smashTicks >= 0) {
            m.apply("ground_smash", (smashTicks + partial) / 20.0F, pose, 1.0F);
        } else if (jumpTicks >= 0) {
            m.apply("jump", (jumpTicks + partial) * 1.45F / GolemConfig.JUMP_ANIMATION_TICKS, pose, 1.0F);
            // Entity physics supplies the jump height; discard GLB root motion to avoid adding it twice.
            int pelvis = m.nodeIndex("Pelvis_Pivot");
            pose.t[pelvis * 3 + 1] = m.nodes[pelvis].t[1];
        } else {
            float w = Mth.lerp(partial, walkWeightO, walkWeight);
            if (w > 0.001F) m.apply("walk", (float) Mth.lerp((double) partial, walkPhaseO, walkPhase), pose, w);
        }
        return pose;
    }

    // ------------------------------------------------------------------ smash attack

    /** Used by AI melee and by the driver's attack key (via packet). Server only. */
    public void startSmash() {
        if (level().isClientSide || smashTicks >= 0 || smashCooldown > 0 || !isAlive()) return;
        smashTicks = 0;
        level().broadcastEntityEvent(this, EVENT_SMASH_START);
        playSound(SoundEvents.IRON_GOLEM_ATTACK, 2.0F, 0.6F);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        startSmash();
        return true;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_SMASH_START) {
            smashTicks = 0;
        } else if (id == EVENT_SMASH_IMPACT) {
            Vec3 c = getSmashCenter();
            ClientShake.trigger(c.x, c.y, c.z, level().getGameTime());
        } else if (id == EVENT_JUMP_START) {
            if (jumpTicks < 0) {
                jumpTicks = 0;
                jumpWasAirborne = false;
                jumpLanded = false;
                setNoGravity(true);
            }
        } else if (id == EVENT_JUMP_LAND) {
            jumpLanded = true;
        } else {
            super.handleEntityEvent(id);
        }
    }

    private Vec3 getSmashCenter() {
        return new Vec3(getX(), getY(), getZ());
    }

    private boolean canSmash(LivingEntity e) {
        if (e == this || hasPassenger(e) || e instanceof IronGolem || !e.isAlive()) return false;
        if (e instanceof Player p) return !isPlayerCreated() && !p.isCreative() && !p.isSpectator();
        if (e instanceof Enemy || e == getTarget()) return true;
        if (e instanceof AbstractVillager) return false;
        if (e instanceof TamableAnimal t && t.isTame()) return false;
        return getControllingPassenger() instanceof Player;
    }

    private void doImpact() {
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 c = getSmashCenter();
        BlockState ground = sl.getBlockState(BlockPos.containing(c.x, c.y - 0.5D, c.z));
        if (ground.isAir()) ground = Blocks.STONE.defaultBlockState();
        BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground);

        // Damage + knock-up
        double radius = GolemConfig.SMASH_RADIUS;
        AABB box = new AABB(c.x - radius, c.y - 3.0D, c.z - radius,
                c.x + radius, c.y + GolemConfig.HEIGHT, c.z + radius);
        List<LivingEntity> victims = sl.getEntitiesOfClass(LivingEntity.class, box, this::canSmash);
        for (LivingEntity e : victims) {
            AABB bounds = e.getBoundingBox();
            double dx = Mth.clamp(c.x, bounds.minX, bounds.maxX) - c.x;
            double dz = Mth.clamp(c.z, bounds.minZ, bounds.maxZ) - c.z;
            if (dx * dx + dz * dz > radius * radius) continue;
            e.hurt(damageSources().mobAttack(this), GolemConfig.SMASH_DAMAGE);
            Vec3 dir = new Vec3(e.getX() - c.x, 0.0D, e.getZ() - c.z);
            if (dir.lengthSqr() < 1.0E-6D) dir = Vec3.directionFromRotation(0.0F, getYRot());
            else dir = dir.normalize();
            e.push(dir.x * 2.0D, 0.8D, dir.z * 2.0D);
            e.hurtMarked = true;
        }

        // Particles (sent to every player tracking this area)
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5D, c.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        sl.sendParticles(debris, c.x, c.y + 0.3D, c.z, 160, 1.4D, 0.4D, 1.4D, 0.7D);
        sl.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.2D, c.z, 60, 2.0D, 0.2D, 2.0D, 0.12D);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.5D, c.z, 40, 1.5D, 0.5D, 1.5D, 0.05D);
        for (int ring = 1; ring <= 5; ring++) {
            double r = radius * ring / 5.0D;
            for (int i = 0; i < 32; i++) {
                double a = i * (Math.PI * 2.0D / 32.0D);
                double px = c.x + Math.cos(a) * r;
                double pz = c.z + Math.sin(a) * r;
                BlockPos floorPos = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(px, c.y, pz)).below();
                BlockState floor = sl.getBlockState(floorPos);
                if (floor.isAir()) continue;
                double py = floorPos.getY() + 1.05D;
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor), px, py, pz, 2, 0.2D, 0.08D, 0.2D, 0.08D);
                sl.sendParticles(ParticleTypes.POOF, px, py, pz, 1, 0.15D, 0.05D, 0.15D, 0.02D);
            }
        }

        // Sounds + screen shake
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.6F);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.0F, 0.5F);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 2.0F, 0.5F);
        sl.broadcastEntityEvent(this, EVENT_SMASH_IMPACT);
    }

    // ------------------------------------------------------------------ misc

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (hasSeats()) spawnAtLocation(new ItemStack(ModItems.SEATING_ARRANGEMENT.get()));
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (!s.isEmpty()) spawnAtLocation(s);
        }
        chest.clearContent();
    }
}
