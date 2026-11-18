package com.transportgolem;

import com.transportgolem.entity.TransportGolemEntity;
import com.transportgolem.network.ModNetwork;
import com.transportgolem.registry.ModEntities;
import com.transportgolem.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(TransportGolemMod.MODID)
public class TransportGolemMod {
    public static final String MODID = "transportgolem";

    public TransportGolemMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITIES.register(bus);
        ModItems.ITEMS.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(this::registerAttributes);
        bus.addListener(this::addToTabs);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.TRANSPORT_GOLEM.get(), TransportGolemEntity.createGolemAttributes().build());
    }

    private void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.SEATING_ARRANGEMENT);
        } else if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.TRANSPORT_GOLEM_SPAWN_EGG);
        }
    }
}
