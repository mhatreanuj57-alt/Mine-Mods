package dev.transport.truck;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.*;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;
import org.slf4j.Logger;

@Mod(TransportTruck.ID)
public final class TransportTruck {
  public static final String ID = "transport_truck";
  public static final Logger LOG = LogUtils.getLogger();

  public static ResourceLocation id(String name) {
    return new ResourceLocation(ID, name);
  }

  static final DeferredRegister<EntityType<?>> ENTITIES =
      DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
  static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
  static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
  static final DeferredRegister<SoundEvent> SOUNDS =
      DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ID);
  public static final RegistryObject<EntityType<TruckEntity>> TYPE =
      ENTITIES.register(
          "truck",
          () ->
              EntityType.Builder.<TruckEntity>of(TruckEntity::new, MobCategory.MISC)
                  .sized(2.4f, 3.4f)
                  .clientTrackingRange(12)
                  .updateInterval(2)
                  .build(ID + ":truck"));
  public static final RegistryObject<Item> ITEM =
      ITEMS.register(
          "truck",
          () ->
              new Item(new Item.Properties().stacksTo(1)) {
                @Override
                public InteractionResult useOn(UseOnContext c) {
                  var pos = c.getClickedPos().relative(c.getClickedFace());
                  if (c.getLevel().isClientSide) return InteractionResult.SUCCESS;
                  if (spawn(c.getLevel(), pos, c.getRotation()) == null)
                    return InteractionResult.FAIL;
                  if (c.getPlayer() == null || !c.getPlayer().getAbilities().instabuild)
                    c.getItemInHand().shrink(1);
                  return InteractionResult.CONSUME;
                }
              });

  static TruckEntity spawn(Level l, BlockPos p, float yaw) {
    TruckEntity t = TYPE.get().create(l);
    if (t == null) return null;
    t.moveTo(p.getX() + .5, p.getY(), p.getZ() + .5, yaw, 0);
    return l.noCollision(t) && l.addFreshEntity(t) ? t : null;
  }

  static final RegistryObject<CreativeModeTab> TAB =
      TABS.register(
          "vehicles",
          () ->
              CreativeModeTab.builder()
                  .title(Component.translatable("itemGroup.transport_truck"))
                  .icon(() -> new ItemStack(ITEM.get()))
                  .displayItems((p, o) -> o.accept(ITEM.get()))
                  .build());

  static RegistryObject<SoundEvent> sound(String n) {
    return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(id(n)));
  }

  public static final RegistryObject<SoundEvent> ENGINE = sound("engine"),
      HORN = sound("horn"),
      IMPACT = sound("impact"),
      DOOR = sound("door");
  static final ForgeConfigSpec.Builder CFG = new ForgeConfigSpec.Builder();
  static final ForgeConfigSpec.BooleanValue FUEL = CFG.define("fuel", false),
      DAMAGE = CFG.define("damage", true);
  static final ForgeConfigSpec.DoubleValue MAX = CFG.defineInRange("maxSpeed", 16d, 1d, 50d);

  public TransportTruck() {
    IEventBus b = FMLJavaModLoadingContext.get().getModEventBus();
    ENTITIES.register(b);
    ITEMS.register(b);
    TABS.register(b);
    SOUNDS.register(b);
    ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CFG.build(), "truck-common.toml");
    Network.init();
    MinecraftForge.EVENT_BUS.addListener(this::commands);
  }

  private void commands(RegisterCommandsEvent e) {
    e.getDispatcher()
        .register(
            Commands.literal("truck")
                .requires(s -> s.hasPermission(2))
                .then(
                    Commands.literal("spawn")
                        .executes(
                            c -> {
                              var s = c.getSource();
                              float yaw = s.getRotation().y;
                              var forward =
                                  net.minecraft.world.phys.Vec3.directionFromRotation(0, yaw);
                              if (spawn(
                                      s.getLevel(),
                                      BlockPos.containing(s.getPosition().add(forward.scale(5))),
                                      yaw)
                                  == null) {
                                s.sendFailure(Component.literal("Clear the space ahead of you."));
                                return 0;
                              }
                              s.sendSuccess(
                                  () -> Component.literal("Transport truck spawned."), false);
                              return 1;
                            })));
  }
}
