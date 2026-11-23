package dev.missilesnukes.common;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side configuration (world/serverconfig/missilesnukes-server.toml). */
public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue CRATER_RADIUS = B
        .comment("Horizontal radius of the crater carved by a warhead, in blocks.")
        .defineInRange("craterRadius", 28.0, 4.0, 96.0);
    public static final ModConfigSpec.IntValue CRATER_BLOCKS_PER_TICK = B
        .comment("How many blocks the crater may inspect per server tick. Lower = smoother but slower crater formation.")
        .defineInRange("craterBlocksPerTick", 6000, 500, 200000);
    public static final ModConfigSpec.DoubleValue CRATER_MAX_RESISTANCE = B
        .comment("Blast resistance that a block can have and still be destroyed at the very centre of the crater.",
                 "Falls off towards the rim. Obsidian is 1200, bedrock is never destroyed.")
        .defineInRange("craterMaxBlastResistance", 1500.0, 0.0, 3600000.0);
    public static final ModConfigSpec.BooleanValue BLOCK_DAMAGE = B
        .comment("If false, warheads and falling stages never destroy blocks (effects and damage still happen).")
        .define("blockDamage", true);
    public static final ModConfigSpec.BooleanValue RESPECT_MOB_GRIEFING = B
        .comment("If true, the mobGriefing game rule also disables crater destruction.")
        .define("respectMobGriefingRule", false);
    public static final ModConfigSpec.DoubleValue SHOCKWAVE_MAX_DAMAGE = B
        .comment("Damage dealt by the shockwave at the point of impact. It falls off with distance.")
        .defineInRange("shockwaveMaxDamage", 150.0, 0.0, 10000.0);
    public static final ModConfigSpec.DoubleValue SHOCKWAVE_RADIUS_FACTOR = B
        .comment("Shockwave reach as a multiple of the crater radius.")
        .defineInRange("shockwaveRadiusFactor", 3.2, 1.0, 10.0);
    public static final ModConfigSpec.BooleanValue DEBRIS_BLOCK_DAMAGE = B
        .comment("If true, the detached engine section and spent body explode with block damage when they hit the ground.")
        .define("debrisBlockDamage", false);
    public static final ModConfigSpec.BooleanValue CHUNK_LOADING = B
        .comment("Keep the chunks around a flying missile (and around its flight path ahead) loaded so it keeps simulating",
                 "when no player is nearby. Disabling this means missiles only fly while players are close.")
        .define("chunkLoading", true);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}
