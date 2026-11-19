package net.runictitan;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EarthshakerItem extends SwordItem {
	static final String[] NAMES = {"Ground Smash", "Meteor Strike", "Gravity Well", "Thunder Crash", "Earthquake", "Titanic Throw"};
	static final int THROW_INDEX = 5;

	/** Hold at least this many ticks to throw (20 ticks = 1 s). */
	static final int MIN_CHARGE = 8;
	/** Ticks of holding for a full-power throw. */
	static final int FULL_CHARGE = 40;
	/** Throw damage / speed range, min (just charged) to max (fully charged). */
	static final float MIN_DAMAGE = 25.0F, MAX_DAMAGE = 120.0F;
	static final float MIN_SPEED = 1.5F, MAX_SPEED = 4.0F;

	public EarthshakerItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
		super(tier, attackDamage, attackSpeed, properties);
	}

	private static int selected(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		return tag == null ? 0 : Math.floorMod(tag.getInt("Ability"), NAMES.length);
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return selected(stack) == THROW_INDEX ? UseAnim.SPEAR : UseAnim.NONE;
	}

	@Override
	public int getUseDuration(ItemStack stack) {
		return 72000;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		int sel = selected(stack);

		// Sneak + right click = switch technique
		if (player.isShiftKeyDown()) {
			sel = (sel + 1) % NAMES.length;
			stack.getOrCreateTag().putInt("Ability", sel);
			if (!level.isClientSide) {
				player.displayClientMessage(Component.literal("Technique: " + NAMES[sel]).withStyle(ChatFormatting.GOLD), true);
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}

		// Titanic Throw: hold right click to charge, release to throw
		if (sel == THROW_INDEX) {
			player.startUsingItem(hand);
			return InteractionResultHolder.consume(stack);
		}

		if (level.isClientSide) return InteractionResultHolder.success(stack);

		ServerLevel sl = (ServerLevel) level;
		switch (sel) {
			case 0 -> Abilities.groundSmash(sl, player);
			case 1 -> Abilities.meteorStrike(sl, player);
			case 2 -> Abilities.gravityWell(sl, player);
			case 3 -> Abilities.thunderCrash(sl, player);
			default -> Abilities.earthquake(sl, player);
		}
		player.displayClientMessage(Component.literal(NAMES[sel]).withStyle(ChatFormatting.GOLD), true);
		return InteractionResultHolder.success(stack);
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
		if (selected(stack) != THROW_INDEX) return;
		int charge = getUseDuration(stack) - remaining;
		if (charge == FULL_CHARGE && !level.isClientSide) {
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
					SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 1.8F);
		}
		if (level.isClientSide && charge >= MIN_CHARGE && charge % 2 == 0) {
			double x = entity.getX() + (level.random.nextDouble() - 0.5) * 0.8;
			double y = entity.getY() + 1.0 + level.random.nextDouble() * 0.8;
			double z = entity.getZ() + (level.random.nextDouble() - 0.5) * 0.8;
			level.addParticle(charge >= FULL_CHARGE ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SMOKE, x, y, z, 0, 0.02, 0);
		}
	}

	@Override
	public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
		if (!(entity instanceof Player player) || selected(stack) != THROW_INDEX) return;
		int charge = getUseDuration(stack) - remaining;
		if (charge < MIN_CHARGE) return;

		float power = Mth.clamp((float) charge / FULL_CHARGE, 0.0F, 1.0F);
		float speed = Mth.lerp(power, MIN_SPEED, MAX_SPEED);
		float damage = Mth.lerp(power, MIN_DAMAGE, MAX_DAMAGE);

		if (!level.isClientSide) {
			ThrownHammer hammer = new ThrownHammer(level, player, stack, damage);
			hammer.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, speed, 1.0F);
			if (player.getAbilities().instabuild) {
				hammer.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
			}
			level.addFreshEntity(hammer);
			level.playSound(null, hammer, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.2F, 0.6F + 0.4F * (1.0F - power));
			level.playSound(null, hammer, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.8F);
			if (!player.getAbilities().instabuild) {
				player.getInventory().removeItem(stack);
			}
		}
		player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
	}

	/** Lets the Loyalty book be applied (anvil / enchanting table) so the thrown hammer flies back. */
	@Override
	public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
		if (enchantment == Enchantments.LOYALTY) return true;
		return super.canApplyAtEnchantingTable(stack, enchantment);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.literal("Technique: " + NAMES[selected(stack)]).withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.literal("Right-click: cast (Titanic Throw: hold, release to throw)").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.literal("Sneak + right-click: change technique").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.literal("Loyalty: the thrown hammer returns to you").withStyle(ChatFormatting.DARK_GRAY));
	}
}
