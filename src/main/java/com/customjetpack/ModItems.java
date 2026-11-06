package com.customjetpack;

import com.customjetpack.item.JetpackItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, JetpackMod.MODID);
    public static final RegistryObject<JetpackItem> JETPACK = ITEMS.register("jetpack",
            () -> new JetpackItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}
}
