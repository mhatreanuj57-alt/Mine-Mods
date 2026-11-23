package dev.missilesnukes.common;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MissilesNukes.MODID);

    /** The 8 launch slots stored on a launch remote. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RemoteData>> REMOTE =
        COMPONENTS.registerComponentType("remote", b -> b.persistent(RemoteData.CODEC).networkSynchronized(RemoteData.STREAM_CODEC));

    private ModComponents() {}
}
