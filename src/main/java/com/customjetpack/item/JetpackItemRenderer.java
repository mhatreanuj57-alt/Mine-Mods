package com.customjetpack.item;

import software.bernie.geckolib.renderer.GeoItemRenderer;

public class JetpackItemRenderer extends GeoItemRenderer<JetpackItem> {
    public JetpackItemRenderer() {
        super(new JetpackGeoModel());
    }
}
