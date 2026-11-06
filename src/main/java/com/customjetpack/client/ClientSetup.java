package com.customjetpack.client;

import java.util.function.Supplier;

import com.customjetpack.JetpackMod;
import com.customjetpack.ModItems;
import com.customjetpack.item.JetpackCuriosRenderer;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import top.theillusivec4.curios.api.client.ICurioRenderer;

@EventBusSubscriber(modid = JetpackMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> BakedModelFactory.register(JetpackMod.MODID, PolyMeshBakedModelFactory.INSTANCE));
        event.enqueueWork(() -> {
            try {
                // shortcut: Curios' dev compile hides this client registry, upgrade when it is visible directly.
                Class.forName("top.theillusivec4.curios.api.client.CuriosRendererRegistry")
                        .getMethod("register", Item.class, Supplier.class)
                        .invoke(null, ModItems.JETPACK.get(), (Supplier<ICurioRenderer>) JetpackCuriosRenderer::new);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Could not register the jetpack Curios renderer", e);
            }
        });
    }
}
