package dev.missilesnukes.net;

import dev.missilesnukes.common.MissilesNukes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: one action from the launch remote's control panel. */
public record RemoteActionPayload(int action, int slot, int x, int y, int z) implements CustomPacketPayload {
    public static final int SELECT = 0, SET_TARGET = 1, LAUNCH = 2, LAUNCH_ALL = 3, UNLINK = 4, LINK_NEAREST = 5, TOGGLE_FX = 6;

    public static final Type<RemoteActionPayload> TYPE = new Type<>(MissilesNukes.id("remote_action"));

    public static final StreamCodec<ByteBuf, RemoteActionPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, RemoteActionPayload::action,
        ByteBufCodecs.VAR_INT, RemoteActionPayload::slot,
        ByteBufCodecs.INT, RemoteActionPayload::x,
        ByteBufCodecs.INT, RemoteActionPayload::y,
        ByteBufCodecs.INT, RemoteActionPayload::z,
        RemoteActionPayload::new);

    public static RemoteActionPayload simple(int action, int slot) { return new RemoteActionPayload(action, slot, 0, 0, 0); }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
