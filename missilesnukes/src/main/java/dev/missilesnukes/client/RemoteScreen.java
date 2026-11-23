package dev.missilesnukes.client;

import dev.missilesnukes.common.ModComponents;
import dev.missilesnukes.common.ModItems;
import dev.missilesnukes.common.MissileEntity;
import dev.missilesnukes.common.RemoteData;
import dev.missilesnukes.common.RemoteData.Slot;
import dev.missilesnukes.common.RemoteData.Status;
import dev.missilesnukes.net.RemoteActionPayload;
import dev.missilesnukes.sim.FlightSim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The launch remote's control panel: eight pad buttons (one launch slot each, with their own target, state and status
 * LED), target entry, live flight telemetry, single and multi-launch. Styled after the OLED display of the 3D remote.
 * Click selects a slot; Shift+click arms or disarms it for a simultaneous launch.
 */
public class RemoteScreen extends Screen {
    /** Compact control panel size in GUI pixels (it also follows Minecraft's own GUI Scale option). */
    private static final int W = 224, H = 200;
    private static final int TEAL = 0xFF5CF2C4, GREEN = 0xFF33D699, AMBER = 0xFFE0B040, RED = 0xFFE05050, ORANGE = 0xFFFF8A3D, GREY = 0xFF667777, VIOLET = 0xFF8C8CC8;
    private static final int PANEL = 0xFF0F1D1A, PANEL_EDGE = 0xFF1E5A4A, PAD_BG = 0xFF14211E;

    private int left, top;
    private final PadButton[] pads = new PadButton[8];
    final int[] pressTicks = new int[8];
    final boolean[] armed = new boolean[8];
    int sel;
    private EditBox bx, by, bz;
    private Button fxButton, launchButton;
    private String planKey = "";
    private FlightSim.Plan plan;

    public RemoteScreen() {
        super(Component.literal("Light ICBM Link"));
    }

    // ------------------------------------------------------------------------------------------ data helpers

    private ItemStack heldRemote() {
        Player p = Minecraft.getInstance().player;
        if (p == null) return ItemStack.EMPTY;
        if (p.getMainHandItem().is(ModItems.LAUNCH_REMOTE.get())) return p.getMainHandItem();
        if (p.getOffhandItem().is(ModItems.LAUNCH_REMOTE.get())) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }

    RemoteData data() { return heldRemote().getOrDefault(ModComponents.REMOTE.get(), RemoteData.EMPTY); }

    private MissileEntity missileOf(Slot s) { return s.missile().map(ClientFlight::find).orElse(null); }

    static String statusText(Slot s, boolean live) {
        return switch (s.status()) {
            case Status.EMPTY -> "EMPTY";
            case Status.LINKED -> s.hasTarget() && !s.feasible() ? "NO SOLUTION" : "LINKED";
            case Status.READY -> "READY";
            case Status.FIRED -> live ? "IN FLIGHT" : "FIRED";
            case Status.DONE -> "DETONATED";
            default -> "NO SIGNAL";
        };
    }

    static int statusColor(Slot s) {
        return switch (s.status()) {
            case Status.EMPTY -> GREY;
            case Status.LINKED -> s.hasTarget() && !s.feasible() ? RED : AMBER;
            case Status.READY -> GREEN;
            case Status.FIRED -> ORANGE;
            case Status.DONE -> VIOLET;
            default -> RED;
        };
    }

    private void send(RemoteActionPayload p) { PacketDistributor.sendToServer(p); }

    // ------------------------------------------------------------------------------------------ setup

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        RemoteData d = data();
        sel = d.selected();

        for (int i = 0; i < 8; i++) {
            int c = i % 4, r = i / 4;
            pads[i] = addRenderableWidget(new PadButton(left + 6 + c * 54, top + 47 + r * 25, 50, 22, i, this));
        }

        bx = box(left + 16, top + 99, 54, "X");
        by = box(left + 84, top + 99, 38, "Y");
        bz = box(left + 136, top + 99, 54, "Z");
        fillBoxes(d.slot(sel));

        addRenderableWidget(Button.builder(Component.literal("AIM"), b -> aim()).bounds(left + 6, top + 116, 52, 16).build());
        addRenderableWidget(Button.builder(Component.literal("HERE"), b -> here()).bounds(left + 62, top + 116, 52, 16).build());
        addRenderableWidget(Button.builder(Component.literal("SET TARGET"), b -> sendTarget(true)).bounds(left + 118, top + 116, 100, 16).build());

        addRenderableWidget(Button.builder(Component.literal("LINK"), b -> send(RemoteActionPayload.simple(RemoteActionPayload.LINK_NEAREST, sel))).bounds(left + 6, top + 135, 44, 16).build());
        addRenderableWidget(Button.builder(Component.literal("UNLINK"), b -> send(RemoteActionPayload.simple(RemoteActionPayload.UNLINK, sel))).bounds(left + 54, top + 135, 50, 16).build());
        fxButton = addRenderableWidget(Button.builder(Component.literal("FX: ON"), b -> send(RemoteActionPayload.simple(RemoteActionPayload.TOGGLE_FX, sel))).bounds(left + 108, top + 135, 46, 16).build());
        launchButton = addRenderableWidget(Button.builder(Component.literal("LAUNCH"), b -> launchSelected()).bounds(left + 158, top + 135, 60, 16).build());
        addRenderableWidget(Button.builder(Component.literal("LAUNCH ALL READY"), b -> send(RemoteActionPayload.simple(RemoteActionPayload.LAUNCH_ALL, sel))).bounds(left + 6, top + 154, 212, 16).build());
    }

    private EditBox box(int x, int y, int w, String label) {
        EditBox b = new EditBox(font, x, y, w, 14, Component.literal(label));
        b.setMaxLength(10);
        b.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{1,9}"));
        addRenderableWidget(b);
        return b;
    }

    private void fillBoxes(Slot s) {
        Player p = Minecraft.getInstance().player;
        if (s.hasTarget() || p == null) {
            bx.setValue(String.valueOf(s.x())); by.setValue(String.valueOf(s.y())); bz.setValue(String.valueOf(s.z()));
        } else {
            bx.setValue(String.valueOf(p.getBlockX())); by.setValue(String.valueOf(p.getBlockY() - 1)); bz.setValue(String.valueOf(p.getBlockZ()));
        }
    }

    private int[] parseTarget() {
        try { return new int[]{Integer.parseInt(bx.getValue()), Integer.parseInt(by.getValue()), Integer.parseInt(bz.getValue())}; }
        catch (NumberFormatException e) { return null; }
    }

    // ------------------------------------------------------------------------------------------ actions

    void onPad(int slot, boolean shift) {
        Minecraft mc = Minecraft.getInstance();
        pressTicks[slot] = RemoteVisual.PRESS_TICKS;
        if (mc.level != null) RemoteVisual.press(slot, mc.level.getGameTime());
        if (shift) {
            armed[slot] = !armed[slot];
        } else {
            sel = slot;
            send(RemoteActionPayload.simple(RemoteActionPayload.SELECT, slot));
            fillBoxes(data().slot(slot));
        }
    }

    private void sendTarget(boolean force) {
        int[] t = parseTarget();
        if (t == null) return;
        Slot cur = data().slot(sel);
        if (force || !cur.hasTarget() || cur.x() != t[0] || cur.y() != t[1] || cur.z() != t[2]) {
            send(new RemoteActionPayload(RemoteActionPayload.SET_TARGET, sel, t[0], t[1], t[2]));
        }
    }

    private void launchSelected() {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 8; i++) if (armed[i]) slots.add(i);
        if (slots.isEmpty()) slots.add(sel);
        Minecraft mc = Minecraft.getInstance();
        for (int s : slots) {
            if (s == sel) sendTarget(false);
            send(RemoteActionPayload.simple(RemoteActionPayload.LAUNCH, s));
            if (mc.level != null) RemoteVisual.press(s, mc.level.getGameTime());
            pressTicks[s] = RemoteVisual.PRESS_TICKS;
            armed[s] = false;
        }
    }

    private void aim() {
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        HitResult hit = p.pick(512.0, 1.0f, false);
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            bx.setValue(String.valueOf(bhr.getBlockPos().getX()));
            by.setValue(String.valueOf(bhr.getBlockPos().getY()));
            bz.setValue(String.valueOf(bhr.getBlockPos().getZ()));
        }
    }

    private void here() {
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        bx.setValue(String.valueOf(p.getBlockX())); by.setValue(String.valueOf(p.getBlockY() - 1)); bz.setValue(String.valueOf(p.getBlockZ()));
    }

    // ------------------------------------------------------------------------------------------ ticking / input

    @Override
    public void tick() {
        super.tick();
        if (heldRemote().isEmpty()) { onClose(); return; }
        for (int i = 0; i < 8; i++) if (pressTicks[i] > 0) pressTicks[i]--;
        MissileEntity m = missileOf(data().slot(sel));
        fxButton.setMessage(Component.literal(m != null && !m.thrusterFx() ? "FX: OFF" : "FX: ON"));
        int armedCount = 0;
        for (boolean a : armed) if (a) armedCount++;
        launchButton.setMessage(Component.literal(armedCount > 0 ? "LAUNCH x" + armedCount : "LAUNCH " + (sel + 1)));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean typing = getFocused() instanceof EditBox eb && eb.isFocused();
        if (!typing && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_8) {
            onPad(key - GLFW.GLFW_KEY_1, Screen.hasShiftDown());
            return true;
        }
        if (typing && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) { sendTarget(true); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) { /* drawn in render() so widgets stay on top */ }

    // ------------------------------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        RemoteData d = data();
        Slot s = d.slot(sel);
        MissileEntity m = missileOf(s);
        ClientFlight.Live live = m != null && m.stage() != dev.missilesnukes.sim.FlightState.Stage.PAD ? ClientFlight.live(m) : null;

        g.fill(0, 0, width, height, 0x88000000);
        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, PANEL_EDGE);
        g.fill(left, top, left + W, top + H, PANEL);
        g.fill(left + 4, top + 4, left + W - 4, top + 17, 0xFF0B2A22);
        g.drawString(font, "LIGHT ICBM LINK", left + 8, top + 7, TEAL, false);
        String rdy = "RDY " + d.readyCount() + "/8";
        g.drawString(font, rdy, left + W - 8 - font.width(rdy), top + 7, TEAL, false);

        // selected slot: name + status box + progress bar (as on the remote's OLED display)
        g.drawString(font, "ICBM-0" + (sel + 1), left + 8, top + 24, 0xFFFFFFFF, false);
        int sc = statusColor(s);
        int bx1 = left + W - 100, bx2 = left + W - 6, by1 = top + 21, by2 = top + 33;
        g.fill(bx1, by1, bx2, by2, 0xFF0B3B30);
        g.fill(bx1, by1, bx2, by1 + 1, sc); g.fill(bx1, by2 - 1, bx2, by2, sc);
        g.fill(bx1, by1, bx1 + 1, by2, sc); g.fill(bx2 - 1, by1, bx2, by2, sc);
        g.drawCenteredString(font, statusText(s, m != null), (bx1 + bx2) / 2, by1 + 2, sc);
        double progress = live != null ? live.progress() : (s.status() == Status.READY ? 1.0 : 0.0);
        g.fill(left + 6, top + 37, left + W - 6, top + 43, 0xFF0B3B30);
        g.fill(left + 7, top + 38, left + 7 + (int) ((W - 14) * progress), top + 42, live != null ? ORANGE : (s.status() == Status.READY ? GREEN : GREY));

        g.drawString(font, "X", left + 8, top + 102, TEAL, false);
        g.drawString(font, "Y", left + 76, top + 102, TEAL, false);
        g.drawString(font, "Z", left + 128, top + 102, TEAL, false);

        super.render(g, mx, my, pt);

        drawInfo(g, s, m, live);
    }

    private static String shortStage(MissileEntity m) {
        return switch (m.stage()) {
            case PAD -> "PAD"; case IGNITION -> "IGNITION"; case ASCENT -> "ASCENT"; case CUTOFF -> "CUTOFF";
            case MIDCOURSE -> "COAST"; case TERMINAL -> "DESCENT"; case IMPACT -> "IMPACT";
        };
    }

    /** Small (0.75x) text line so the compact panel can show a full line of telemetry. */
    private void info(GuiGraphics g, String text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, font.plainSubstrByWidth(text, 276), 0, 0, color, false);   // never wider than the panel
        g.pose().popPose();
    }

    private void drawInfo(GuiGraphics g, Slot s, MissileEntity m, ClientFlight.Live live) {
        int x = left + 7, y = top + 175;
        if (live != null) {
            info(g, String.format("%s  ALT %.0f  %.0f b/s  FUEL %d%%", shortStage(m), live.altitude(), live.speedBps(), live.fuelPercent()), x, y, 0xFFE8FFF8);
            info(g, String.format("TGT %.0f  ETA %.1f s  APOGEE Y %.0f", live.distanceToTarget(), live.etaSeconds(), live.plannedApogee()), x, y + 10, 0xFF9FD8C8);
        } else if (m != null) {
            int[] t = parseTarget();
            if (t != null) {
                String key = m.spec().id + sel + ":" + t[0] + "," + t[1] + "," + t[2] + ":" + (int) m.getX() + "," + (int) m.getZ();
                if (!key.equals(planKey)) {
                    planKey = key;
                    plan = FlightSim.dryRun(m.spec(), m.getX(), m.getY() + m.half(), m.getZ(), t[0] + 0.5, t[1] + 1.0, t[2] + 0.5);
                }
                if (plan != null && plan.feasible) {
                    info(g, String.format("RANGE %.0f  FLIGHT %.1f s  BURN %.1f s", plan.range, plan.flightTicks / 20.0, plan.burnTicks / 20.0), x, y, 0xFFE8FFF8);
                    info(g, String.format("APOGEE +%.0f  PEAK %.0f b/s  SOLUTION OK", plan.apogeeAboveTarget, plan.maxSpeed * 20), x, y + 10, 0xFF9FD8C8);
                } else if (plan != null) {
                    info(g, plan.reason, x, y, 0xFFFF9090);
                    info(g, String.format("RANGE %.0f  MAX %.0f", plan.range, m.spec().maxRange), x, y + 10, 0xFFB08080);
                }
            } else {
                info(g, "Enter target X Y Z (block under impact)", x, y, 0xFF9FD8C8);
            }
        } else {
            info(g, "No missile linked to this slot.", x, y, 0xFF9FD8C8);
            info(g, "Right-click a missile with the remote.", x, y + 10, 0xFF7FA89A);
        }
    }

    // ------------------------------------------------------------------------------------------ pad button

    /** One of the eight numbered pad buttons: own status LED, selection frame, armed marker and a press animation. */
    private static final class PadButton extends Button {
        private final int slot;
        private final RemoteScreen screen;

        PadButton(int x, int y, int w, int h, int slot, RemoteScreen screen) {
            super(x, y, w, h, Component.literal(String.valueOf(slot + 1)), b -> screen.onPad(slot, Screen.hasShiftDown()), DEFAULT_NARRATION);
            this.slot = slot;
            this.screen = screen;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            RemoteData d = screen.data();
            Slot s = d.slot(slot);
            boolean pressed = screen.pressTicks[slot] > 0;
            boolean selected = screen.sel == slot;
            boolean live = s.missile().isPresent() && ClientFlight.find(s.missile().get()) != null;
            int off = pressed ? 1 : 0;                              // the key physically sinks while pressed
            int x1 = getX(), y1 = getY() + off, x2 = getX() + width, y2 = getY() + height;
            int led = statusColor(s);
            int body = y2 - (pressed ? 0 : 1);

            g.fill(x1, y2 - 1, x2, y2 + 1, 0xFF050B09);                                   // key well / shadow
            g.fill(x1, y1, x2, body, pressed ? 0xFF0C1714 : (isHoveredOrFocused() ? 0xFF1B3029 : PAD_BG));
            int edge = selected ? TEAL : 0xFF2A3A36;
            g.fill(x1, y1, x2, y1 + 1, edge); g.fill(x1, body - 1, x2, body, edge);
            g.fill(x1, y1, x1 + 1, body, edge); g.fill(x2 - 1, y1, x2, body, edge);

            g.fill(x1 + 5, y1 + 3, x2 - 5, y1 + 5, led);                                  // status LED strip
            if (s.status() == Status.FIRED && live && (System.currentTimeMillis() / 250) % 2 == 0) g.fill(x1 + 5, y1 + 3, x2 - 5, y1 + 5, 0xFFFFD0A0);
            g.drawCenteredString(Minecraft.getInstance().font, String.valueOf(slot + 1), x1 + width / 2, y1 + 7, selected ? 0xFFFFFFFF : 0xFFC8E8E0);
            g.pose().pushPose();
            g.pose().translate(x1 + width / 2f, y1 + 16, 0);
            g.pose().scale(0.5f, 0.5f, 1f);
            g.drawCenteredString(Minecraft.getInstance().font, statusText(s, live), 0, 0, led);
            g.pose().popPose();
            if (screen.armed[slot]) {                                                     // armed for a simultaneous launch
                g.fill(x1 + 2, y1 + 2, x1 + 6, y1 + 3, ORANGE); g.fill(x1 + 2, y1 + 2, x1 + 3, y1 + 6, ORANGE);
                g.fill(x2 - 6, y1 + 2, x2 - 2, y1 + 3, ORANGE); g.fill(x2 - 3, y1 + 2, x2 - 2, y1 + 6, ORANGE);
            }
        }
    }
}
