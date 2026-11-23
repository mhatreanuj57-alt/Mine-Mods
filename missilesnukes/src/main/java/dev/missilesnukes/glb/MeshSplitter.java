package dev.missilesnukes.glb;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts triangle meshes with horizontal planes (y = const) into stacked pieces. Triangles that straddle a plane are
 * clipped exactly (positions, normals and UVs are interpolated), so the pieces together reproduce the original surface
 * with no gaps, overlaps or distortion. Pure Java.
 */
public final class MeshSplitter {
    private MeshSplitter() {}

    public static final class Piece {
        public final List<Gltf.Primitive> primitives = new ArrayList<>();
        public double area;
        public final double[] centroid = new double[3]; // area-weighted surface centroid
        public final double[] min = {1e30, 1e30, 1e30}, max = {-1e30, -1e30, -1e30};
        public int triangles;
    }

    /** planes must be ascending; returns planes.length + 1 pieces, bottom first. */
    public static List<Piece> split(List<Gltf.Primitive> source, double[] planes) {
        int pieces = planes.length + 1;
        List<Piece> out = new ArrayList<>();
        for (int i = 0; i < pieces; i++) out.add(new Piece());
        for (Gltf.Primitive prim : source) {
            List<float[]>[] buckets = new List[pieces];
            for (int i = 0; i < pieces; i++) buckets[i] = new ArrayList<>();
            for (int t = 0; t < prim.triangleCount(); t++) {
                float[][] tri = new float[3][];
                for (int k = 0; k < 3; k++) tri[k] = vertex(prim, prim.idx[t * 3 + k]);
                for (int piece = 0; piece < pieces; piece++) {
                    List<float[]> poly = new ArrayList<>(List.of(tri));
                    if (piece > 0) poly = clip(poly, planes[piece - 1], true);
                    if (piece < pieces - 1) poly = clip(poly, planes[piece], false);
                    for (int i = 1; i + 1 < poly.size(); i++) {
                        buckets[piece].add(poly.get(0));
                        buckets[piece].add(poly.get(i));
                        buckets[piece].add(poly.get(i + 1));
                    }
                }
            }
            for (int piece = 0; piece < pieces; piece++) {
                List<float[]> verts = buckets[piece];
                if (verts.isEmpty()) continue;
                Gltf.Primitive p = new Gltf.Primitive();
                int n = verts.size();
                p.pos = new float[n * 3]; p.nor = new float[n * 3]; p.uv = new float[n * 2]; p.idx = new int[n];
                p.material = prim.material;
                for (int i = 0; i < n; i++) {
                    float[] v = verts.get(i);
                    System.arraycopy(v, 0, p.pos, i * 3, 3);
                    System.arraycopy(v, 3, p.nor, i * 3, 3);
                    System.arraycopy(v, 6, p.uv, i * 2, 2);
                    p.idx[i] = i;
                }
                out.get(piece).primitives.add(p);
                account(out.get(piece), p);
            }
        }
        for (Piece pc : out) if (pc.area > 0) for (int i = 0; i < 3; i++) pc.centroid[i] /= pc.area;
        return out;
    }

    private static void account(Piece pc, Gltf.Primitive p) {
        for (int t = 0; t < p.triangleCount(); t++) {
            double[][] v = new double[3][3];
            for (int k = 0; k < 3; k++) for (int c = 0; c < 3; c++) {
                v[k][c] = p.pos[p.idx[t * 3 + k] * 3 + c];
                pc.min[c] = Math.min(pc.min[c], v[k][c]);
                pc.max[c] = Math.max(pc.max[c], v[k][c]);
            }
            double ax = v[1][0] - v[0][0], ay = v[1][1] - v[0][1], az = v[1][2] - v[0][2];
            double bx = v[2][0] - v[0][0], by = v[2][1] - v[0][1], bz = v[2][2] - v[0][2];
            double cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
            double a = 0.5 * Math.sqrt(cx * cx + cy * cy + cz * cz);
            pc.area += a; pc.triangles++;
            for (int c = 0; c < 3; c++) pc.centroid[c] += a * (v[0][c] + v[1][c] + v[2][c]) / 3.0;
        }
    }

    private static float[] vertex(Gltf.Primitive p, int i) {
        return new float[]{p.pos[i * 3], p.pos[i * 3 + 1], p.pos[i * 3 + 2], p.nor[i * 3], p.nor[i * 3 + 1], p.nor[i * 3 + 2], p.uv[i * 2], p.uv[i * 2 + 1]};
    }

    /** Sutherland-Hodgman against y >= plane (keepAbove) or y <= plane. */
    private static List<float[]> clip(List<float[]> poly, double plane, boolean keepAbove) {
        List<float[]> out = new ArrayList<>();
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            float[] a = poly.get(i), b = poly.get((i + 1) % n);
            boolean ain = keepAbove ? a[1] >= plane : a[1] <= plane;
            boolean bin = keepAbove ? b[1] >= plane : b[1] <= plane;
            if (ain) out.add(a);
            if (ain != bin) {
                float f = (float) ((plane - a[1]) / (b[1] - a[1]));
                float[] v = new float[8];
                for (int k = 0; k < 8; k++) v[k] = a[k] + (b[k] - a[k]) * f;
                v[1] = (float) plane;
                out.add(v);
            }
        }
        return out;
    }
}
