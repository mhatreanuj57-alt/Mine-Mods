package dev.missilesnukes.glb;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Small, dependency-light glTF 2.0 binary (.glb) reader: node hierarchy, triangle meshes (POSITION / NORMAL /
 * TEXCOORD_0), materials (base-colour texture, emissive, alpha mode), embedded PNG images and TRS node animations
 * (LINEAR and STEP). It does not depend on Minecraft, so it can be unit tested.
 */
public final class Gltf {
    public static final int PATH_TRANSLATION = 0, PATH_ROTATION = 1, PATH_SCALE = 2;

    public static final class Primitive {
        public float[] pos, nor, uv;     // 3, 3 and 2 floats per vertex
        public int[] idx;                // triangle list
        public int material;
        public int vertexCount() { return pos.length / 3; }
        public int triangleCount() { return idx.length / 3; }
    }

    public static final class Material {
        public String name = "";
        public int baseImage = -1;
        public float[] baseColor = {1, 1, 1, 1};
        public float[] emissive = {0, 0, 0};
        public boolean blend, mask, doubleSided;
        public float alphaCutoff = 0.5f;
        public boolean isEmissive() { return emissive[0] + emissive[1] + emissive[2] > 0.001f; }
    }

    public static final class Node {
        public String name = "";
        public int mesh = -1;
        public float[] t = {0, 0, 0}, r = {0, 0, 0, 1}, s = {1, 1, 1};
        public float[] matrix;
        public int[] children = new int[0];
        public int parent = -1;
    }

    public static final class Channel {
        public int node, path;
        public boolean step;
        public float[] times, values;
    }

    public static final class Animation {
        public String name = "";
        public float duration;
        public final List<Channel> channels = new ArrayList<>();
    }

    public final List<Node> nodes = new ArrayList<>();
    public int[] roots = new int[0];
    public final List<List<Primitive>> meshes = new ArrayList<>();
    public final List<Material> materials = new ArrayList<>();
    public final List<byte[]> images = new ArrayList<>();
    public final List<Animation> animations = new ArrayList<>();

    // -------------------------------------------------------------------------------- parsing

    public static Gltf parse(byte[] glb) {
        ByteBuffer bb = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);
        if (bb.getInt() != 0x46546C67) throw new IllegalArgumentException("Not a GLB file");
        bb.getInt();            // version
        bb.getInt();            // total length
        JsonObject json = null;
        byte[] bin = new byte[0];
        while (bb.remaining() >= 8) {
            int len = bb.getInt();
            int type = bb.getInt();
            byte[] chunk = new byte[len];
            bb.get(chunk);
            if (type == 0x4E4F534A) json = JsonParser.parseString(new String(chunk, StandardCharsets.UTF_8)).getAsJsonObject();
            else if (type == 0x004E4942) bin = chunk;
        }
        if (json == null) throw new IllegalArgumentException("GLB has no JSON chunk");
        return new Gltf(json, ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN));
    }

    private final JsonObject j;
    private final ByteBuffer bin;

    private Gltf(JsonObject json, ByteBuffer bin) {
        this.j = json;
        this.bin = bin;
        readImages();
        readMaterials();
        readMeshes();
        readNodes();
        readAnimations();
    }

    private static JsonArray arr(JsonObject o, String key) {
        return o.has(key) ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static float[] floats(JsonElement e, int n, float[] def) {
        if (e == null || !e.isJsonArray()) return def;
        JsonArray a = e.getAsJsonArray();
        float[] r = new float[n];
        for (int i = 0; i < n; i++) r[i] = a.get(i).getAsFloat();
        return r;
    }

    private void readImages() {
        for (JsonElement e : arr(j, "images")) {
            JsonObject im = e.getAsJsonObject();
            if (!im.has("bufferView")) { images.add(new byte[0]); continue; }
            JsonObject bv = arr(j, "bufferViews").get(im.get("bufferView").getAsInt()).getAsJsonObject();
            int off = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
            byte[] data = new byte[bv.get("byteLength").getAsInt()];
            bin.position(off);
            bin.get(data);
            images.add(data);
        }
    }

    private void readMaterials() {
        JsonArray textures = arr(j, "textures");
        for (JsonElement e : arr(j, "materials")) {
            JsonObject m = e.getAsJsonObject();
            Material mat = new Material();
            if (m.has("name")) mat.name = m.get("name").getAsString();
            if (m.has("pbrMetallicRoughness")) {
                JsonObject pbr = m.getAsJsonObject("pbrMetallicRoughness");
                mat.baseColor = floats(pbr.get("baseColorFactor"), 4, mat.baseColor);
                if (pbr.has("baseColorTexture")) {
                    int ti = pbr.getAsJsonObject("baseColorTexture").get("index").getAsInt();
                    mat.baseImage = textures.get(ti).getAsJsonObject().get("source").getAsInt();
                }
            }
            mat.emissive = floats(m.get("emissiveFactor"), 3, mat.emissive);
            String mode = m.has("alphaMode") ? m.get("alphaMode").getAsString() : "OPAQUE";
            mat.blend = mode.equals("BLEND");
            mat.mask = mode.equals("MASK");
            if (m.has("alphaCutoff")) mat.alphaCutoff = m.get("alphaCutoff").getAsFloat();
            mat.doubleSided = m.has("doubleSided") && m.get("doubleSided").getAsBoolean();
            materials.add(mat);
        }
    }

    /** Reads an accessor into a float array (components per element returned in comps[0]). */
    private float[] readFloats(int accessorIndex, int[] comps) {
        JsonObject a = arr(j, "accessors").get(accessorIndex).getAsJsonObject();
        int n = switch (a.get("type").getAsString()) {
            case "SCALAR" -> 1; case "VEC2" -> 2; case "VEC3" -> 3; case "VEC4" -> 4; case "MAT4" -> 16;
            default -> throw new IllegalArgumentException("accessor type " + a.get("type"));
        };
        if (comps != null) comps[0] = n;
        int count = a.get("count").getAsInt();
        int ct = a.get("componentType").getAsInt();
        int csize = (ct == 5126 || ct == 5125) ? 4 : (ct == 5123 || ct == 5122) ? 2 : 1;
        JsonObject bv = arr(j, "bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();
        int base = (bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0) + (a.has("byteOffset") ? a.get("byteOffset").getAsInt() : 0);
        int stride = bv.has("byteStride") ? bv.get("byteStride").getAsInt() : n * csize;
        boolean norm = a.has("normalized") && a.get("normalized").getAsBoolean();
        float[] out = new float[count * n];
        for (int i = 0; i < count; i++) {
            for (int c = 0; c < n; c++) {
                int p = base + i * stride + c * csize;
                float v = switch (ct) {
                    case 5126 -> bin.getFloat(p);
                    case 5125 -> (float) (bin.getInt(p) & 0xFFFFFFFFL);
                    case 5123 -> { int u = bin.getShort(p) & 0xFFFF; yield norm ? u / 65535f : u; }
                    case 5122 -> { int u = bin.getShort(p); yield norm ? Math.max(u / 32767f, -1f) : u; }
                    case 5121 -> { int u = bin.get(p) & 0xFF; yield norm ? u / 255f : u; }
                    case 5120 -> { int u = bin.get(p); yield norm ? Math.max(u / 127f, -1f) : u; }
                    default -> throw new IllegalArgumentException("componentType " + ct);
                };
                out[i * n + c] = v;
            }
        }
        return out;
    }

    private int[] readIndices(int accessorIndex) {
        float[] f = readFloats(accessorIndex, null);
        int[] r = new int[f.length];
        for (int i = 0; i < f.length; i++) r[i] = (int) f[i];
        return r;
    }

    private void readMeshes() {
        for (JsonElement e : arr(j, "meshes")) {
            List<Primitive> prims = new ArrayList<>();
            for (JsonElement pe : arr(e.getAsJsonObject(), "primitives")) {
                JsonObject p = pe.getAsJsonObject();
                if (p.has("mode") && p.get("mode").getAsInt() != 4) continue; // triangles only
                JsonObject at = p.getAsJsonObject("attributes");
                Primitive pr = new Primitive();
                pr.pos = readFloats(at.get("POSITION").getAsInt(), null);
                int vc = pr.pos.length / 3;
                pr.nor = at.has("NORMAL") ? readFloats(at.get("NORMAL").getAsInt(), null) : new float[vc * 3];
                pr.uv = at.has("TEXCOORD_0") ? readFloats(at.get("TEXCOORD_0").getAsInt(), null) : new float[vc * 2];
                if (p.has("indices")) pr.idx = readIndices(p.get("indices").getAsInt());
                else { pr.idx = new int[vc]; for (int i = 0; i < vc; i++) pr.idx[i] = i; }
                pr.material = p.has("material") ? p.get("material").getAsInt() : -1;
                prims.add(pr);
            }
            meshes.add(prims);
        }
    }

    private void readNodes() {
        for (JsonElement e : arr(j, "nodes")) {
            JsonObject o = e.getAsJsonObject();
            Node n = new Node();
            if (o.has("name")) n.name = o.get("name").getAsString();
            if (o.has("mesh")) n.mesh = o.get("mesh").getAsInt();
            n.t = floats(o.get("translation"), 3, n.t);
            n.r = floats(o.get("rotation"), 4, n.r);
            n.s = floats(o.get("scale"), 3, n.s);
            if (o.has("matrix")) n.matrix = floats(o.get("matrix"), 16, null);
            if (o.has("children")) {
                JsonArray c = o.getAsJsonArray("children");
                n.children = new int[c.size()];
                for (int i = 0; i < c.size(); i++) n.children[i] = c.get(i).getAsInt();
            }
            nodes.add(n);
        }
        for (int i = 0; i < nodes.size(); i++) for (int c : nodes.get(i).children) nodes.get(c).parent = i;
        JsonArray scenes = arr(j, "scenes");
        if (scenes.size() > 0) {
            int si = j.has("scene") ? j.get("scene").getAsInt() : 0;
            JsonArray rs = arr(scenes.get(si).getAsJsonObject(), "nodes");
            roots = new int[rs.size()];
            for (int i = 0; i < rs.size(); i++) roots[i] = rs.get(i).getAsInt();
        }
    }

    private void readAnimations() {
        for (JsonElement e : arr(j, "animations")) {
            JsonObject ao = e.getAsJsonObject();
            Animation an = new Animation();
            if (ao.has("name")) an.name = ao.get("name").getAsString();
            JsonArray samplers = arr(ao, "samplers");
            for (JsonElement ce : arr(ao, "channels")) {
                JsonObject co = ce.getAsJsonObject();
                JsonObject target = co.getAsJsonObject("target");
                if (!target.has("node")) continue;
                String path = target.get("path").getAsString();
                int pi = switch (path) { case "translation" -> PATH_TRANSLATION; case "rotation" -> PATH_ROTATION; case "scale" -> PATH_SCALE; default -> -1; };
                if (pi < 0) continue; // weights are not used by these models
                JsonObject s = samplers.get(co.get("sampler").getAsInt()).getAsJsonObject();
                Channel ch = new Channel();
                ch.node = target.get("node").getAsInt();
                ch.path = pi;
                ch.step = s.has("interpolation") && s.get("interpolation").getAsString().equals("STEP");
                ch.times = readFloats(s.get("input").getAsInt(), null);
                ch.values = readFloats(s.get("output").getAsInt(), null);
                if (ch.times.length > 0) an.duration = Math.max(an.duration, ch.times[ch.times.length - 1]);
                an.channels.add(ch);
            }
            animations.add(an);
        }
    }

    // -------------------------------------------------------------------------------- evaluation

    public int findNode(String name) {
        for (int i = 0; i < nodes.size(); i++) if (nodes.get(i).name.equals(name)) return i;
        return -1;
    }

    /** World matrices for every node. anim may be null (rest pose). time is clamped or looped to the clip length. */
    public float[][] worldMatrices(Animation anim, float time, boolean loop) {
        int n = nodes.size();
        float[][] t = new float[n][], r = new float[n][], s = new float[n][];
        for (int i = 0; i < n; i++) { Node nd = nodes.get(i); t[i] = nd.t; r[i] = nd.r; s[i] = nd.s; }
        if (anim != null && anim.duration > 0) {
            float tt = loop ? (time % anim.duration + anim.duration) % anim.duration : Math.max(0, Math.min(time, anim.duration));
            for (Channel ch : anim.channels) {
                float[] v = sample(ch, tt);
                switch (ch.path) {
                    case PATH_TRANSLATION -> t[ch.node] = v;
                    case PATH_ROTATION -> r[ch.node] = v;
                    default -> s[ch.node] = v;
                }
            }
        }
        float[][] world = new float[n][];
        for (int i = 0; i < n; i++) computeWorld(i, world, t, r, s);
        return world;
    }

    private float[] computeWorld(int i, float[][] world, float[][] t, float[][] r, float[][] s) {
        if (world[i] != null) return world[i];
        Node nd = nodes.get(i);
        float[] local = nd.matrix != null && t[i] == nd.t && r[i] == nd.r && s[i] == nd.s ? nd.matrix : Mat4.compose(t[i], r[i], s[i]);
        world[i] = nd.parent < 0 ? local : Mat4.mul(computeWorld(nd.parent, world, t, r, s), local);
        return world[i];
    }

    private static float[] sample(Channel ch, float time) {
        int comps = ch.path == PATH_ROTATION ? 4 : 3;
        float[] times = ch.times;
        int last = times.length - 1;
        float[] out = new float[comps];
        if (time <= times[0] || last == 0) { System.arraycopy(ch.values, 0, out, 0, comps); return out; }
        if (time >= times[last]) { System.arraycopy(ch.values, last * comps, out, 0, comps); return out; }
        int lo = 0, hi = last;
        while (hi - lo > 1) { int mid = (lo + hi) >>> 1; if (times[mid] <= time) lo = mid; else hi = mid; }
        float f = (time - times[lo]) / (times[hi] - times[lo]);
        if (ch.step) { System.arraycopy(ch.values, lo * comps, out, 0, comps); return out; }
        if (ch.path == PATH_ROTATION) {
            float ax = ch.values[lo * 4], ay = ch.values[lo * 4 + 1], az = ch.values[lo * 4 + 2], aw = ch.values[lo * 4 + 3];
            float bx = ch.values[hi * 4], by = ch.values[hi * 4 + 1], bz = ch.values[hi * 4 + 2], bw = ch.values[hi * 4 + 3];
            float dot = ax * bx + ay * by + az * bz + aw * bw;
            if (dot < 0) { bx = -bx; by = -by; bz = -bz; bw = -bw; dot = -dot; }
            float wa, wb;
            if (dot > 0.9995f) { wa = 1 - f; wb = f; }
            else { float th = (float) Math.acos(dot), sn = (float) Math.sin(th); wa = (float) Math.sin((1 - f) * th) / sn; wb = (float) Math.sin(f * th) / sn; }
            float x = wa * ax + wb * bx, y = wa * ay + wb * by, z = wa * az + wb * bz, w = wa * aw + wb * bw;
            float len = (float) Math.sqrt(x * x + y * y + z * z + w * w);
            out[0] = x / len; out[1] = y / len; out[2] = z / len; out[3] = w / len;
        } else {
            for (int c = 0; c < 3; c++) out[c] = ch.values[lo * 3 + c] * (1 - f) + ch.values[hi * 3 + c] * f;
        }
        return out;
    }

    /** Collects a node and all its descendants. */
    public List<Integer> subtree(int root) {
        List<Integer> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private void collect(int i, List<Integer> out) {
        out.add(i);
        for (int c : nodes.get(i).children) collect(c, out);
    }
}
