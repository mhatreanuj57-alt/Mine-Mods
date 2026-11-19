package net.runictitan;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
	public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RunicTitan.MOD_ID);

	public static final RegistryObject<EntityType<ThrownHammer>> THROWN_HAMMER = ENTITIES.register("thrown_hammer",
			() -> EntityType.Builder.<ThrownHammer>of(ThrownHammer::new, MobCategory.MISC)
					.sized(0.7f, 0.7f)
					.clientTrackingRange(8)
					.updateInterval(20)
					.build("thrown_hammer"));

	private ModEntities() {}
}
