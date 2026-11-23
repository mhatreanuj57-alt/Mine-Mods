package dev.missilesnukes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.missilesnukes.common.MissilePartEntity;
import dev.missilesnukes.sim.MissileSpec;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Renders a detached engine section or spent body (of either missile type), tumbling about its own centre of mass. */
public class MissilePartRenderer extends EntityRenderer<MissilePartEntity> {
    public MissilePartRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(MissilePartEntity entity) { return GlbAssets.white(); }

    @Override
    public void render(MissilePartEntity e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        MissileSpec spec = e.spec();
        GlbModels.MissileSet m = GlbModels.missile(spec);
        boolean engine = e.partType() == MissilePartEntity.ENGINE;
        double[] c = engine ? spec.centroidEngine : spec.centroidBody;
        ps.pushPose();
        ps.translate(0.0, MissilePartEntity.HALF, 0.0);
        ps.mulPose(e.renderOrientation(partialTick));
        ps.translate(-c[0], -c[1], -c[2]);
        GlbRender.draw(m.pieces[engine ? 0 : 1], m.restWorld, ps, buffers, light, OverlayTexture.NO_OVERLAY, 0, 0f);
        ps.popPose();
    }
}
