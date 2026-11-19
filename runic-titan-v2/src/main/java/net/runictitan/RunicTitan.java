package net.runictitan;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(RunicTitan.MOD_ID)
public class RunicTitan {
	public static final String MOD_ID = "runictitan";

	public RunicTitan() {
		IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
		ModItems.ITEMS.register(bus);
		ModEntities.ENTITIES.register(bus);
		bus.addListener(RunicTitan::addToCreativeTab);
		Scheduler.init();
	}

	private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.COMBAT) {
			event.accept(ModItems.EARTHSHAKER);
		}
	}
}
