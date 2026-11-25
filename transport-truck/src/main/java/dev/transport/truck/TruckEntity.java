package dev.transport.truck;

import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.*;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;

public class TruckEntity extends Entity {
  private static final EntityDataAccessor<Float> HP =
      SynchedEntityData.defineId(TruckEntity.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Integer> PANELS =
      SynchedEntityData.defineId(TruckEntity.class, EntityDataSerializers.INT);
  public float speed,
      steer,
      spin,
      pitch,
      roll,
      previousSteer,
      previousSpin,
      previousPitch,
      previousRoll;
  public final float[] compression = new float[4],
      oldCompression = new float[4],
      panels = new float[4],
      oldPanels = new float[4];
  private final float[] velocity = new float[4];
  private int keys, lastInput, lastHorn = -30, fuel, steps;
  private float load;
  private Network.State target;
  public final SimpleContainer cargo =
      new SimpleContainer(54) {
        @Override
        public boolean stillValid(Player p) {
          return TruckEntity.this.isAlive() && TruckEntity.this.distanceToSqr(p) < 100;
        }
      };

  public TruckEntity(EntityType<? extends TruckEntity> type, Level level) {
    super(type, level);
    setMaxUpStep(1.05f);
    blocksBuilding = true;
    cargo.addListener(
        c -> {
          float sum = 0;
          for (int i = 0; i < c.getContainerSize(); i++) {
            var s = c.getItem(i);
            if (!s.isEmpty()) sum += (float) s.getCount() / s.getMaxStackSize();
          }
          load = sum / 54;
        });
  }

  @Override
  protected void defineSynchedData() {
    entityData.define(HP, 100f);
    entityData.define(PANELS, 0);
  }

  @Override
  public Packet<ClientGamePacketListener> getAddEntityPacket() {
    return NetworkHooks.getEntitySpawningPacket(this);
  }

  @Override
  public boolean isPickable() {
    return !isRemoved();
  }

  @Override
  public boolean canBeCollidedWith() {
    return !isRemoved();
  }

  @Override
  public boolean isControlledByLocalInstance() {
    return !level().isClientSide;
  }

  @Override
  protected boolean canAddPassenger(Entity e) {
    return getPassengers().size() < 2;
  }

  @Override
  public LivingEntity getControllingPassenger() {
    return getFirstPassenger() instanceof LivingEntity l ? l : null;
  }

  @Override
  public AABB getBoundingBoxForCulling() {
    return getBoundingBox().inflate(3, 1, 3);
  }

  public Vec3 local(double x, double y, double z) {
    float a = getYRot() * Mth.DEG_TO_RAD;
    return position().add(x * Mth.cos(a) - z * Mth.sin(a), y, x * Mth.sin(a) + z * Mth.cos(a));
  }

  @Override
  protected void positionRider(Entity p, MoveFunction mover) {
    if (hasPassenger(p)) {
      Vec3 v = local(getFirstPassenger() == p ? .57 : -.57, 1.52, 1.18);
      mover.accept(p, v.x, v.y, v.z);
    }
  }

  @Override
  public Vec3 getDismountLocationForPassenger(LivingEntity p) {
    for (double x :
        new double[] {getFirstPassenger() == p ? 2 : -2, getFirstPassenger() == p ? -2 : 2})
      for (int dy = 0; dy <= 2; dy++) {
        Vec3 v = local(x, dy, 1.1);
        if (level().noCollision(p, p.getDimensions(p.getPose()).makeBoundingBox(v))) return v;
      }
    return super.getDismountLocationForPassenger(p);
  }

  void controls(ServerPlayer p, int k, int actions) {
    if (getControllingPassenger() != p) return;
    keys = k & 31;
    lastInput = tickCount;
    if ((actions & 1) != 0 && tickCount - lastHorn >= 12) {
      lastHorn = tickCount;
      Network.sound(this, 0);
    }
    if ((actions & 2) != 0) openCargo(p);
    int toggles = (actions >> 2) & 15;
    if (Math.abs(speed) > .6) toggles &= 1;
    if (toggles != 0) {
      entityData.set(PANELS, entityData.get(PANELS) ^ toggles);
      Network.sound(this, 2);
    }
  }

  public void openCargo(Player p) {
    if (p instanceof ServerPlayer sp)
      sp.openMenu(
          new SimpleMenuProvider(
              (id, inv, user) -> ChestMenu.sixRows(id, inv, cargo),
              Component.translatable("container.transport_truck.cargo")));
  }

  @Override
  public InteractionResult interact(Player p, InteractionHand hand) {
    ItemStack s = p.getItemInHand(hand);
    if (level().isClientSide) return InteractionResult.SUCCESS;
    if (p.isSecondaryUseActive()) {
      openCargo(p);
      return InteractionResult.CONSUME;
    }
    if (s.is(Items.IRON_INGOT) && entityData.get(HP) < 100) {
      entityData.set(HP, Math.min(100, entityData.get(HP) + 20));
    } else if (TransportTruck.FUEL.get() && (s.is(Items.COAL) || s.is(Items.CHARCOAL))) {
      fuel = Math.min(72000, fuel + 6000);
    } else return p.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
    if (!p.getAbilities().instabuild) s.shrink(1);
    return InteractionResult.CONSUME;
  }

  @Override
  protected void addAdditionalSaveData(CompoundTag n) {
    n.putFloat("Health", entityData.get(HP));
    n.putInt("Panels", entityData.get(PANELS));
    n.putInt("Fuel", fuel);
    ListTag items = new ListTag();
    for (int i = 0; i < 54; i++)
      if (!cargo.getItem(i).isEmpty()) {
        var tag = new CompoundTag();
        tag.putInt("Slot", i);
        cargo.getItem(i).save(tag);
        items.add(tag);
      }
    n.put("Cargo", items);
  }

  @Override
  protected void readAdditionalSaveData(CompoundTag n) {
    if (n.contains("Health")) entityData.set(HP, n.getFloat("Health"));
    entityData.set(PANELS, n.getInt("Panels"));
    fuel = n.getInt("Fuel");
    cargo.clearContent();
    var items = n.getList("Cargo", Tag.TAG_COMPOUND);
    for (int i = 0; i < items.size(); i++) {
      var s = items.getCompound(i);
      int k = s.getInt("Slot");
      if (k >= 0 && k < 54) cargo.setItem(k, ItemStack.of(s));
    }
  }

  @Override
  public boolean hurt(DamageSource src, float damage) {
    if (level().isClientSide || isRemoved()) return false;
    boolean pickup =
        src.getDirectEntity() instanceof Player p && p.getMainHandItem().is(ItemTags.PICKAXES);
    if (!pickup && (!TransportTruck.DAMAGE.get() || isInvulnerableTo(src))) return false;
    entityData.set(HP, Math.max(0, entityData.get(HP) - damage));
    if (pickup || entityData.get(HP) <= 0) {
      Containers.dropContents(level(), this, cargo);
      if (pickup) spawnAtLocation(TransportTruck.ITEM.get());
      discard();
    }
    return true;
  }

  @Override
  public boolean causeFallDamage(float dist, float scalar, DamageSource src) {
    return dist > 3 && hurt(src, (dist - 3) * 4 * scalar);
  }

  @Override
  public void lerpTo(double x, double y, double z, float yaw, float xr, int n, boolean teleport) {
    if (target == null || teleport) super.lerpTo(x, y, z, yaw, xr, n, teleport);
  }

  public void receive(Network.State s) {
    target = s;
    steps = 2;
  }

  private static float approach(float a, float b, float d) {
    return a + Mth.clamp(b - a, -d, d);
  }

  private boolean blocked(double distance) {
    for (double x : new double[] {-1.12, 0, 1.12}) {
      Vec3 start = local(x, 1.055, 0), end = local(x, 1.055, distance);
      if (level()
              .clip(
                  new ClipContext(
                      start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
              .getType()
          != HitResult.Type.MISS) return true;
    }
    return false;
  }

  @Override
  public void tick() {
    super.tick();
    previousSteer = steer;
    previousSpin = spin;
    previousPitch = pitch;
    previousRoll = roll;
    System.arraycopy(compression, 0, oldCompression, 0, 4);
    for (int i = 0; i < 4; i++) {
      oldPanels[i] = panels[i];
      panels[i] = approach(panels[i], (entityData.get(PANELS) >> i) & 1, .08f);
    }
    if (level().isClientSide) {
      if (target != null && steps > 0) {
        float a = 1f / steps--;
        setPos(
            Mth.lerp(a, getX(), target.x()),
            Mth.lerp(a, getY(), target.y()),
            Mth.lerp(a, getZ(), target.z()));
        setYRot(getYRot() + a * Mth.wrapDegrees(target.yaw() - getYRot()));
        speed = Mth.lerp(a, speed, target.speed());
        steer = Mth.lerp(a, steer, target.steer());
        spin += a * Mth.wrapDegrees(target.spin() - spin);
        pitch = Mth.lerp(a, pitch, target.pitch());
        roll = Mth.lerp(a, roll, target.roll());
        for (int i = 0; i < 4; i++)
          compression[i] = Mth.lerp(a, compression[i], target.wheels()[i]);
      }
      return;
    }
    if (getControllingPassenger() == null || tickCount - lastInput > 12) keys = 0;
    boolean w = (keys & 1) != 0,
        s = (keys & 2) != 0,
        wet = level().getFluidState(blockPosition()).is(FluidTags.WATER),
        deep = level().getFluidState(blockPosition().above()).is(FluidTags.WATER);
    float max = TransportTruck.MAX.get().floatValue() * (wet ? .42f : 1);
    if ((keys & 16) != 0 || deep) speed = approach(speed, 0, 1.5f);
    else if (w != s && (!TransportTruck.FUEL.get() || fuel > 0)) {
      speed =
          approach(
              speed,
              w ? max : -Math.min(6, max),
              w && speed < 0 || s && speed > 0 ? .65f : .21f / (1 + .38f * load));
      if (TransportTruck.FUEL.get()) fuel--;
    } else speed = approach(speed, 0, .064f + Math.abs(speed) * .008f);
    float demand =
        (((keys & 4) != 0 ? 1 : 0) - ((keys & 8) != 0 ? 1 : 0)) / (1 + Math.abs(speed) * .048f);
    steer = approach(steer, demand, .11f);
    setYRot(
        getYRot()
            - (float) Math.toDegrees(speed * .05 * Math.tan(Math.toRadians(31.5 * steer)) / 4.2));
    double z = getZ(), x = getX(), vy = Math.max(-3.4, getDeltaMovement().y - .08);
    float original = speed;
    if (speed != 0 && blocked(Math.copySign(3.05 + Math.abs(speed) * .05, speed))) speed = 0;
    float a = getYRot() * Mth.DEG_TO_RAD;
    move(MoverType.SELF, new Vec3(-Mth.sin(a) * speed * .05, vy, Mth.cos(a) * speed * .05));
    setDeltaMovement(0, verticalCollision ? 0 : vy, 0);
    if (horizontalCollision) speed = 0;
    if (speed == 0 && Math.abs(original) > 4) {
      Network.sound(this, 1);
      hurt(damageSources().generic(), (Math.abs(original) - 4) * 1.4f);
    }
    spin =
        (spin
                + (float)
                    (Math.copySign(Math.hypot(getX() - x, getZ() - z), original)
                        / .65
                        * Mth.RAD_TO_DEG))
            % 360;
    float[] h = new float[4];
    for (int i = 0; i < 4; i++) {
      Vec3 v = local(i % 2 == 0 ? 1.1 : -1.1, 0, i < 2 ? 2.1 : -2.1);
      var hit =
          level()
              .clip(
                  new ClipContext(
                      v.add(0, 1.55, 0),
                      v.add(0, -2.1, 0),
                      ClipContext.Block.COLLIDER,
                      ClipContext.Fluid.NONE,
                      this));
      h[i] = (float) (hit.getLocation().y - getY());
      velocity[i] = (velocity[i] + (Mth.clamp(h[i], -.15f, .15f) - compression[i]) * .32f) * .62f;
      compression[i] = Mth.clamp(compression[i] + velocity[i], -.15f, .15f);
    }
    pitch = Mth.lerp(.24f, pitch, (float) Math.atan2((h[0] + h[1] - h[2] - h[3]) * .5, 4.2));
    roll = Mth.lerp(.24f, roll, (float) Math.atan2((h[0] + h[2] - h[1] - h[3]) * .5, 2.2));
    if ((tickCount & 1) == 0) Network.sync(this);
  }
}
