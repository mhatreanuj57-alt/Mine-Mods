package com.transportgolem;

/** Tunable constants. Model units are the GLB's native units (60 units = 1 block, so the golem is 8 blocks tall). */
public final class GolemConfig {
    private GolemConfig() {}

    // Hitbox (blocks)
    public static final float WIDTH = 4.2F;
    public static final float HEIGHT = 8.0F;

    // Animation timing (ticks @ 20 TPS). ground_smash is 1.6 s, hands touch the ground at 0.9 s (keyframe 4).
    public static final int SMASH_TOTAL_TICKS = 32;
    public static final int SMASH_IMPACT_TICK = 18;
    public static final int SMASH_COOLDOWN_TICKS = 30;
    public static final int JUMP_ANIMATION_TICKS = 100;
    public static final int JUMP_ASCENT_TICKS = 20;
    public static final double JUMP_HEIGHT = 20.0D;
    public static final double JUMP_VELOCITY = 2.0D;

    /** Blocks travelled per full walk cycle (two steps). Raise it if the feet look like they slide forwards. */
    public static final float WALK_STRIDE_BLOCKS = 4.0F;

    // Smash combat
    public static final double SMASH_RADIUS = 10.0;
    public static final float SMASH_DAMAGE = 2000.0F;

    // Seat anchors in Torso_Pivot space (GLB units): top-centre of each cushion, pulled forward of the backrest.
    // 0 = driver (centre), 1 = left, 2 = right. Taken from Driver_Seat_Cushion / Passenger_Seat_*_Cushion bounds.
    public static final float[][] SEATS = {
            {0.0F, 202.5F, 79.5F},
            {-91.5F, 63.0F, 171.0F},
            {91.5F, 63.0F, 171.0F}
    };
    /** Distance from the seat surface down to a sitting player's feet origin (blocks). */
    public static final double SIT_DROP = 0.59;
}
