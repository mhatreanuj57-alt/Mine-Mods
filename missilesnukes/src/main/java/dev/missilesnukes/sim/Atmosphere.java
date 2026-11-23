package dev.missilesnukes.sim;

/** Exponential atmosphere scaled to Minecraft's sea level. Density is 1.0 at y=62 and thins with altitude. */
public final class Atmosphere {
    public static final double SEA_LEVEL_Y = 62.0;
    public static final double SCALE_HEIGHT = 220.0;

    private Atmosphere() {}

    public static double density(double y) {
        double h = Math.max(-64.0, y - SEA_LEVEL_Y);
        return Math.min(Math.exp(-h / SCALE_HEIGHT), 1.3);
    }
}
