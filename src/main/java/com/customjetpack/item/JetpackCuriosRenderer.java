package com.customjetpack.item;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public final class JetpackCuriosRenderer implements ICurioRenderer {
    private final JetpackItemRenderer renderer = new JetpackItemRenderer();

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext,
            PoseStack poseStack, RenderLayerParent<T, M> layerParent, MultiBufferSource buffer, int packedLight,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
            float headPitch) {
        RenderContext.begin(slotContext.entity());
        poseStack.pushPose();
        try {
            poseStack.translate(0, 0.35, 0.2);
            renderer.renderByItem(stack, ItemDisplayContext.NONE, poseStack, buffer, packedLight, 0);
        } finally {
            poseStack.popPose();
            RenderContext.end();
        }
    }
}
