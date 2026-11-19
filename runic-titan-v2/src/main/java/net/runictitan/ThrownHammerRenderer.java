package net.runictitan;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws the thrown hammer with its real 3D item model, spinning end over end while it flies. */
public class ThrownHammerRenderer extends EntityRenderer<ThrownHammer> {
	private ItemStack stack;

	public ThrownHammerRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(ThrownHammer hammer, float entityYaw, float partialTick, PoseStack pose,
					   MultiBufferSource buffer, int light) {
		if (stack == null) stack = new ItemStack(ModItems.EARTHSHAKER.get());

		pose.pushPose();
		float yRot = Mth.lerp(partialTick, hammer.yRotO, hammer.getYRot());
		float xRot = Mth.lerp(partialTick, hammer.xRotO, hammer.getXRot());
		pose.mulPose(Axis.YP.rotationDegrees(-yRot - 90.0F));
		pose.mulPose(Axis.ZP.rotationDegrees(-xRot));
		if (!hammer.isStuck()) {
			pose.mulPose(Axis.ZP.rotationDegrees(-(hammer.tickCount + partialTick) * 50.0F));
		}
		pose.scale(1.6F, 1.6F, 1.6F);
		Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light,
				OverlayTexture.NO_OVERLAY, pose, buffer, hammer.level(), hammer.getId());
		pose.popPose();

		super.render(hammer, entityYaw, partialTick, pose, buffer, light);
	}

	@Override
	public ResourceLocation getTextureLocation(ThrownHammer hammer) {
		return InventoryMenu.BLOCK_ATLAS;
	}
}
