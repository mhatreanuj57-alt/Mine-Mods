package com.transportgolem.client;

import com.transportgolem.TransportGolemMod;
import com.transportgolem.registry.ModEntities;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TransportGolemMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    public static final KeyMapping[] SEAT_KEYS = {
            new KeyMapping("key.transportgolem.seat_driver", KeyConflictContext.IN_GAME, KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_1, "key.categories.transportgolem"),
            new KeyMapping("key.transportgolem.seat_left", KeyConflictContext.IN_GAME, KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_2, "key.categories.transportgolem"),
            new KeyMapping("key.transportgolem.seat_right", KeyConflictContext.IN_GAME, KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_3, "key.categories.transportgolem")
    };

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : SEAT_KEYS) event.register(key);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TRANSPORT_GOLEM.get(), TransportGolemRenderer::new);
    }

    private ClientModEvents() {}
}
