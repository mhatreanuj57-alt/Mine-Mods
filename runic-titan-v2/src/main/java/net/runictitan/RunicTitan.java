package net.runictitan;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class RunicTitan implements ModInitializer {
	public static final String MOD_ID = "runictitan";
	public static Item EARTHSHAKER;

	@Override
	public void onInitialize() {
		EARTHSHAKER = Registry.register(Registries.ITEM, new Identifier(MOD_ID, "earthshaker"),
				new EarthshakerItem(ToolMaterials.NETHERITE, 7, -2.4f, new FabricItemSettings().fireproof().maxCount(1)));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(EARTHSHAKER));
		Scheduler.init();
	}
}
