package com.transportgolem.event;

import com.transportgolem.TransportGolemMod;
import com.transportgolem.entity.TransportGolemEntity;
import com.transportgolem.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Build pattern (the vanilla golem shape with a double-height body), front view, top to bottom:
 * <pre>
 *   . P .      P = carved pumpkin / jack o'lantern (placed last)
 *   I I I      I = iron block
 *   I I I
 *   . I .
 * </pre>
 * Handled on right-click *before* vanilla's own golem check, so the 4-block vanilla T inside this shape can't steal it.
 */
@Mod.EventBusSubscriber(modid = TransportGolemMod.MODID)
public final class GolemBuildHandler {

    private record Pattern(BlockPos bottom, List<BlockPos> blocks) {}

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BlockItem bi)) return;
        Block b = bi.getBlock();
        if (b != Blocks.CARVED_PUMPKIN && b != Blocks.JACK_O_LANTERN) return;

        Level level = event.getLevel();
        Player player = event.getEntity();
        BlockPlaceContext ctx = new BlockPlaceContext(player, event.getHand(), stack, event.getHitVec());
        if (!ctx.canPlace()) return;
        Pattern pattern = find(level, ctx.getClickedPos());
        if (pattern == null) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (!(level instanceof ServerLevel sl)) return;

        for (BlockPos p : pattern.blocks()) {
            BlockState old = sl.getBlockState(p);
            sl.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            sl.levelEvent(2001, p, Block.getId(old));
        }
        TransportGolemEntity golem = ModEntities.TRANSPORT_GOLEM.get().create(sl);
        if (golem != null) {
            BlockPos bt = pattern.bottom();
            golem.moveTo(bt.getX() + 0.5D, bt.getY() + 0.05D, bt.getZ() + 0.5D, player.getYRot() + 180.0F, 0.0F);
            golem.yBodyRot = golem.getYRot();
            golem.yHeadRot = golem.getYRot();
            golem.setPlayerCreated(true);
            sl.addFreshEntity(golem);
        }
        for (BlockPos p : pattern.blocks()) sl.blockUpdated(p, Blocks.AIR);
        if (!player.getAbilities().instabuild) stack.shrink(1);
    }

    private static Pattern find(Level level, BlockPos pumpkin) {
        for (Direction side : new Direction[]{Direction.EAST, Direction.SOUTH}) {
            List<BlockPos> list = new ArrayList<>();
            boolean ok = true;
            for (int dy = 1; dy <= 2 && ok; dy++) {
                for (int s = -1; s <= 1 && ok; s++) {
                    BlockPos p = pumpkin.below(dy).relative(side, s);
                    if (level.getBlockState(p).is(Blocks.IRON_BLOCK)) list.add(p);
                    else ok = false;
                }
            }
            BlockPos bottom = pumpkin.below(3);
            if (ok && level.getBlockState(bottom).is(Blocks.IRON_BLOCK)) {
                list.add(bottom);
                return new Pattern(bottom, list);
            }
        }
        return null;
    }

    private GolemBuildHandler() {}
}
