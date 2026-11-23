package dev.missilesnukes.common;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MissilesNukes.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> MISSILE = ENTITIES.register("icbm_missile",
        () -> EntityType.Builder.<MissileEntity>of(MissileEntity::new, MobCategory.MISC)
            .sized(0.7f, 4.0f).clientTrackingRange(64).updateInterval(1).fireImmune()
            .build(MissilesNukes.MODID + ":icbm_missile"));

    /** The Heavy ICBM is the same entity class with its own, taller hitbox and its own MissileSpec. */
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> HEAVY_MISSILE = ENTITIES.register("icbm_heavy_missile",
        () -> EntityType.Builder.<MissileEntity>of(MissileEntity::new, MobCategory.MISC)
            .sized(1.0f, 6.4f).clientTrackingRange(64).updateInterval(1).fireImmune()
            .build(MissilesNukes.MODID + ":icbm_heavy_missile"));

    public static final DeferredHolder<EntityType<?>, EntityType<MissilePartEntity>> PART = ENTITIES.register("icbm_part",
        () -> EntityType.Builder.<MissilePartEntity>of(MissilePartEntity::new, MobCategory.MISC)
            .sized(0.8f, 0.8f).clientTrackingRange(64).updateInterval(1).fireImmune().noSave()
            .build(MissilesNukes.MODID + ":icbm_part"));

    public static final DeferredHolder<EntityType<?>, EntityType<ExplosionFxEntity>> EXPLOSION_FX = ENTITIES.register("icbm_explosion_fx",
        () -> EntityType.Builder.<ExplosionFxEntity>of(ExplosionFxEntity::new, MobCategory.MISC)
            .sized(1.0f, 1.0f).clientTrackingRange(96).updateInterval(20).fireImmune().noSave()
            .build(MissilesNukes.MODID + ":icbm_explosion_fx"));

    private ModEntities() {}
}
