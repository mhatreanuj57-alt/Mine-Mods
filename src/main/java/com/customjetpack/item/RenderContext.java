package com.customjetpack.item;

import net.minecraft.world.entity.LivingEntity;

public final class RenderContext {
    private static int entityId = -1;

    private RenderContext() {}

    public static int entityId() {
        return entityId;
    }

    public static void begin(LivingEntity entity) {
        entityId = entity.getId();
    }

    public static void end() {
        entityId = -1;
    }
}
