package com.customjetpack;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod("custom_jetpack")
public class JetpackMod {
    public static final String MODID = "custom_jetpack";
    private static final Logger LOGGER = LogUtils.getLogger();

    public JetpackMod(FMLJavaModLoadingContext context) {
        ModItems.ITEMS.register(context.getModEventBus());
        LOGGER.info("Custom Jetpack loading");
    }
}
