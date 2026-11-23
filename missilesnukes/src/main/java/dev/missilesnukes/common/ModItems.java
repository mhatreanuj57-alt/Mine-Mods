package dev.missilesnukes.common;

import dev.missilesnukes.sim.MissileSpec;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MissilesNukes.MODID);

    public static final DeferredItem<Item> CENTRAL_BODY = ITEMS.registerItem("light_icbm_central_body",
        p -> new TooltipItem(p, "tooltip.missilesnukes.central_body"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> ENGINE_MOUNT = ITEMS.registerItem("light_icbm_engine_mount",
        p -> new TooltipItem(p, "tooltip.missilesnukes.engine_mount"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> WARHEAD = ITEMS.registerItem("light_icbm_warhead",
        p -> new TooltipItem(p, "tooltip.missilesnukes.warhead"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<MissileItem> COMPLETE_MISSILE = ITEMS.registerItem("light_icbm_complete_missile",
        p -> new MissileItem(p, ModEntities.MISSILE::get, MissileSpec.LIGHT, "tooltip.missilesnukes.missile"), new Item.Properties().stacksTo(1));

    // Heavy ICBM: components and the assembled missile. No crafting recipes yet (creative tab / /give only).
    public static final DeferredItem<Item> HEAVY_CENTRAL_BODY = ITEMS.registerItem("heavy_icbm_central_body",
        p -> new TooltipItem(p, "tooltip.missilesnukes.heavy_central_body"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> HEAVY_ENGINE_MOUNT = ITEMS.registerItem("heavy_icbm_engine_mount",
        p -> new TooltipItem(p, "tooltip.missilesnukes.heavy_engine_mount"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> HEAVY_WARHEAD = ITEMS.registerItem("heavy_icbm_warhead",
        p -> new TooltipItem(p, "tooltip.missilesnukes.heavy_warhead"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<MissileItem> HEAVY_MISSILE = ITEMS.registerItem("heavy_icbm_complete_missile",
        p -> new MissileItem(p, ModEntities.HEAVY_MISSILE::get, MissileSpec.HEAVY, "tooltip.missilesnukes.heavy_missile"), new Item.Properties().stacksTo(1));
    public static final DeferredItem<LaunchRemoteItem> LAUNCH_REMOTE = ITEMS.registerItem("light_icbm_launch_remote",
        LaunchRemoteItem::new, new Item.Properties().stacksTo(1));

    private ModItems() {}
}
