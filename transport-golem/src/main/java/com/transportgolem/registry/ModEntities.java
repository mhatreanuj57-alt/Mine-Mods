package com.transportgolem.registry;

import com.transportgolem.GolemConfig;
import com.transportgolem.TransportGolemMod;
import com.transportgolem.entity.TransportGolemEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, TransportGolemMod.MODID);

    public static final RegistryObject<EntityType<TransportGolemEntity>> TRANSPORT_GOLEM = ENTITIES.register("transport_golem",
            () -> EntityType.Builder.<TransportGolemEntity>of(TransportGolemEntity::new, MobCategory.MISC)
                    .sized(GolemConfig.WIDTH, GolemConfig.HEIGHT)
                    .clientTrackingRange(12)
                    .build("transport_golem"));

    private ModEntities() {}
}
