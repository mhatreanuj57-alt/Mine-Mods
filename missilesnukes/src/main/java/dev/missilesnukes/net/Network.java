package dev.missilesnukes.net;

import dev.missilesnukes.common.RemoteLogic;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Network {
    private Network() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(RemoteActionPayload.TYPE, RemoteActionPayload.CODEC, Network::onRemoteAction);
    }

    private static void onRemoteAction(RemoteActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) RemoteLogic.handle(player, payload);
    }
}
