package dev.missilesnukes.client;

/** Client-side state for the remote's 3D model: which keycaps are currently pressed in. */
public final class RemoteVisual {
    public static final int PRESS_TICKS = 6;
    private static final long[] PRESSED_UNTIL = new long[8];

    private RemoteVisual() {}

    public static void press(int slot, long gameTime) {
        if (slot >= 0 && slot < 8) PRESSED_UNTIL[slot] = gameTime + PRESS_TICKS;
    }

    public static int mask(long gameTime) {
        int m = 0;
        for (int i = 0; i < 8; i++) if (PRESSED_UNTIL[i] > gameTime) m |= 1 << i;
        return m;
    }
}
