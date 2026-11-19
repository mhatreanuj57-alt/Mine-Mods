package net.runictitan;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Tiny server-tick scheduler used to animate the attacks over several ticks. */
public final class Scheduler {
	private static final List<Task> TASKS = new ArrayList<>();

	private static final class Task {
		int delay;
		final Runnable action;
		Task(int delay, Runnable action) { this.delay = delay; this.action = action; }
	}

	private Scheduler() {}

	public static void init() {
		MinecraftForge.EVENT_BUS.addListener(Scheduler::onServerTick);
		MinecraftForge.EVENT_BUS.addListener(Scheduler::onServerStopping);
	}

	public static void later(int delay, Runnable action) {
		TASKS.add(new Task(Math.max(0, delay), action));
	}

	private static void onServerStopping(ServerStoppingEvent event) {
		TASKS.clear();
	}

	private static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || TASKS.isEmpty()) return;
		List<Task> due = new ArrayList<>();
		for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
			Task t = it.next();
			if (t.delay-- <= 0) {
				due.add(t);
				it.remove();
			}
		}
		for (Task t : due) {
			try {
				t.action.run();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
