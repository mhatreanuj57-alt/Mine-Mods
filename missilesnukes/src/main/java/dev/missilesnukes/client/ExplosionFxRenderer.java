package dev.missilesnukes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.missilesnukes.common.ExplosionFxEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Plays the original animated explosion model at the point of impact, scaled to the crater size. */
public class ExplosionFxRenderer extends EntityRenderer<ExplosionFxEntity> {
    public ExplosionFxRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(ExplosionFxEntity entity) { return GlbAssets.white(); }

    @Override
    public boolean shouldRender(ExplosionFxEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(ExplosionFxEntity e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        GlbModels.ExplosionSet x = GlbModels.explosion();
        if (x.anim == null) return;
        float t = (e.tickCount + partialTick) / 20f;
        if (t > x.anim.duration) return;
        float[][] world = x.model.gltf.worldMatrices(x.anim, t, false);
        float s = e.modelScale();
        ps.pushPose();
        ps.scale(s, s, s);
        GlbRender.draw(x.draws, world, ps, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0, 0f);
        ps.popPose();
    }
}
