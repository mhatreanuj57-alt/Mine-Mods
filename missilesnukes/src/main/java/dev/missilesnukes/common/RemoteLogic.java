package dev.missilesnukes.common;

import dev.missilesnukes.common.RemoteData.Slot;
import dev.missilesnukes.common.RemoteData.Status;
import dev.missilesnukes.net.RemoteActionPayload;
import dev.missilesnukes.sim.FlightSim;
import dev.missilesnukes.sim.FlightState.Stage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything the launch remote does. Runs on the server only: the client sends actions, the server validates them,
 * changes the remote's data component (which vanilla syncs back to the client) and drives the missiles.
 */
public final class RemoteLogic {
    private static final int COORD_LIMIT = 30_000_000;

    private RemoteLogic() {}

    private static ItemStack heldRemote(ServerPlayer p) {
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(h);
            if (s.is(ModItems.LAUNCH_REMOTE.get())) return s;
        }
        return ItemStack.EMPTY;
    }

    private static MissileEntity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e instanceof MissileEntity m && !m.isRemoved()) return m;
        }
        return null;
    }

    private static RemoteData data(ItemStack stack) { return stack.getOrDefault(ModComponents.REMOTE.get(), RemoteData.EMPTY); }
    private static void put(ItemStack stack, RemoteData d) { stack.set(ModComponents.REMOTE.get(), d); }
    private static void say(ServerPlayer p, String key, Object... args) { p.displayClientMessage(Component.translatable(key, args), true); }

    // ------------------------------------------------------------------------------------------ actions

    public static void handle(ServerPlayer player, RemoteActionPayload a) {
        ItemStack stack = heldRemote(player);
        if (stack.isEmpty()) { say(player, "message.missilesnukes.no_remote"); return; }
        if (a.slot() < 0 || a.slot() >= RemoteData.SLOT_COUNT) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        RemoteData d = data(stack);
        int slot = a.slot();

        switch (a.action()) {
            case RemoteActionPayload.SELECT -> put(stack, d.withSelected(slot));
            case RemoteActionPayload.SET_TARGET -> {
                if (Math.abs(a.x()) > COORD_LIMIT || Math.abs(a.z()) > COORD_LIMIT || a.y() < -2048 || a.y() > 20000) return;
                Slot s = d.slot(slot).withTarget(a.x(), a.y(), a.z(), false);
                put(stack, d.withSlot(slot, evaluate(server, s)).withSelected(slot));
            }
            case RemoteActionPayload.LAUNCH -> {
                Slot s = refresh(server, d.slot(slot));
                String err = launch(server, s);
                if (err == null) {
                    say(player, "message.missilesnukes.launched", slot + 1);
                    put(stack, d.withSlot(slot, s.withStatus(Status.FIRED)).withSelected(slot));
                } else {
                    say(player, "message.missilesnukes.not_ready", slot + 1, err);
                    put(stack, d.withSlot(slot, s).withSelected(slot));
                }
            }
            case RemoteActionPayload.LAUNCH_ALL -> {
                int fired = 0;
                RemoteData nd = d;
                for (int i = 0; i < RemoteData.SLOT_COUNT; i++) {
                    Slot s = refresh(server, nd.slot(i));
                    if (s.status() != Status.READY) { nd = nd.withSlot(i, s); continue; }
                    if (launch(server, s) == null) { fired++; s = s.withStatus(Status.FIRED); }
                    nd = nd.withSlot(i, s);
                }
                put(stack, nd);
                say(player, "message.missilesnukes.launch_all", fired);
            }
            case RemoteActionPayload.UNLINK -> put(stack, d.withSlot(slot, d.slot(slot).unlinked()).withSelected(slot));
            case RemoteActionPayload.LINK_NEAREST -> {
                MissileEntity best = null; double bestD = Double.MAX_VALUE;
                List<MissileEntity> near = player.serverLevel().getEntitiesOfClass(MissileEntity.class, player.getBoundingBox().inflate(32.0),
                    m -> m.fs.stage == Stage.PAD && !isLinked(d, m.getUUID()));
                for (MissileEntity m : near) { double dd = m.distanceToSqr(player); if (dd < bestD) { bestD = dd; best = m; } }
                if (best == null) { say(player, "message.missilesnukes.not_ready", slot + 1, "no free missile within 32 blocks"); return; }
                link(player, stack, d, slot, best);
            }
            case RemoteActionPayload.TOGGLE_FX -> {
                Optional<UUID> id = d.slot(slot).missile();
                MissileEntity m = id.isPresent() ? find(server, id.get()) : null;
                if (m != null) m.setThrusterFx(!m.thrusterFx());
            }
            default -> { }
        }
    }

    /** Right-clicking a missile with the remote links it to the currently selected slot. */
    public static void linkFromClick(ServerPlayer player, ItemStack stack, MissileEntity missile) {
        RemoteData d = data(stack);
        link(player, stack, d, d.selected(), missile);
    }

    private static void link(ServerPlayer player, ItemStack stack, RemoteData d, int slot, MissileEntity missile) {
        if (missile.fs.stage != Stage.PAD) { say(player, "message.missilesnukes.not_ready", slot + 1, "missile already launched"); return; }
        RemoteData nd = d;
        for (int i = 0; i < RemoteData.SLOT_COUNT; i++) {          // a missile belongs to at most one slot
            if (i != slot && nd.slot(i).missile().map(missile.getUUID()::equals).orElse(false)) nd = nd.withSlot(i, nd.slot(i).unlinked());
        }
        MinecraftServer server = player.getServer();
        Slot s = nd.slot(slot).withMissile(missile.getUUID(), Status.LINKED);
        nd = nd.withSlot(slot, server == null ? s : evaluate(server, s)).withSelected(slot);
        put(stack, nd);
        say(player, "message.missilesnukes.linked", slot + 1);
    }

    private static boolean isLinked(RemoteData d, UUID id) {
        for (Slot s : d.slots()) if (s.missile().map(id::equals).orElse(false)) return true;
        return false;
    }

    private static String launch(MinecraftServer server, Slot s) {
        if (s.missile().isEmpty()) return "no missile linked";
        MissileEntity m = find(server, s.missile().get());
        if (m == null) return "no signal";
        if (!s.hasTarget()) return "no target set";
        return m.tryLaunch(s.x(), s.y(), s.z());
    }

    // ------------------------------------------------------------------------------------------ status

    /** Cheap status refresh (no flight planning): keeps the slot LEDs in step with the real missiles. */
    private static Slot refresh(MinecraftServer server, Slot s) {
        if (s.missile().isEmpty()) return s.status() == Status.EMPTY ? s : s.withStatus(Status.EMPTY);
        MissileEntity m = find(server, s.missile().get());
        if (m == null) {
            int st = s.status();
            return s.withStatus(st == Status.FIRED || st == Status.DONE ? Status.DONE : Status.NO_SIGNAL);
        }
        if (m.fs.stage != Stage.PAD) return s.withStatus(Status.FIRED);
        if (!s.hasTarget()) return s.withStatus(Status.LINKED);
        return s.withStatus(s.feasible() ? Status.READY : Status.LINKED);
    }

    /** Full evaluation: also runs a complete simulated flight to check the target is reachable. */
    private static Slot evaluate(MinecraftServer server, Slot s) {
        Slot r = refresh(server, s);
        if (r.missile().isEmpty() || !r.hasTarget()) return r.withFeasible(false);
        MissileEntity m = find(server, r.missile().get());
        if (m == null || m.fs.stage != Stage.PAD) return r;
        FlightSim.Plan plan = m.planFor(r.x(), r.y(), r.z());
        return refresh(server, r.withFeasible(plan != null && plan.feasible));
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        ItemStack stack = heldRemote(player);
        if (stack.isEmpty() || player.getServer() == null) return;
        RemoteData d = data(stack), nd = d;
        for (int i = 0; i < RemoteData.SLOT_COUNT; i++) {
            Slot s = refresh(player.getServer(), d.slot(i));
            if (!s.equals(d.slot(i))) nd = nd.withSlot(i, s);
        }
        if (!nd.equals(d)) put(stack, nd);
    }
}
