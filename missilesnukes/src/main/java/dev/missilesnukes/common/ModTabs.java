package dev.missilesnukes.common;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MissilesNukes.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.missilesnukes"))
        .icon(() -> ModItems.COMPLETE_MISSILE.get().getDefaultInstance())
        .displayItems((params, out) -> {
            out.accept(ModItems.CENTRAL_BODY.get());
            out.accept(ModItems.ENGINE_MOUNT.get());
            out.accept(ModItems.WARHEAD.get());
            out.accept(ModItems.COMPLETE_MISSILE.get());
            out.accept(ModItems.HEAVY_CENTRAL_BODY.get());
            out.accept(ModItems.HEAVY_ENGINE_MOUNT.get());
            out.accept(ModItems.HEAVY_WARHEAD.get());
            out.accept(ModItems.HEAVY_MISSILE.get());
            out.accept(ModItems.LAUNCH_REMOTE.get());
        })
        .build());

    private ModTabs() {}
}
