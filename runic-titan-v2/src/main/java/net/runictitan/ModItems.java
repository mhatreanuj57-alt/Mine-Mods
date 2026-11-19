package net.runictitan;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RunicTitan.MOD_ID);

	/**
	 * Melee damage in the main hand: 1 (player) + 4 (netherite tier) + 80 = 85.
	 * Change the 80 to tune it.
	 */
	public static final RegistryObject<Item> EARTHSHAKER = ITEMS.register("earthshaker",
			() -> new EarthshakerItem(Tiers.NETHERITE, 80, -2.4f, new Item.Properties().fireResistant().stacksTo(1)));

	/** Helper item: the meteor animation renders its textured cubes through this item's models. */
	public static final RegistryObject<Item> VFX_CUBE = ITEMS.register("vfx_cube",
			() -> new Item(new Item.Properties()));

	private ModItems() {}
}
