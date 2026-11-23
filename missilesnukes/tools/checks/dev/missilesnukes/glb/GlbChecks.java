package dev.missilesnukes.glb;

import java.nio.file.*;
import java.util.*;

/** Self-checking program: java -cp out:gson.jar dev.missilesnukes.glb.GlbChecks <packDir> */
public class GlbChecks {
    static int fails;
    static void check(boolean ok, String m) { if (!ok) { fails++; System.out.println("FAIL: " + m); } }
    /** Finds the file by name anywhere below dir, so it works on the original pack and on the mod\'s flat assets folder. */
    static Gltf load(String dir, String f) throws Exception {
        String name = Paths.get(f).getFileName().toString();
        try (var w = Files.walk(Paths.get(dir))) {
            Path p = w.filter(x -> x.getFileName().toString().equals(name)).findFirst().orElseThrow(() -> new java.io.FileNotFoundException(name));
            return Gltf.parse(Files.readAllBytes(p));
        }
    }
    static double area(List<Gltf.Primitive> ps) {
        double a = 0;
        for (Gltf.Primitive p : ps) for (int t = 0; t < p.triangleCount(); t++) {
            int i0 = p.idx[t * 3] * 3, i1 = p.idx[t * 3 + 1] * 3, i2 = p.idx[t * 3 + 2] * 3;
            double ax = p.pos[i1] - p.pos[i0], ay = p.pos[i1 + 1] - p.pos[i0 + 1], az = p.pos[i1 + 2] - p.pos[i0 + 2];
            double bx = p.pos[i2] - p.pos[i0], by = p.pos[i2 + 1] - p.pos[i0 + 1], bz = p.pos[i2 + 2] - p.pos[i0 + 2];
            double cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
            a += 0.5 * Math.sqrt(cx * cx + cy * cy + cz * cz);
        }
        return a;
    }
    static int tris(Gltf g) { int n = 0; for (var m : g.meshes) for (var p : m) n += p.triangleCount(); return n; }

    public static void main(String[] a) throws Exception {
        String d = a[0];
        String M = "light_icbm_complete_missile/";
        Gltf missile = load(d, M + "light_icbm_missile_without_flame.glb");
        Gltf flame = load(d, M + "light_icbm_complete_assembled_with_flame.glb");
        Gltf expl = load(d, M + "icbm_explosion_vfx.glb");
        Gltf remote = load(d, "light_icbm_launch_remote_resource_pack/light_icbm_launch_remote.glb");
        Gltf warhead = load(d, "light_icbm_warhead_resoucre_pack/light_icbm_warhead.glb");
        Gltf body = load(d, "light_icbm_central_body_resource_pack/light_icbm_central_body.glb");
        Gltf engine = load(d, "light_icbm_engine_mount_resource_pack/light_icbm_engine_mount.glb");

        // 1. counts match the independent Python inspection
        check(tris(missile) == 13176, "missile triangles " + tris(missile));
        check(missile.materials.size() == 4 && missile.images.size() == 12, "missile materials/images");
        check(flame.animations.size() == 1 && flame.animations.get(0).channels.size() == 217, "flame channels");
        check(Math.abs(flame.animations.get(0).duration - 1.6f) < 1e-4, "flame duration");
        check(expl.animations.get(0).channels.size() == 212 && Math.abs(expl.animations.get(0).duration - 6f) < 1e-4, "explosion anim");
        check(remote.materials.size() == 10, "remote materials");
        System.out.printf("1. parsed: missile %d tris, flame clip %.1fs/%d ch, explosion clip %.1fs/%d ch, remote %d materials%n",
            tris(missile), flame.animations.get(0).duration, flame.animations.get(0).channels.size(),
            expl.animations.get(0).duration, expl.animations.get(0).channels.size(), remote.materials.size());
        for (Gltf g : List.of(warhead, body, engine, missile, remote, flame, expl)) for (var im : g.images) check(im.length > 8 && im[1] == 'P' && im[2] == 'N' && im[3] == 'G', "embedded image is not PNG");
        System.out.println("   all embedded images are PNG");

        // 2. splitter: exact area conservation, correct bounds, centroids
        List<Gltf.Primitive> all = missile.meshes.get(0);
        double total = area(all);
        double[] planes = {-1.495, 0.785};
        List<MeshSplitter.Piece> pcs = MeshSplitter.split(all, planes);
        double sum = 0; for (var p : pcs) sum += p.area;
        check(Math.abs(sum - total) / total < 1e-5, "area not conserved: " + sum + " vs " + total);
        String[] names = {"engine mount", "central body", "warhead"};
        for (int i = 0; i < 3; i++) {
            var p = pcs.get(i);
            double lo = i == 0 ? -2.0 : planes[i - 1], hi = i == 2 ? 2.0 : planes[i];
            check(p.min[1] >= lo - 1e-4 && p.max[1] <= hi + 1e-4, names[i] + " exceeds its slab");
            System.out.printf("2. %-12s tris %5d  area %.4f  y %.3f..%.3f  centroid (%.4f, %.4f, %.4f)%n", names[i], p.triangles, p.area, p.min[1], p.max[1], p.centroid[0], p.centroid[1], p.centroid[2]);
        }
        System.out.printf("   total area %.5f, pieces sum %.5f (diff %.2e)%n", total, sum, Math.abs(sum - total));
        // materials preserved per piece
        Set<Integer> mats = new TreeSet<>(); for (var p : pcs) for (var pr : p.primitives) mats.add(pr.material);
        check(mats.equals(Set.of(0, 1, 2, 3)), "materials lost in split");

        // 3. animation: finite, flame hangs below the nozzle, explosion grows
        int troot = flame.findNode("Voxel_Rocket_Thruster_Root"); check(troot >= 0, "thruster root missing");
        List<Integer> sub = flame.subtree(troot);
        float miny = 1e9f, maxy = -1e9f; boolean finite = true;
        for (float t : new float[]{0f, 0.4f, 0.8f, 1.2f, 1.59f}) {
            float[][] w = flame.worldMatrices(flame.animations.get(0), t, true);
            for (int ni : sub) { var nd = flame.nodes.get(ni); if (nd.mesh < 0) continue;
                for (var pr : flame.meshes.get(nd.mesh)) { float[] o = new float[3];
                    for (int v = 0; v < pr.vertexCount(); v += 7) { Mat4.transformPoint(w[ni], pr.pos[v*3], pr.pos[v*3+1], pr.pos[v*3+2], o);
                        for (float f : o) if (Float.isNaN(f) || Float.isInfinite(f)) finite = false; miny = Math.min(miny, o[1]); maxy = Math.max(maxy, o[1]); } } }
        }
        check(finite, "NaN in flame animation");
        check(maxy < -1.5f && miny < -4.5f, "flame not attached below nozzle: " + miny + ".." + maxy);
        System.out.printf("3. flame (all clip times) spans y %.2f .. %.2f in missile space (nozzle at -2.0), finite=%b%n", miny, maxy, finite);
        double e0 = extent(expl, 0f), e1 = extent(expl, 1.5f), e2 = extent(expl, 4f);
        check(e1 > e0 * 2, "explosion does not expand");
        System.out.printf("   explosion bounding radius: t=0 -> %.2f, t=1.5 -> %.2f, t=4 -> %.2f blocks%n", e0, e1, e2);

        heavy(d);

        System.out.println(fails == 0 ? "\nALL GLB CHECKS PASSED" : "\n" + fails + " GLB CHECK(S) FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }

    /** The Heavy ICBM: three separate stage nodes placed by node offsets, plus an embedded animated thruster. */
    static void heavy(String d) throws Exception {
        Gltf h = load(d, "heavy_icbm_complete.glb");
        var spec = dev.missilesnukes.sim.MissileSpec.HEAVY;
        float[][] w = h.worldMatrices(null, 0f, false);
        String[] stage = {"Heavy_ICBM_Engine", "Heavy_ICBM_Central_Body", "Heavy_ICBM_Warhead"};
        String[] comp = {"heavy_icbm_engine.glb", "heavy_icbm_central_body.glb", "heavy_icbm_warhead.glb"};
        double[][] want = {spec.centroidEngine, spec.centroidBody, spec.centroidWarhead};
        double top = -1e9, bottom = 1e9; float[] o = new float[3];
        for (int i = 0; i < 3; i++) {
            int root = h.findNode(stage[i]); check(root >= 0, "missing node " + stage[i]);
            double area = 0, cy = 0, minY = 1e9, maxY = -1e9; int verts = 0;
            for (int ni : h.subtree(root)) {
                var nd = h.nodes.get(ni); if (nd.mesh < 0) continue;
                for (var p : h.meshes.get(nd.mesh)) {
                    verts += p.vertexCount();
                    for (int t = 0; t < p.triangleCount(); t++) {
                        double[][] v = new double[3][3];
                        for (int k = 0; k < 3; k++) { int vi = p.idx[t * 3 + k];
                            Mat4.transformPoint(w[ni], p.pos[vi * 3], p.pos[vi * 3 + 1], p.pos[vi * 3 + 2], o);
                            v[k][0] = o[0]; v[k][1] = o[1]; v[k][2] = o[2]; minY = Math.min(minY, o[1]); maxY = Math.max(maxY, o[1]); }
                        double ax = v[1][0]-v[0][0], ay = v[1][1]-v[0][1], az = v[1][2]-v[0][2], bx = v[2][0]-v[0][0], by = v[2][1]-v[0][1], bz = v[2][2]-v[0][2];
                        double cx = ay*bz-az*by, cyy = az*bx-ax*bz, cz = ax*by-ay*bx; double a = 0.5*Math.sqrt(cx*cx+cyy*cyy+cz*cz);
                        area += a; cy += a * (v[0][1] + v[1][1] + v[2][1]) / 3.0;
                    }
                }
            }
            cy /= area; top = Math.max(top, maxY); bottom = Math.min(bottom, minY);
            Gltf c = load(d, comp[i]); int cv = 0; for (var m : c.meshes) for (var p : m) cv += p.vertexCount();
            check(cv == verts, stage[i] + " vertex count differs from standalone file: " + verts + " vs " + cv);
            check(Math.abs(cy - want[i][1]) < 2e-3, stage[i] + " centroid y " + cy + " != MissileSpec.HEAVY " + want[i][1]);
            System.out.printf("4. heavy %-24s y %.3f..%.3f  centroid y %.4f (spec %.4f)  %d verts = standalone file%n", stage[i], minY, maxY, cy, want[i][1], verts);
        }
        check(Math.abs(top - spec.halfHeight) < 2e-3 && Math.abs(bottom + spec.halfHeight) < 2e-3, "heavy model is not symmetric about 0 / halfHeight mismatch: " + bottom + ".." + top);
        System.out.printf("   assembled height %.3f (halfHeight in spec %.3f, symmetric about the model origin)%n", top - bottom, spec.halfHeight);

        int fr = h.findNode("Heavy_ICBM_Thruster_VFX"); check(fr >= 0, "thruster root missing");
        check(h.animations.size() == 2 && h.animations.get(0).name.equals("Thruster_Active_Loop") && h.animations.get(1).name.equals("Thruster_Off_Idle"), "heavy animations");
        float miny = 1e9f, maxy = -1e9f; boolean finite = true;
        for (float t : new float[]{0f, 0.4f, 0.8f, 1.2f, 1.59f}) {
            float[][] aw = h.worldMatrices(h.animations.get(0), t, true);
            for (int ni : h.subtree(fr)) { var nd = h.nodes.get(ni); if (nd.mesh < 0) continue;
                for (var p : h.meshes.get(nd.mesh)) for (int v = 0; v < p.vertexCount(); v += 5) {
                    Mat4.transformPoint(aw[ni], p.pos[v*3], p.pos[v*3+1], p.pos[v*3+2], o);
                    for (float f : o) if (Float.isNaN(f) || Float.isInfinite(f)) finite = false; miny = Math.min(miny, o[1]); maxy = Math.max(maxy, o[1]); } }
        }
        check(finite && maxy < -2.9f && miny < -6f, "heavy flame misplaced: " + miny + ".." + maxy);
        System.out.printf("   heavy flame spans y %.2f .. %.2f (nozzle at %.3f), finite=%b%n", miny, maxy, -spec.halfHeight, finite);
    }

    static double extent(Gltf g, float t) {
        float[][] w = g.worldMatrices(g.animations.get(0), t, false); double r = 0; float[] o = new float[3];
        for (int ni = 0; ni < g.nodes.size(); ni++) { var nd = g.nodes.get(ni); if (nd.mesh < 0) continue;
            for (var pr : g.meshes.get(nd.mesh)) for (int v = 0; v < pr.vertexCount(); v += 3) {
                Mat4.transformPoint(w[ni], pr.pos[v*3], pr.pos[v*3+1], pr.pos[v*3+2], o); r = Math.max(r, Math.sqrt(o[0]*o[0]+o[1]*o[1]+o[2]*o[2])); } }
        return r;
    }
}
