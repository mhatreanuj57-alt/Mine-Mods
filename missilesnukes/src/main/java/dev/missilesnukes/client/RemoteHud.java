package dev.missilesnukes.client;

import dev.missilesnukes.common.MissileEntity;
import dev.missilesnukes.common.ModComponents;
import dev.missilesnukes.common.ModItems;
import dev.missilesnukes.common.RemoteData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Compact heads-up display shown while the launch remote is held: eight slot LEDs plus live data for the selected slot. */
public class RemoteHud implements LayeredDraw.Layer {
    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.options.hideGui || mc.screen != null) return;
        ItemStack stack = p.getMainHandItem().is(ModItems.LAUNCH_REMOTE.get()) ? p.getMainHandItem()
            : p.getOffhandItem().is(ModItems.LAUNCH_REMOTE.get()) ? p.getOffhandItem() : ItemStack.EMPTY;
        if (stack.isEmpty()) return;
        RemoteData d = stack.getOrDefault(ModComponents.REMOTE.get(), RemoteData.EMPTY);

        int x = 6, y = 6;
        g.fill(x - 3, y - 3, x + 8 * 20 + 3, y + 40, 0xAA0A1512);
        for (int i = 0; i < 8; i++) {
            RemoteData.Slot s = d.slot(i);
            int bx = x + i * 20;
            g.fill(bx, y, bx + 18, y + 14, i == d.selected() ? 0xFF2F6F5F : 0xFF1A2623);
            g.fill(bx + 1, y + 1, bx + 17, y + 3, RemoteScreen.statusColor(s));
            g.drawCenteredString(mc.font, String.valueOf(i + 1), bx + 9, y + 5, 0xFFFFFFFF);
        }
        RemoteData.Slot s = d.slot(d.selected());
        MissileEntity m = s.missile().map(ClientFlight::find).orElse(null);
        String line1 = "ICBM-0" + (d.selected() + 1) + "  " + RemoteScreen.statusText(s, m != null);
        g.drawString(mc.font, line1, x, y + 19, RemoteScreen.statusColor(s), false);
        if (m != null && m.stage() != dev.missilesnukes.sim.FlightState.Stage.PAD) {
            ClientFlight.Live l = ClientFlight.live(m);
            g.drawString(mc.font, String.format("%s  ALT %.0f  %.0f b/s", l.stage(), l.altitude(), l.speedBps()), x, y + 29, 0xFFE8FFF8, false);
            g.drawString(mc.font, String.format("ETA %.1f s  TGT %.0f", l.etaSeconds(), l.distanceToTarget()), x, y + 39, 0xFF9FD8C8, false);
        }
    }
}
