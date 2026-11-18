package com.transportgolem.registry;

import com.transportgolem.TransportGolemMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, TransportGolemMod.MODID);

    public static final RegistryObject<Item> SEATING_ARRANGEMENT =
            ITEMS.register("seating_arrangement", () -> new Item(new Item.Properties().stacksTo(16)));

    /** Spawns the plain golem (no seats) - apply a Seating Arrangement to it. For testing/creative. */
    public static final RegistryObject<Item> TRANSPORT_GOLEM_SPAWN_EGG =
            ITEMS.register("transport_golem_spawn_egg",
                    () -> new ForgeSpawnEggItem(ModEntities.TRANSPORT_GOLEM, 0xB8B8B8, 0xC07838, new Item.Properties()));

    private ModItems() {}
}
