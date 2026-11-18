package com.transportgolem.client;

/** Plain data holder (no client classes) so common code can trigger the camera shake safely. */
public final class ClientShake {
    public static long startTick = -1000L;
    public static double x, y, z;

    public static void trigger(double px, double py, double pz, long gameTime) {
        x = px;
        y = py;
        z = pz;
        startTick = gameTime;
    }

    private ClientShake() {}
}
