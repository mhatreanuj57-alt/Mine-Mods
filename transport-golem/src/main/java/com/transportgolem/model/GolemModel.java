package com.transportgolem.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Node hierarchy + keyframe data converted 1:1 from 3_final_transport_golem_3x.glb
 * (see tools/glb_to_json.py). Pure data/maths, no client classes, so the server can use it for seat positions.
 */
public final class GolemModel {
    public static final float UNITS_PER_BLOCK = 60.0F;
    private static final String PATH = "/assets/transportgolem/model/transport_golem.json";
    private static GolemModel instance;

    public static synchronized GolemModel get() {
        if (instance == null) instance = load();
        return instance;
    }

    public static final class Node {
        public String name;
        public final float[] t = new float[3];
        public final float[] r = new float[4];
        public int[] children = new int[0];
        public float[] quads = new float[0]; // 4 vertices * (pos3 + normal3 + uv2) per quad
        public int quadCount;
        public boolean seatPart;
        public int parent = -1;
    }

    public static final class Channel {
        public int node;
        public boolean rotation;
        public float[] times;
        public float[][] values;
    }

    public static final class Anim {
        public float duration;
        public boolean loop;
        public Channel[] channels;
    }

    public static final class Pose {
        public final float[] t;
        public final float[] r;

        public Pose(int n) {
            t = new float[n * 3];
            r = new float[n * 4];
        }
    }

    public Node[] nodes;
    private final Map<String, Integer> nameToIndex = new HashMap<>();
    private final Map<String, Anim> anims = new HashMap<>();
    private final Vector3f smashImpact = new Vector3f();

    private static GolemModel load() {
        GolemModel m = new GolemModel();
        try (InputStream in = GolemModel.class.getResourceAsStream(PATH)) {
            if (in == null) throw new IllegalStateException("Missing " + PATH);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray ns = root.getAsJsonArray("nodes");
            m.nodes = new Node[ns.size()];
            for (int i = 0; i < ns.size(); i++) {
                JsonObject o = ns.get(i).getAsJsonObject();
                Node n = new Node();
                n.name = o.get("name").getAsString();
                for (int k = 0; k < 3; k++) n.t[k] = o.getAsJsonArray("t").get(k).getAsFloat();
                for (int k = 0; k < 4; k++) n.r[k] = o.getAsJsonArray("r").get(k).getAsFloat();
                JsonArray ch = o.getAsJsonArray("children");
                n.children = new int[ch.size()];
                for (int k = 0; k < ch.size(); k++) n.children[k] = ch.get(k).getAsInt();
                JsonArray qs = o.getAsJsonArray("quads");
                n.quadCount = qs.size();
                n.quads = new float[qs.size() * 32];
                for (int q = 0; q < qs.size(); q++) {
                    JsonArray qa = qs.get(q).getAsJsonArray();
                    for (int k = 0; k < 32; k++) n.quads[q * 32 + k] = qa.get(k).getAsFloat();
                }
                n.seatPart = n.name.startsWith("Rig_") || n.name.startsWith("Driver_")
                        || n.name.startsWith("Chest_") || n.name.startsWith("Passenger_");
                m.nodes[i] = n;
                m.nameToIndex.put(n.name, i);
            }
            for (int i = 0; i < m.nodes.length; i++)
                for (int c : m.nodes[i].children) m.nodes[c].parent = i;

            JsonObject as = root.getAsJsonObject("animations");
            for (Map.Entry<String, JsonElement> e : as.entrySet()) {
                JsonObject ao = e.getValue().getAsJsonObject();
                Anim a = new Anim();
                a.duration = ao.get("duration").getAsFloat();
                a.loop = e.getKey().equals("walk");
                JsonArray chs = ao.getAsJsonArray("channels");
                a.channels = new Channel[chs.size()];
                for (int i = 0; i < chs.size(); i++) {
                    JsonObject co = chs.get(i).getAsJsonObject();
                    Channel c = new Channel();
                    c.node = co.get("node").getAsInt();
                    c.rotation = co.get("path").getAsString().equals("r");
                    JsonArray ts = co.getAsJsonArray("times");
                    JsonArray vs = co.getAsJsonArray("values");
                    c.times = new float[ts.size()];
                    c.values = new float[vs.size()][];
                    for (int k = 0; k < ts.size(); k++) c.times[k] = ts.get(k).getAsFloat();
                    for (int k = 0; k < vs.size(); k++) {
                        JsonArray va = vs.get(k).getAsJsonArray();
                        c.values[k] = new float[va.size()];
                        for (int j = 0; j < va.size(); j++) c.values[k][j] = va.get(j).getAsFloat();
                    }
                    a.channels[i] = c;
                }
                m.anims.put(e.getKey(), a);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read golem model", ex);
        }
        // Ground point hit by the two forearms at the impact keyframe (t = 0.9 s), model units.
        Pose p = m.createRestPose();
        m.apply("ground_smash", 0.9F, p, 1.0F);
        Vector3f l = m.toModelSpace(m.nodeIndex("Elbow_Left"), p, 0, -168, 3);
        Vector3f r = m.toModelSpace(m.nodeIndex("Elbow_Right"), p, 0, -168, 3);
        m.smashImpact.set((l.x + r.x) * 0.5F, 0.0F, (l.z + r.z) * 0.5F);
        return m;
    }

    public int nodeIndex(String name) {
        return nameToIndex.get(name);
    }

    public Vector3f getSmashImpact() {
        return smashImpact;
    }

    public Pose createRestPose() {
        Pose p = new Pose(nodes.length);
        for (int i = 0; i < nodes.length; i++) {
            System.arraycopy(nodes[i].t, 0, p.t, i * 3, 3);
            System.arraycopy(nodes[i].r, 0, p.r, i * 4, 4);
        }
        return p;
    }

    /** Samples an animation (linear translation, slerp rotation) and blends it into the pose with the given weight. */
    public void apply(String name, float time, Pose pose, float weight) {
        Anim a = anims.get(name);
        if (a == null || weight <= 0.0F) return;
        float tt;
        if (a.loop) {
            tt = time % a.duration;
            if (tt < 0) tt += a.duration;
        } else {
            tt = Math.max(0.0F, Math.min(time, a.duration));
        }
        for (Channel c : a.channels) {
            float[] ts = c.times;
            int n = ts.length;
            int k = 0;
            while (k < n - 2 && ts[k + 1] <= tt) k++;
            int k2 = Math.min(k + 1, n - 1);
            float span = ts[k2] - ts[k];
            float f = span > 1.0E-6F ? Math.max(0.0F, Math.min(1.0F, (tt - ts[k]) / span)) : 0.0F;
            float[] v0 = c.values[k];
            float[] v1 = c.values[k2];
            if (!c.rotation) {
                for (int i = 0; i < 3; i++) {
                    float v = v0[i] + (v1[i] - v0[i]) * f;
                    int idx = c.node * 3 + i;
                    pose.t[idx] += (v - pose.t[idx]) * weight;
                }
            } else {
                Quaternionf q = new Quaternionf(v0[0], v0[1], v0[2], v0[3]).slerp(new Quaternionf(v1[0], v1[1], v1[2], v1[3]), f);
                int o = c.node * 4;
                if (weight < 1.0F) {
                    Quaternionf cur = new Quaternionf(pose.r[o], pose.r[o + 1], pose.r[o + 2], pose.r[o + 3]);
                    cur.slerp(q, weight);
                    q = cur;
                }
                q.normalize();
                pose.r[o] = q.x;
                pose.r[o + 1] = q.y;
                pose.r[o + 2] = q.z;
                pose.r[o + 3] = q.w;
            }
        }
    }

    /** Transforms a point given in a node's local space into model space (root space, GLB units). */
    public Vector3f toModelSpace(int node, Pose pose, float x, float y, float z) {
        ArrayDeque<Integer> path = new ArrayDeque<>();
        for (int i = node; i >= 0; i = nodes[i].parent) path.addFirst(i);
        Matrix4f m = new Matrix4f();
        for (int i : path) {
            m.translate(pose.t[i * 3], pose.t[i * 3 + 1], pose.t[i * 3 + 2]);
            m.rotate(new Quaternionf(pose.r[i * 4], pose.r[i * 4 + 1], pose.r[i * 4 + 2], pose.r[i * 4 + 3]));
        }
        return m.transformPosition(new Vector3f(x, y, z));
    }
}
