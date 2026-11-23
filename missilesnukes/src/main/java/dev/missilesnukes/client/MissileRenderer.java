package dev.missilesnukes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.missilesnukes.common.MissileEntity;
import dev.missilesnukes.sim.FlightState.Stage;
import dev.missilesnukes.sim.MissileSpec;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders a missile (Light or Heavy) from its original model, as three stages. Stages that have separated are simply
 * not attached any more (they exist as their own entities), and the thruster flame is the original animated effect.
 */
public class MissileRenderer extends EntityRenderer<MissileEntity> {
    private final MissileSpec spec;

    public MissileRenderer(EntityRendererProvider.Context ctx, MissileSpec spec) {
        super(ctx);
        this.spec = spec;
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(MissileEntity entity) { return GlbAssets.white(); }

    @Override
    public void render(MissileEntity e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        GlbModels.MissileSet m = GlbModels.missile(spec);
        Stage stage = e.stage();

        ps.pushPose();
        ps.translate(0.0, spec.halfHeight, 0.0);
        ps.mulPose(e.renderOrientation(partialTick));
        if (e.warheadPivot()) {
            double[] c = spec.centroidWarhead;      // after separation the warhead rotates about its own centre
            ps.translate(-c[0], -c[1], -c[2]);
        }
        if (e.engineAttached()) GlbRender.draw(m.pieces[0], m.restWorld, ps, buffers, light, OverlayTexture.NO_OVERLAY, 0, 0f);
        if (e.bodyAttached()) GlbRender.draw(m.pieces[1], m.restWorld, ps, buffers, light, OverlayTexture.NO_OVERLAY, 0, 0f);
        GlbRender.draw(m.pieces[2], m.restWorld, ps, buffers, light, OverlayTexture.NO_OVERLAY, 0, 0f);

        float thrust = e.thrust();
        boolean burning = stage.ordinal() >= Stage.IGNITION.ordinal() && stage.ordinal() <= Stage.CUTOFF.ordinal();
        if (e.thrusterFx() && e.engineAttached() && burning && thrust > 0.02f && m.flame.anim != null) {
            GlbModels.FlameSet f = m.flame;
            ps.pushPose();
            ps.translate(0.0, -spec.halfHeight, 0.0);          // scale the plume about the nozzle: it grows with thrust
            ps.scale(0.7f + 0.3f * thrust, 0.35f + 0.65f * thrust, 0.7f + 0.3f * thrust);
            ps.translate(0.0, spec.halfHeight, 0.0);
            float t = (e.tickCount + partialTick) / 20f;
            float[][] world = f.model.gltf.worldMatrices(f.anim, t, true);
            GlbRender.draw(f.draws, world, ps, buffers, light, OverlayTexture.NO_OVERLAY, 0, 0f);
            ps.popPose();
        }
        ps.popPose();
    }
}
