package com.transportgolem.network;

import com.transportgolem.TransportGolemMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TransportGolemMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, SmashPacket.class, SmashPacket::encode, SmashPacket::decode, SmashPacket::handle);
        CHANNEL.registerMessage(1, SwitchSeatPacket.class, SwitchSeatPacket::encode, SwitchSeatPacket::decode, SwitchSeatPacket::handle);
        CHANNEL.registerMessage(2, JumpPacket.class, JumpPacket::encode, JumpPacket::decode, JumpPacket::handle);
    }

    private ModNetwork() {}
}
