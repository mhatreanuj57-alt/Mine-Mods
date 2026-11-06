package com.customjetpack.item;

import com.customjetpack.JetpackMod;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class JetpackGeoModel extends GeoModel<JetpackItem> {
    @Override
    public ResourceLocation getModelResource(JetpackItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(JetpackMod.MODID, "geo/models/jetpack.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(JetpackItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(JetpackMod.MODID, "textures/models/jetpack_atlas.png");
    }

    @Override
    public ResourceLocation getAnimationResource(JetpackItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(JetpackMod.MODID, "animations/jetpack.animation.json");
    }
}
