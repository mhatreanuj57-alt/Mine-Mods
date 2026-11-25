package dev.transport.truck;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Network {
  static final SimpleChannel CHANNEL =
      NetworkRegistry.newSimpleChannel(
          TransportTruck.id("control"), () -> "1", "1"::equals, "1"::equals);

  public record Input(int keys, int actions) {}

  public record State(
      int id,
      double x,
      double y,
      double z,
      float yaw,
      float speed,
      float steer,
      float spin,
      float pitch,
      float roll,
      float[] wheels) {
    void write(FriendlyByteBuf b) {
      b.writeVarInt(id);
      b.writeDouble(x);
      b.writeDouble(y);
      b.writeDouble(z);
      for (float f : new float[] {yaw, speed, steer, spin, pitch, roll}) b.writeFloat(f);
      for (float f : wheels) b.writeFloat(f);
    }

    static State read(FriendlyByteBuf b) {
      return new State(
          b.readVarInt(),
          b.readDouble(),
          b.readDouble(),
          b.readDouble(),
          b.readFloat(),
          b.readFloat(),
          b.readFloat(),
          b.readFloat(),
          b.readFloat(),
          b.readFloat(),
          new float[] {b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat()});
    }
  }

  public record Noise(int id, int kind) {}

  static void init() {
    CHANNEL
        .messageBuilder(Input.class, 0, NetworkDirection.PLAY_TO_SERVER)
        .encoder(
            (p, b) -> {
              b.writeByte(p.keys);
              b.writeByte(p.actions);
            })
        .decoder(b -> new Input(b.readUnsignedByte(), b.readUnsignedByte()))
        .consumerMainThread(
            (p, c) -> {
              var player = c.get().getSender();
              if (player != null && player.getVehicle() instanceof TruckEntity t)
                t.controls(player, p.keys, p.actions);
            })
        .add();
    CHANNEL
        .messageBuilder(State.class, 1, NetworkDirection.PLAY_TO_CLIENT)
        .encoder(State::write)
        .decoder(State::read)
        .consumerMainThread(
            (p, c) ->
                DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT, () -> () -> dev.transport.truck.client.ClientTruck.receive(p)))
        .add();
    CHANNEL
        .messageBuilder(Noise.class, 2, NetworkDirection.PLAY_TO_CLIENT)
        .encoder(
            (p, b) -> {
              b.writeVarInt(p.id);
              b.writeByte(p.kind);
            })
        .decoder(b -> new Noise(b.readVarInt(), b.readUnsignedByte()))
        .consumerMainThread(
            (p, c) ->
                DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT, () -> () -> dev.transport.truck.client.ClientTruck.noise(p)))
        .add();
  }

  public static void input(int keys, int actions) {
    CHANNEL.sendToServer(new Input(keys, actions));
  }

  static void sync(TruckEntity t) {
    CHANNEL.send(
        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> t),
        new State(
            t.getId(),
            t.getX(),
            t.getY(),
            t.getZ(),
            t.getYRot(),
            t.speed,
            t.steer,
            t.spin,
            t.pitch,
            t.roll,
            t.compression));
  }

  static void sound(TruckEntity t, int kind) {
    CHANNEL.send(
        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> t), new Noise(t.getId(), kind));
  }
}
