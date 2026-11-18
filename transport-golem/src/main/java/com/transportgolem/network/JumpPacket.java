package com.transportgolem.network;

import com.transportgolem.entity.TransportGolemEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: the driver requested a jump. */
public class JumpPacket {
    public static void encode(JumpPacket msg, FriendlyByteBuf buf) {}

    public static JumpPacket decode(FriendlyByteBuf buf) { return new JumpPacket(); }

    public static void handle(JumpPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.getVehicle() instanceof TransportGolemEntity golem
                    && golem.getControllingPassenger() == player) {
                golem.jumpFromRider();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
