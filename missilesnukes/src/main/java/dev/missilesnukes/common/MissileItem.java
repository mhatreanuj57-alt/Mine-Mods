package dev.missilesnukes.common;

import net.minecraft.ChatFormatting;
import dev.missilesnukes.sim.MissileSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.function.Supplier;

/** A fully assembled missile. Using it on the top of a block stands it upright there as a launch-ready entity. */
public class MissileItem extends Item {
    private final Supplier<EntityType<MissileEntity>> entityType;
    private final MissileSpec spec;
    private final String tooltipKey;

    public MissileItem(Properties properties, Supplier<EntityType<MissileEntity>> entityType, MissileSpec spec, String tooltipKey) {
        super(properties);
        this.entityType = entityType;
        this.spec = spec;
        this.tooltipKey = tooltipKey;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (ctx.getClickedFace() != Direction.UP) return InteractionResult.FAIL;
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(level, pos);
        double groundY = pos.getY() + (shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y));
        double x = pos.getX() + 0.5, z = pos.getZ() + 0.5;

        double hw = spec.width * 0.5;
        AABB box = new AABB(x - hw, groundY + 0.01, z - hw, x + hw, groundY + spec.halfHeight * 2.0, z + hw);
        if (!level.noCollision(box)) return InteractionResult.FAIL;

        if (!level.isClientSide) {
            MissileEntity missile = entityType.get().create(level);
            if (missile == null) return InteractionResult.FAIL;
            missile.placeOnPad(x, groundY, z);
            level.addFreshEntity(missile);
            level.playSound(null, x, groundY, z, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.BLOCKS, 1.5f, 0.6f);
            Player player = ctx.getPlayer();
            if (player == null || !player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
    }
}
