package com.transportgolem.network;

import com.transportgolem.entity.TransportGolemEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: move the sender to a requested golem seat. */
public class SwitchSeatPacket {
    private final int seatIndex;

    public SwitchSeatPacket(int seatIndex) { this.seatIndex = seatIndex; }

    public static void encode(SwitchSeatPacket msg, FriendlyByteBuf buf) { buf.writeVarInt(msg.seatIndex); }

    public static SwitchSeatPacket decode(FriendlyByteBuf buf) { return new SwitchSeatPacket(buf.readVarInt()); }

    public static void handle(SwitchSeatPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.getVehicle() instanceof TransportGolemEntity golem) {
                golem.switchSeat(player, msg.seatIndex);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
