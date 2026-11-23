package dev.missilesnukes.common;

import com.mojang.logging.LogUtils;
import dev.missilesnukes.net.Network;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Missiles & Nukes: Light ICBM missiles, physics-based flight, 8-slot launch remote. Requires Create. */
@Mod(MissilesNukes.MODID)
public class MissilesNukes {
    public static final String MODID = "missilesnukes";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public MissilesNukes(IEventBus modBus, ModContainer container) {
        ModComponents.COMPONENTS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModTabs.TABS.register(modBus);

        modBus.addListener(Network::register);
        modBus.addListener(ChunkLoading::register);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        NeoForge.EVENT_BUS.addListener(ImpactManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(ImpactManager::onServerStopping);
        NeoForge.EVENT_BUS.addListener(RemoteLogic::onPlayerTick);
    }
}
