package com.transportgolem.client;

import com.transportgolem.TransportGolemMod;
import com.transportgolem.entity.TransportGolemEntity;
import com.transportgolem.network.ModNetwork;
import com.transportgolem.network.JumpPacket;
import com.transportgolem.network.SmashPacket;
import com.transportgolem.network.SwitchSeatPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = TransportGolemMod.MODID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        for (int seat = 0; seat < ClientModEvents.SEAT_KEYS.length; seat++) {
            if (ClientModEvents.SEAT_KEYS[seat].consumeClick()) {
                if (mc.player != null && mc.screen == null && mc.player.getVehicle() instanceof TransportGolemEntity) {
                    ModNetwork.CHANNEL.sendToServer(new SwitchSeatPacket(seat));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onJumpInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getAction() != GLFW.GLFW_PRESS || mc.player == null || mc.screen != null
                || !mc.options.keyJump.matches(event.getKey(), event.getScanCode())
                || !(mc.player.getVehicle() instanceof TransportGolemEntity golem)
                || golem.getControllingPassenger() != mc.player) return;
        golem.jumpFromRider();
        ModNetwork.CHANNEL.sendToServer(new JumpPacket());
    }

    /** Driver's attack key (left click) = smash. */
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && event.isAttack() && p.getVehicle() instanceof TransportGolemEntity g
                && g.getControllingPassenger() == p) {
            ModNetwork.CHANNEL.sendToServer(new SmashPacket());
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float age = (mc.level.getGameTime() - ClientShake.startTick) + (float) event.getPartialTick();
        if (age < 0.0F || age > 20.0F) return;
        double dist = event.getCamera().getPosition().distanceTo(new Vec3(ClientShake.x, ClientShake.y, ClientShake.z));
        float amp = (float) Math.max(0.0D, 1.0D - dist / 40.0D) * (1.0F - age / 20.0F) * 3.0F;
        if (amp <= 0.0F) return;
        event.setPitch(event.getPitch() + (float) Math.sin(age * 2.7F) * amp);
        event.setRoll(event.getRoll() + (float) Math.cos(age * 2.1F) * amp * 0.6F);
    }

    private ClientForgeEvents() {}
}
