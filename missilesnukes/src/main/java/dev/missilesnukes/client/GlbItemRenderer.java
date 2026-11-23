package dev.missilesnukes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.missilesnukes.common.ModItems;
import dev.missilesnukes.sim.MissileSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Renders the five items from their original GLB models: the three crafting components, the assembled missile and the
 * launch remote (whose eight keycaps visibly depress when a button is pressed in the control panel).
 */
public class GlbItemRenderer extends BlockEntityWithoutLevelRenderer {
    public GlbItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Item item = stack.getItem();
        GlbModel model;
        List<GlbModel.Draw> draws;
        int pressMask = 0;
        boolean longObject = true;

        if (item == ModItems.LAUNCH_REMOTE.get()) {
            GlbModels.RemoteSet r = GlbModels.remote();
            model = r.model; draws = r.draws; longObject = false;
            if (Minecraft.getInstance().level != null) pressMask = RemoteVisual.mask(Minecraft.getInstance().level.getGameTime());
        } else if (item == ModItems.COMPLETE_MISSILE.get() || item == ModItems.HEAVY_MISSILE.get()) {
            GlbModels.MissileSet s = GlbModels.missile(item == ModItems.HEAVY_MISSILE.get() ? MissileSpec.HEAVY : MissileSpec.LIGHT);
            // the item shows the full missile: all three stages
            ps.pushPose();
            place(ps, s.model, ctx, true);
            for (int i = 0; i < 3; i++) GlbRender.draw(s.pieces[i], s.restWorld, ps, buffers, light, overlay, 0, 0f);
            ps.popPose();
            return;
        } else if (item == ModItems.WARHEAD.get()) {
            GlbModels.StaticSet s = GlbModels.warhead(); model = s.model; draws = s.draws;
        } else if (item == ModItems.CENTRAL_BODY.get()) {
            GlbModels.StaticSet s = GlbModels.body(); model = s.model; draws = s.draws;
        } else if (item == ModItems.ENGINE_MOUNT.get()) {
            GlbModels.StaticSet s = GlbModels.engine(); model = s.model; draws = s.draws;
        } else if (item == ModItems.HEAVY_WARHEAD.get()) {
            GlbModels.StaticSet s = GlbModels.heavyWarhead(); model = s.model; draws = s.draws;
        } else if (item == ModItems.HEAVY_CENTRAL_BODY.get()) {
            GlbModels.StaticSet s = GlbModels.heavyBody(); model = s.model; draws = s.draws;
        } else if (item == ModItems.HEAVY_ENGINE_MOUNT.get()) {
            GlbModels.StaticSet s = GlbModels.heavyEngine(); model = s.model; draws = s.draws;
        } else {
            return;
        }

        ps.pushPose();
        place(ps, model, ctx, longObject);
        GlbRender.draw(draws, null, ps, buffers, light, overlay, pressMask, 0.045f);
        ps.popPose();
    }

    /** Fits the model into a unit cube for the given display context and orients it sensibly. */
    private static void place(PoseStack ps, GlbModel model, ItemDisplayContext ctx, boolean longObject) {
        float fit = switch (ctx) {
            case GUI -> 0.92f;
            case GROUND -> 0.5f;
            case FIXED -> 0.9f;
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> 0.85f;
            default -> 0.6f;
        };
        ps.translate(0.5, 0.5, 0.5);
        switch (ctx) {
            case GUI -> {
                ps.mulPose(Axis.XP.rotationDegrees(longObject ? 8 : 12));
                ps.mulPose(Axis.YP.rotationDegrees(longObject ? -28 : -18));
                if (longObject) ps.mulPose(Axis.ZP.rotationDegrees(28));
            }
            case FIXED -> ps.mulPose(Axis.YP.rotationDegrees(180));
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                ps.mulPose(Axis.XP.rotationDegrees(-15));
                if (longObject) ps.mulPose(Axis.ZP.rotationDegrees(-20));
            }
            case GROUND -> ps.mulPose(Axis.YP.rotationDegrees(25));
            default -> { }
        }
        float s = fit / model.maxDimension();
        ps.scale(s, s, s);
        ps.translate(-model.centerX(), -model.centerY(), -model.centerZ());
    }
}
