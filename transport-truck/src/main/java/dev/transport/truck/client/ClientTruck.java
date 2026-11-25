package dev.transport.truck.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.transport.truck.*;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.*;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

@Mod.EventBusSubscriber(
    modid = TransportTruck.ID,
    value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientTruck {
  static final Minecraft MC = Minecraft.getInstance();
  static final String[] NAMES = {
    "accelerate",
    "reverse",
    "left",
    "right",
    "brake",
    "horn",
    "cargo",
    "wings",
    "hood",
    "left_door",
    "right_door"
  };
  static final int[] DEFAULTS = {87, 83, 65, 68, 32, 72, 82, 71, 74, 75, 76};
  static final KeyMapping[] KEYS = new KeyMapping[NAMES.length];
  static int last = -1, id = -1;
  static TruckModel model;
  static boolean failed;
  static final Map<TruckEntity, Motor> motors = new WeakHashMap<>();
  static final IKeyConflictContext DRIVING =
      new IKeyConflictContext() {
        public boolean isActive() {
          return MC.screen == null
              && MC.player != null
              && MC.player.getVehicle() instanceof TruckEntity t
              && t.getControllingPassenger() == MC.player;
        }

        public boolean conflicts(IKeyConflictContext other) {
          return other == this;
        }
      };

  @SubscribeEvent
  public static void keys(RegisterKeyMappingsEvent e) {
    for (int i = 0; i < KEYS.length; i++) {
      KEYS[i] =
          new KeyMapping(
              "key.transport_truck." + NAMES[i],
              DRIVING,
              InputConstants.Type.KEYSYM,
              DEFAULTS[i],
              "category.transport_truck");
      e.register(KEYS[i]);
    }
  }

  @SubscribeEvent
  public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
    e.registerEntityRenderer(TransportTruck.TYPE.get(), Renderer::new);
  }

  @SubscribeEvent
  public static void reload(RegisterClientReloadListenersEvent e) {
    e.registerReloadListener(
        (ResourceManagerReloadListener)
            rm -> {
              if (model != null) model.close();
              model = null;
              failed = false;
            });
  }

  public static void receive(Network.State s) {
    if (MC.level == null || !(MC.level.getEntity(s.id()) instanceof TruckEntity t)) return;
    t.receive(s);
    if (t.isVehicle() && !motors.containsKey(t)) {
      Motor m = new Motor(t);
      motors.put(t, m);
      MC.getSoundManager().play(m);
    }
  }

  static SoundEvent sound(String name, SoundEvent custom, SoundEvent fallback) {
    return MC.getResourceManager()
            .getResource(TransportTruck.id("sounds/" + name + ".ogg"))
            .isPresent()
        ? custom
        : fallback;
  }

  public static void noise(Network.Noise n) {
    if (MC.level == null || !(MC.level.getEntity(n.id()) instanceof TruckEntity t)) return;
    SoundEvent s =
        switch (n.kind()) {
          case 0 ->
              sound("horn", TransportTruck.HORN.get(), SoundEvents.NOTE_BLOCK_DIDGERIDOO.value());
          case 1 -> sound("impact", TransportTruck.IMPACT.get(), SoundEvents.ANVIL_LAND);
          default -> sound("door", TransportTruck.DOOR.get(), SoundEvents.IRON_DOOR_OPEN);
        };
    MC.level.playLocalSound(
        t.getX(), t.getY() + 1, t.getZ(), s, SoundSource.NEUTRAL, .85f, 1, false);
  }

  static final class Motor extends AbstractTickableSoundInstance {
    final TruckEntity t;
    float prev;

    Motor(TruckEntity t) {
      super(
          ClientTruck.sound("engine", TransportTruck.ENGINE.get(), SoundEvents.MINECART_RIDING),
          SoundSource.NEUTRAL,
          t.level().random);
      this.t = t;
      looping = true;
      delay = 0;
      tick();
    }

    public void tick() {
      if (t.isRemoved() || !t.isVehicle() || MC.level != t.level()) {
        stop();
        motors.remove(t);
        return;
      }
      x = t.getX();
      y = t.getY() + 1;
      z = t.getZ();
      float s = Math.abs(t.speed);
      pitch = .68f + Math.min(1, s / 17) * .7f + (s > prev + .02f ? .1f : 0);
      volume = .24f + Math.min(.35f, s / 36);
      prev = s;
    }
  }

  @Mod.EventBusSubscriber(modid = TransportTruck.ID, value = Dist.CLIENT)
  public static class Gameplay {
    static final Method CAMERA_POS =
        ObfuscationReflectionHelper.findMethod(Camera.class, "m_90581_", Vec3.class);

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
      if (MC.player == null
          || !(MC.player.getVehicle() instanceof TruckEntity t)
          || !e.getCamera().isDetached()) return;
      var cam = e.getCamera();
      Vec3 start = MC.player.getEyePosition((float) e.getPartialTick()).add(0, .55, 0),
          dir = Vec3.directionFromRotation(cam.getXRot(), cam.getYRot()),
          end = start.subtract(dir.scale(9));
      var hit =
          t.level()
              .clip(
                  new ClipContext(
                      start, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, MC.player));
      try {
        CAMERA_POS.invoke(
            cam,
            hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().add(dir.scale(.25)));
      } catch (ReflectiveOperationException ex) {
        throw new IllegalStateException(ex);
      }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
      if (e.phase != TickEvent.Phase.END) return;
      if (MC.player == null
          || !(MC.player.getVehicle() instanceof TruckEntity t)
          || t.getControllingPassenger() != MC.player) {
        id = -1;
        return;
      }
      int bits = 0, actions = 0;
      if (DRIVING.isActive()) {
        for (int i = 0; i < 5; i++) if (KEYS[i].isDown()) bits |= 1 << i;
        for (int i = 5; i < KEYS.length; i++) if (KEYS[i].consumeClick()) actions |= 1 << (i - 5);
      }
      if (t.getId() != id || bits != last || actions != 0 || MC.player.tickCount % 5 == 0) {
        Network.input(bits, actions);
        id = t.getId();
        last = bits;
      }
    }
  }

  static final class Renderer extends EntityRenderer<TruckEntity> {
    Renderer(EntityRendererProvider.Context c) {
      super(c);
      shadowRadius = 1.8f;
    }

    public ResourceLocation getTextureLocation(TruckEntity t) {
      return TruckModel.MODEL_TEXTURE;
    }

    @Override
    public void render(
        TruckEntity t, float yaw, float a, PoseStack p, MultiBufferSource b, int light) {
      if (model == null && !failed)
        try {
          model = new TruckModel(MC.getResourceManager());
        } catch (Exception ex) {
          failed = true;
          TransportTruck.LOG.error("Cannot load truck GLB", ex);
        }
      if (model != null) {
        p.pushPose();
        p.mulPose(Axis.YP.rotationDegrees(-yaw));
        p.mulPose(Axis.XP.rotation(-Mth.lerp(a, t.previousPitch, t.pitch)));
        p.mulPose(Axis.ZP.rotation(Mth.lerp(a, t.previousRoll, t.roll)));
        model.render(t, a, p, b, light, Math.sqrt(entityRenderDispatcher.distanceToSqr(t)));
        p.popPose();
      }
      super.render(t, yaw, a, p, b, light);
    }
  }
}
