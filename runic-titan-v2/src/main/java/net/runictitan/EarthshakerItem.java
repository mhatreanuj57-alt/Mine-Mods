package net.runictitan;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

public class EarthshakerItem extends SwordItem {
	static final String[] NAMES = {"Ground Smash", "Meteor Strike", "Gravity Well", "Thunder Crash", "Earthquake"};
	public EarthshakerItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
		super(material, attackDamage, attackSpeed, settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		NbtCompound nbt = stack.getOrCreateNbt();
		int selected = Math.floorMod(nbt.getInt("Ability"), NAMES.length);

		// Sneak + right click = switch technique
		if (player.isSneaking()) {
			selected = (selected + 1) % NAMES.length;
			nbt.putInt("Ability", selected);
			if (!world.isClient) {
				player.sendMessage(Text.literal("Technique: " + NAMES[selected]).formatted(Formatting.GOLD), true);
			}
			return TypedActionResult.success(stack, world.isClient);
		}

		if (world.isClient) return TypedActionResult.success(stack, true);

		ServerWorld sw = (ServerWorld) world;
		switch (selected) {
			case 0 -> Abilities.groundSmash(sw, player);
			case 1 -> Abilities.meteorStrike(sw, player);
			case 2 -> Abilities.gravityWell(sw, player);
			case 3 -> Abilities.thunderCrash(sw, player);
			default -> Abilities.earthquake(sw, player);
		}
		player.sendMessage(Text.literal(NAMES[selected]).formatted(Formatting.GOLD), true);
		return TypedActionResult.success(stack, false);
	}

	@Override
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
		int selected = Math.floorMod(stack.hasNbt() ? stack.getNbt().getInt("Ability") : 0, NAMES.length);
		tooltip.add(Text.literal("Technique: " + NAMES[selected]).formatted(Formatting.GOLD));
		tooltip.add(Text.literal("Right-click: cast").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("Sneak + right-click: change technique").formatted(Formatting.GRAY));
	}
}
