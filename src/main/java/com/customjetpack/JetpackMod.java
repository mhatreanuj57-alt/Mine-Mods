package com.customjetpack;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod("custom_jetpack")
public class JetpackMod {
    private static final Logger LOGGER = LogUtils.getLogger();

    public JetpackMod(FMLJavaModLoadingContext context) {
        LOGGER.info("Custom Jetpack loading");
    }
}
