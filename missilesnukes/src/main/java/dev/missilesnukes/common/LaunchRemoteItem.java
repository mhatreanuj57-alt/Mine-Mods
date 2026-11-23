package dev.missilesnukes.common;

import dev.missilesnukes.client.ClientHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.List;

/** The handheld controller with eight launch slots. Right-click opens the control panel (client side). */
public class LaunchRemoteItem extends Item {
    public LaunchRemoteItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide && FMLEnvironment.dist == Dist.CLIENT) ClientHooks.openRemote();
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.missilesnukes.remote").withStyle(ChatFormatting.GRAY));
        RemoteData d = stack.getOrDefault(ModComponents.REMOTE.get(), RemoteData.EMPTY);
        tooltip.add(Component.literal(d.readyCount() + "/8 ready").withStyle(ChatFormatting.DARK_GREEN));
    }
}
