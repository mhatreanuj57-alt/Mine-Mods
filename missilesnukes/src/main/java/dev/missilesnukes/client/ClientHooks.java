package dev.missilesnukes.client;

import net.minecraft.client.Minecraft;

/** Entry points called from common code, only ever executed on the client. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openRemote() {
        Minecraft.getInstance().setScreen(new RemoteScreen());
    }
}
