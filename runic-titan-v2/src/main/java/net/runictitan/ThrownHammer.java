package net.runictitan;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The thrown Earthshaker (behaves like a trident: Loyalty makes it fly back to the thrower).
 */
public class ThrownHammer extends AbstractArrow {
	private static final EntityDataAccessor<Byte> ID_LOYALTY =
			SynchedEntityData.defineId(ThrownHammer.class, EntityDataSerializers.BYTE);

	private ItemStack hammerItem = new ItemStack(ModItems.EARTHSHAKER.get());
	private boolean dealtDamage;
	private float throwDamage = 40.0F;
	private int blockHitTicks;
	public int returnTickCount;

	public ThrownHammer(EntityType<? extends ThrownHammer> type, Level level) {
		super(type, level);
	}

	public ThrownHammer(Level level, LivingEntity owner, ItemStack stack, float damage) {
		super(ModEntities.THROWN_HAMMER.get(), owner, level);
		this.hammerItem = stack.copy();
		this.throwDamage = damage;
		this.entityData.set(ID_LOYALTY, (byte) EnchantmentHelper.getLoyalty(stack));
	}

	@Override
	protected void defineSynchedData() {
		super.defineSynchedData();
		this.entityData.define(ID_LOYALTY, (byte) 0);
	}

	/** True once the hammer has stuck into a block and is not flying back. */
	public boolean isStuck() {
		return blockHitTicks > 0 && !isNoPhysics();
	}

	@Override
	public void tick() {
		if (blockHitTicks > 0) blockHitTicks++;
		if (blockHitTicks > 4) dealtDamage = true;

		Entity owner = getOwner();
		int loyalty = this.entityData.get(ID_LOYALTY);
		if (loyalty > 0 && (dealtDamage || isNoPhysics()) && owner != null) {
			if (!isAcceptableReturnOwner()) {
				if (!level().isClientSide && this.pickup == AbstractArrow.Pickup.ALLOWED) {
					spawnAtLocation(getPickupItem(), 0.1F);
				}
				discard();
			} else {
				setNoPhysics(true);
				Vec3 toOwner = owner.getEyePosition().subtract(position());
				setPosRaw(getX(), getY() + toOwner.y * 0.015D * loyalty, getZ());
				if (level().isClientSide) this.yOld = getY();
				double pull = 0.06D * loyalty;
				setDeltaMovement(getDeltaMovement().scale(0.95D).add(toOwner.normalize().scale(pull)));
				if (returnTickCount == 0) playSound(SoundEvents.TRIDENT_RETURN, 10.0F, 1.0F);
				returnTickCount++;
			}
		}

		if (level().isClientSide && getDeltaMovement().lengthSqr() > 0.05D) {
			level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 0, 0, 0);
			level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 0, 0.02, 0);
		}
		super.tick();
	}

	private boolean isAcceptableReturnOwner() {
		Entity owner = getOwner();
		if (owner == null || !owner.isAlive()) return false;
		return !(owner instanceof ServerPlayer) || !owner.isSpectator();
	}

	@Override
	protected ItemStack getPickupItem() {
		return hammerItem.copy();
	}

	@Override
	protected EntityHitResult findHitEntity(Vec3 start, Vec3 end) {
		return dealtDamage ? null : super.findHitEntity(start, end);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		Entity target = result.getEntity();
		Entity owner = getOwner();
		DamageSource source = damageSources().thrown(this, owner == null ? this : owner);
		dealtDamage = true;

		if (target.hurt(source, throwDamage)) {
			if (target.getType() == EntityType.ENDERMAN) return;
			if (target instanceof LivingEntity living) {
				if (owner instanceof LivingEntity lo) {
					EnchantmentHelper.doPostHurtEffects(living, lo);
					EnchantmentHelper.doPostDamageEffects(lo, living);
				}
				doPostHurtEffects(living);
			}
		}
		setDeltaMovement(getDeltaMovement().multiply(-0.01D, -0.1D, -0.01D));
		impact(target.position().add(0, target.getBbHeight() / 2.0, 0), target);
		playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		if (blockHitTicks == 0) {
			blockHitTicks = 1;
			impact(result.getLocation(), null);
		}
	}

	/** Shockwave when the hammer lands: particles, sound and splash damage around the impact. */
	private void impact(Vec3 at, Entity primary) {
		if (!(level() instanceof ServerLevel sl)) return;
		float power = Math.min(1.0F, throwDamage / 120.0F);
		Abilities.fx(sl, ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		Abilities.fx(sl, ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 20 + (int) (40 * power), 0.8, 0.5, 0.8, 0.05);
		Abilities.fx(sl, ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 30 + (int) (60 * power), 0.8, 0.8, 0.8, 0.4);
		sl.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.PLAYERS, 1.5F, 0.6F);
		sl.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F + power, 1.2F);

		Entity owner = getOwner();
		double radius = 2.5 + 2.5 * power;
		DamageSource source = damageSources().thrown(this, owner == null ? this : owner);
		for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class,
				new net.minecraft.world.phys.AABB(at.x - radius, at.y - radius, at.z - radius, at.x + radius, at.y + radius, at.z + radius),
				t -> t != owner && t != primary && t.isAlive() && !(t instanceof ArmorStand)
						&& t.distanceToSqr(at) <= radius * radius)) {
			e.hurt(source, throwDamage * 0.35F);
			Vec3 away = e.position().subtract(at);
			away = new Vec3(away.x, 0, away.z);
			if (away.lengthSqr() > 1.0E-4) {
				Vec3 push = away.normalize().scale(0.9);
				e.push(push.x, 0.5, push.z);
				e.hurtMarked = true;
			}
		}
	}

	@Override
	protected boolean tryPickup(Player player) {
		return super.tryPickup(player) || (isNoPhysics() && ownedBy(player) && player.getInventory().add(getPickupItem()));
	}

	@Override
	protected SoundEvent getDefaultHitGroundSoundEvent() {
		return SoundEvents.TRIDENT_HIT_GROUND;
	}

	@Override
	public void playerTouch(Player player) {
		if (ownedBy(player) || getOwner() == null) {
			super.playerTouch(player);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("Hammer", 10)) hammerItem = ItemStack.of(tag.getCompound("Hammer"));
		dealtDamage = tag.getBoolean("DealtDamage");
		if (tag.contains("ThrowDamage")) throwDamage = tag.getFloat("ThrowDamage");
		this.entityData.set(ID_LOYALTY, (byte) EnchantmentHelper.getLoyalty(hammerItem));
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.put("Hammer", hammerItem.save(new CompoundTag()));
		tag.putBoolean("DealtDamage", dealtDamage);
		tag.putFloat("ThrowDamage", throwDamage);
	}

	@Override
	public void tickDespawn() {
		int loyalty = this.entityData.get(ID_LOYALTY);
		if (this.pickup != AbstractArrow.Pickup.ALLOWED || loyalty <= 0) {
			super.tickDespawn();
		}
	}

	@Override
	protected float getWaterInertia() {
		return 0.99F;
	}

	@Override
	public boolean shouldRender(double x, double y, double z) {
		return true;
	}
}
