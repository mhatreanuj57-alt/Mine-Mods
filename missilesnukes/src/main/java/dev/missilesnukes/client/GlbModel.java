package dev.missilesnukes.client;

import dev.missilesnukes.glb.Gltf;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** A loaded .glb plus helpers that turn its primitives into ready-to-render draw calls. */
public final class GlbModel {
    /** One material-uniform batch of triangles. */
    public static final class Draw {
        final Gltf.Primitive prim;
        final int node;                 // node whose animated world matrix applies, or -1 for none
        final ResourceLocation texture;
        final int r, g, b, a;
        final boolean blend, bright;
        final int key;                  // remote keycap index (0-7) or -1

        Draw(Gltf.Primitive prim, int node, ResourceLocation texture, float[] color, boolean blend, boolean bright, int key) {
            this.prim = prim; this.node = node; this.texture = texture; this.blend = blend; this.bright = bright; this.key = key;
            this.r = Math.round(Math.max(0, Math.min(1, color[0])) * 255); this.g = Math.round(Math.max(0, Math.min(1, color[1])) * 255);
            this.b = Math.round(Math.max(0, Math.min(1, color[2])) * 255); this.a = Math.round(Math.max(0, Math.min(1, color[3])) * 255);
        }

        RenderType renderType() {
            return blend ? RenderType.entityTranslucent(texture) : RenderType.entityCutoutNoCull(texture);
        }
    }

    public final String key;
    public final Gltf gltf;
    public final float[] min = {1e30f, 1e30f, 1e30f}, max = {-1e30f, -1e30f, -1e30f};

    public GlbModel(String key, String fileName) {
        this.key = key;
        this.gltf = GlbAssets.load(fileName);
        float[][] world = gltf.worldMatrices(null, 0f, false);   // nodes may carry offsets (the heavy missile's stages do)
        float[] o = new float[3];
        for (int ni = 0; ni < gltf.nodes.size(); ni++) {
            Gltf.Node n = gltf.nodes.get(ni);
            if (n.mesh < 0) continue;
            for (Gltf.Primitive p : gltf.meshes.get(n.mesh)) {
                for (int i = 0; i < p.pos.length; i += 3) {
                    dev.missilesnukes.glb.Mat4.transformPoint(world[ni], p.pos[i], p.pos[i + 1], p.pos[i + 2], o);
                    for (int c = 0; c < 3; c++) { min[c] = Math.min(min[c], o[c]); max[c] = Math.max(max[c], o[c]); }
                }
            }
        }
    }

    public float centerX() { return (min[0] + max[0]) * 0.5f; }
    public float centerY() { return (min[1] + max[1]) * 0.5f; }
    public float centerZ() { return (min[2] + max[2]) * 0.5f; }
    public float maxDimension() { return Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2])); }

    public Draw makeDraw(Gltf.Primitive prim, int node, int keyIndex) {
        Gltf.Material m = prim.material >= 0 ? gltf.materials.get(prim.material) : new Gltf.Material();
        ResourceLocation tex = m.baseImage >= 0 ? GlbAssets.texture(key, gltf, m.baseImage) : GlbAssets.white();
        float maxEmissive = Math.max(m.emissive[0], Math.max(m.emissive[1], m.emissive[2]));
        return new Draw(prim, node, tex, m.baseColor, m.blend, maxEmissive >= 0.5f, keyIndex);
    }

    /** All meshes of the given nodes (animated nodes keep their node index so the world matrix can be applied). */
    public List<Draw> drawsForNodes(Collection<Integer> nodes) {
        List<Draw> out = new ArrayList<>();
        for (int ni : nodes) {
            Gltf.Node n = gltf.nodes.get(ni);
            if (n.mesh < 0) continue;
            for (Gltf.Primitive p : gltf.meshes.get(n.mesh)) out.add(makeDraw(p, ni, -1));
        }
        return out;
    }

    public List<Draw> drawsForAllNodes() {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < gltf.nodes.size(); i++) all.add(i);
        return drawsForNodes(all);
    }

    public List<Draw> drawsForPrimitives(List<Gltf.Primitive> prims) {
        List<Draw> out = new ArrayList<>();
        for (Gltf.Primitive p : prims) out.add(makeDraw(p, -1, -1));
        return out;
    }

    /** Splits a primitive made of identical-size vertex groups (quads / boxes) into one primitive per group. */
    public static List<Gltf.Primitive> splitGroups(Gltf.Primitive p, int vertsPerGroup) {
        int groups = p.vertexCount() / vertsPerGroup;
        List<int[]> idx = new ArrayList<>();
        int[] counts = new int[groups];
        for (int i = 0; i < p.idx.length; i++) counts[p.idx[i] / vertsPerGroup]++;
        int[][] arr = new int[groups][];
        for (int g = 0; g < groups; g++) arr[g] = new int[counts[g]];
        int[] fill = new int[groups];
        for (int i = 0; i < p.idx.length; i++) { int g = p.idx[i] / vertsPerGroup; arr[g][fill[g]++] = p.idx[i]; }
        List<Gltf.Primitive> out = new ArrayList<>();
        for (int g = 0; g < groups; g++) {
            Gltf.Primitive q = new Gltf.Primitive();
            q.pos = p.pos; q.nor = p.nor; q.uv = p.uv; q.material = p.material; q.idx = arr[g];
            out.add(q);
        }
        return out;
    }
}
