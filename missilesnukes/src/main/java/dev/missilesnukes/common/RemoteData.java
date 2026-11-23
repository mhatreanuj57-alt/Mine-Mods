package dev.missilesnukes.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contents of a launch remote: exactly eight independent launch slots, each with its own linked missile, target and
 * status, plus the slot currently selected in the control panel. Immutable; stored as a data component on the stack.
 */
public record RemoteData(int selected, List<Slot> slots) {
    public static final int SLOT_COUNT = 8;

    public static final class Status {
        public static final int EMPTY = 0, LINKED = 1, READY = 2, FIRED = 3, DONE = 4, NO_SIGNAL = 5;
        private Status() {}
    }

    /** One launch slot. status is one of Status.*; feasible says whether the stored target can be reached. */
    public record Slot(Optional<UUID> missile, boolean hasTarget, int x, int y, int z, int status, boolean feasible) {
        public static final Slot EMPTY = new Slot(Optional.empty(), false, 0, 64, 0, Status.EMPTY, false);

        public static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("missile").forGetter(Slot::missile),
            Codec.BOOL.optionalFieldOf("has_target", false).forGetter(Slot::hasTarget),
            Codec.INT.optionalFieldOf("x", 0).forGetter(Slot::x),
            Codec.INT.optionalFieldOf("y", 64).forGetter(Slot::y),
            Codec.INT.optionalFieldOf("z", 0).forGetter(Slot::z),
            Codec.INT.optionalFieldOf("status", Status.EMPTY).forGetter(Slot::status),
            Codec.BOOL.optionalFieldOf("feasible", false).forGetter(Slot::feasible)
        ).apply(i, Slot::new));

        public Slot withTarget(int x, int y, int z, boolean feasible) { return new Slot(missile, true, x, y, z, status, feasible); }
        public Slot withStatus(int s) { return new Slot(missile, hasTarget, x, y, z, s, feasible); }
        public Slot withFeasible(boolean f) { return new Slot(missile, hasTarget, x, y, z, status, f); }
        public Slot withMissile(UUID id, int s) { return new Slot(Optional.of(id), hasTarget, x, y, z, s, feasible); }
        public Slot unlinked() { return new Slot(Optional.empty(), hasTarget, x, y, z, Status.EMPTY, false); }
    }

    public RemoteData {
        List<Slot> fixed = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) fixed.add(i < slots.size() ? slots.get(i) : Slot.EMPTY);
        slots = List.copyOf(fixed);
        selected = Math.max(0, Math.min(SLOT_COUNT - 1, selected));
    }

    public static final RemoteData EMPTY = new RemoteData(0, List.of());

    public static final Codec<RemoteData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.optionalFieldOf("selected", 0).forGetter(RemoteData::selected),
        Slot.CODEC.listOf().optionalFieldOf("slots", List.of()).forGetter(RemoteData::slots)
    ).apply(i, RemoteData::new));

    public static final StreamCodec<ByteBuf, RemoteData> STREAM_CODEC = StreamCodec.of(RemoteData::write, RemoteData::read);

    private static void write(ByteBuf raw, RemoteData d) {
        FriendlyByteBuf buf = new FriendlyByteBuf(raw);
        buf.writeVarInt(d.selected);
        for (Slot s : d.slots) {
            buf.writeBoolean(s.missile.isPresent());
            s.missile.ifPresent(buf::writeUUID);
            buf.writeBoolean(s.hasTarget);
            buf.writeInt(s.x); buf.writeInt(s.y); buf.writeInt(s.z);
            buf.writeVarInt(s.status);
            buf.writeBoolean(s.feasible);
        }
    }

    private static RemoteData read(ByteBuf raw) {
        FriendlyByteBuf buf = new FriendlyByteBuf(raw);
        int sel = buf.readVarInt();
        List<Slot> list = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            Optional<UUID> id = buf.readBoolean() ? Optional.of(buf.readUUID()) : Optional.empty();
            boolean has = buf.readBoolean();
            int x = buf.readInt(), y = buf.readInt(), z = buf.readInt();
            int st = buf.readVarInt();
            boolean ok = buf.readBoolean();
            list.add(new Slot(id, has, x, y, z, st, ok));
        }
        return new RemoteData(sel, list);
    }

    public Slot slot(int i) { return slots.get(Math.max(0, Math.min(SLOT_COUNT - 1, i))); }

    public RemoteData withSlot(int i, Slot s) {
        List<Slot> copy = new ArrayList<>(slots);
        copy.set(i, s);
        return new RemoteData(selected, copy);
    }

    public RemoteData withSelected(int i) { return new RemoteData(i, slots); }

    public int readyCount() {
        int n = 0;
        for (Slot s : slots) if (s.status == Status.READY) n++;
        return n;
    }
}
