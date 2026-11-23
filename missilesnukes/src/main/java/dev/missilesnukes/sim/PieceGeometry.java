package dev.missilesnukes.sim;

/**
 * Geometry of the complete-missile model (light_icbm_missile_without_flame.glb), in model units (1 unit = 1 block).
 * The single mesh is cut at two planes into three stages; the centroids are the area-weighted centres of each piece
 * (used as the pivot for the detached piece). Values were measured from the real GLB by GlbChecks (MeshSplitter) and are re-verified there.
 */
public final class PieceGeometry {
    private PieceGeometry() {}

    public static final double MODEL_BOTTOM = -2.0;
    public static final double MODEL_TOP = 2.0;
    /** Plane between the engine mount (below) and the central body (above). */
    public static final double SEAM_ENGINE = -1.495;
    /** Plane between the central body (below) and the warhead (above). */
    public static final double SEAM_WARHEAD = 0.785;

    public static final double[] CENTROID_ENGINE = {0.0, -1.6828, 0.0};
    public static final double[] CENTROID_BODY = {0.0, -0.4550, 0.0};
    public static final double[] CENTROID_WARHEAD = {0.0, 1.2139, 0.0};
}
