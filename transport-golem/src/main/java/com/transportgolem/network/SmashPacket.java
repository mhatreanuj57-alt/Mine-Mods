package com.transportgolem.network;

import com.transportgolem.entity.TransportGolemEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the driver pressed the attack key. */
public class SmashPacket {
    public SmashPacket() {}

    public static void encode(SmashPacket msg, FriendlyByteBuf buf) {}

    public static SmashPacket decode(FriendlyByteBuf buf) {
        return new SmashPacket();
    }

    public static void handle(SmashPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null && p.getVehicle() instanceof TransportGolemEntity g && g.getControllingPassenger() == p) {
                g.startSmash();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
