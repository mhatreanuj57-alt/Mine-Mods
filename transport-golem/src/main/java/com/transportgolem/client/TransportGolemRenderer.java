package com.transportgolem.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.transportgolem.TransportGolemMod;
import com.transportgolem.entity.TransportGolemEntity;
import com.transportgolem.model.GolemModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Draws the converted GLB node tree directly (no GeckoLib). Geometry is untouched, animation is sampled per frame. */
public class TransportGolemRenderer extends EntityRenderer<TransportGolemEntity> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TransportGolemMod.MODID, "textures/entity/transport_golem.png");

    public TransportGolemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 3.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TransportGolemEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(TransportGolemEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        GolemModel model = GolemModel.get();
        GolemModel.Pose pose = entity.computePose(partialTick);

        poseStack.pushPose();
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        if (entity.deathTime > 0) {
            float f = ((float) entity.deathTime + partialTick - 1.0F) / 20.0F * 1.6F;
            f = Math.min(Mth.sqrt(f), 1.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(f * 90.0F));
        }
        float s = 1.0F / GolemModel.UNITS_PER_BLOCK;
        poseStack.scale(s, s, s);

        int overlay = OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(entity.hurtTime > 0 || entity.deathTime > 0));
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        renderNode(model, pose, 0, entity.hasSeats(), poseStack, vc, packedLight, overlay);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private void renderNode(GolemModel model, GolemModel.Pose pose, int index, boolean seats, PoseStack ps,
                            VertexConsumer vc, int light, int overlay) {
        GolemModel.Node n = model.nodes[index];
        ps.pushPose();
        ps.translate(pose.t[index * 3], pose.t[index * 3 + 1], pose.t[index * 3 + 2]);
        ps.mulPose(new Quaternionf(pose.r[index * 4], pose.r[index * 4 + 1], pose.r[index * 4 + 2], pose.r[index * 4 + 3]));

        if (n.quadCount > 0 && (seats || !n.seatPart)) {
            PoseStack.Pose last = ps.last();
            Matrix4f pm = last.pose();
            Matrix3f nm = last.normal();
            float[] q = n.quads;
            for (int i = 0; i < n.quadCount; i++) {
                for (int v = 0; v < 4; v++) {
                    int o = i * 32 + v * 8;
                    vc.vertex(pm, q[o], q[o + 1], q[o + 2])
                            .color(255, 255, 255, 255)
                            .uv(q[o + 6], q[o + 7])
                            .overlayCoords(overlay)
                            .uv2(light)
                            .normal(nm, q[o + 3], q[o + 4], q[o + 5])
                            .endVertex();
                }
            }
        }
        for (int c : n.children) renderNode(model, pose, c, seats, ps, vc, light, overlay);
        ps.popPose();
    }
}
