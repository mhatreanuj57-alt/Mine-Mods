package dev.missilesnukes.client;

import dev.missilesnukes.common.ModEntities;
import dev.missilesnukes.common.ModItems;
import dev.missilesnukes.common.MissilesNukes;
import dev.missilesnukes.sim.MissileSpec;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Client-only bootstrap: renderers, item renderer, HUD layer and model cache invalidation. */
@Mod(value = MissilesNukes.MODID, dist = Dist.CLIENT)
public class MissilesNukesClient {
    public MissilesNukesClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(this::registerRenderers);
        modBus.addListener(this::registerReloadListeners);
        modBus.addListener(this::registerItemExtensions);
        modBus.addListener(this::registerGuiLayers);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MISSILE.get(), ctx -> new MissileRenderer(ctx, MissileSpec.LIGHT));
        event.registerEntityRenderer(ModEntities.HEAVY_MISSILE.get(), ctx -> new MissileRenderer(ctx, MissileSpec.HEAVY));
        event.registerEntityRenderer(ModEntities.PART.get(), MissilePartRenderer::new);
        event.registerEntityRenderer(ModEntities.EXPLOSION_FX.get(), ExplosionFxRenderer::new);
    }

    private void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> GlbModels.clear());
    }

    private void registerItemExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions ext = new IClientItemExtensions() {
            private GlbItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new GlbItemRenderer();
                return renderer;
            }
        };
        event.registerItem(ext, ModItems.CENTRAL_BODY.get(), ModItems.ENGINE_MOUNT.get(), ModItems.WARHEAD.get(),
            ModItems.COMPLETE_MISSILE.get(), ModItems.LAUNCH_REMOTE.get(),
            ModItems.HEAVY_CENTRAL_BODY.get(), ModItems.HEAVY_ENGINE_MOUNT.get(), ModItems.HEAVY_WARHEAD.get(), ModItems.HEAVY_MISSILE.get());
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(MissilesNukes.id("remote_hud"), new RemoteHud());
    }
}
