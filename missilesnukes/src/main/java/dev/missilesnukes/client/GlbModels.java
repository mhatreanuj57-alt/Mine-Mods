package dev.missilesnukes.client;

import dev.missilesnukes.glb.Gltf;
import dev.missilesnukes.glb.MeshSplitter;
import dev.missilesnukes.sim.MissileSpec;
import dev.missilesnukes.sim.PieceGeometry;

import java.util.ArrayList;
import java.util.List;

/** Lazily built, cached model sets. Cleared on every resource reload. */
public final class GlbModels {
    private GlbModels() {}

    /** An animated thruster effect: a named subtree of a model plus the clip that drives it. */
    public static final class FlameSet {
        public final GlbModel model;
        public final Gltf.Animation anim;
        public final List<GlbModel.Draw> draws;

        FlameSet(GlbModel model, String rootNodeName, int animIndex) {
            this.model = model;
            int root = model.gltf.findNode(rootNodeName);
            draws = model.drawsForNodes(root >= 0 ? model.gltf.subtree(root) : List.of());
            anim = model.gltf.animations.size() > animIndex ? model.gltf.animations.get(animIndex) : null;
        }
    }

    /**
     * A complete missile as three stage draw lists (0 engine, 1 central body, 2 warhead) plus its thruster flame.
     * Light: the single-mesh model is cut at the stage seams. Heavy: the three stages are separate nodes in the model,
     * placed by their node offsets (restWorld), so no cutting is needed.
     */
    public static final class MissileSet {
        public final GlbModel model;
        public final List<GlbModel.Draw>[] pieces;
        public final float[][] restWorld;      // null for the light missile (its pieces are already in model space)
        public final FlameSet flame;

        @SuppressWarnings("unchecked")
        private MissileSet(GlbModel model, List<GlbModel.Draw>[] pieces, float[][] restWorld, FlameSet flame) {
            this.model = model; this.pieces = pieces; this.restWorld = restWorld; this.flame = flame;
        }

        @SuppressWarnings("unchecked")
        static MissileSet light() {
            GlbModel model = new GlbModel("missile", "light_icbm_missile_without_flame.glb");
            List<Gltf.Primitive> all = new ArrayList<>();
            for (Gltf.Node n : model.gltf.nodes) if (n.mesh >= 0) all.addAll(model.gltf.meshes.get(n.mesh));
            List<MeshSplitter.Piece> cut = MeshSplitter.split(all, new double[]{PieceGeometry.SEAM_ENGINE, PieceGeometry.SEAM_WARHEAD});
            List<GlbModel.Draw>[] pieces = new List[3];
            for (int i = 0; i < 3; i++) pieces[i] = model.drawsForPrimitives(cut.get(i).primitives);
            FlameSet flame = new FlameSet(new GlbModel("flame", "light_icbm_complete_assembled_with_flame.glb"), "Voxel_Rocket_Thruster_Root", 0);
            return new MissileSet(model, pieces, null, flame);
        }

        @SuppressWarnings("unchecked")
        static MissileSet heavy() {
            GlbModel model = new GlbModel("heavy", "heavy_icbm_complete.glb");
            Gltf g = model.gltf;
            List<GlbModel.Draw>[] pieces = new List[3];
            pieces[0] = model.drawsForNodes(g.subtree(g.findNode("Heavy_ICBM_Engine")));
            pieces[1] = model.drawsForNodes(g.subtree(g.findNode("Heavy_ICBM_Central_Body")));
            pieces[2] = model.drawsForNodes(g.subtree(g.findNode("Heavy_ICBM_Warhead")));
            float[][] rest = g.worldMatrices(null, 0f, false);
            FlameSet flame = new FlameSet(model, "Heavy_ICBM_Thruster_VFX", 0);   // clip 0 = Thruster_Active_Loop
            return new MissileSet(model, pieces, rest, flame);
        }
    }

    public static final class ExplosionSet {
        public final GlbModel model;
        public final Gltf.Animation anim;
        public final List<GlbModel.Draw> draws;

        ExplosionSet() {
            model = new GlbModel("explosion", "icbm_explosion_vfx.glb");
            draws = model.drawsForAllNodes();
            anim = model.gltf.animations.isEmpty() ? null : model.gltf.animations.get(0);
        }
    }

    /** The launch remote; keycap quads and key bodies are separate draws so each of the 8 buttons can be pressed. */
    public static final class RemoteSet {
        public final GlbModel model;
        public final List<GlbModel.Draw> draws = new ArrayList<>();

        RemoteSet() {
            model = new GlbModel("remote", "light_icbm_launch_remote.glb");
            Gltf g = model.gltf;
            for (Gltf.Node n : g.nodes) {
                if (n.mesh < 0) continue;
                for (Gltf.Primitive p : g.meshes.get(n.mesh)) {
                    String name = p.material >= 0 ? g.materials.get(p.material).name : "";
                    if (name.equals("Remote_Keycaps_1_to_8")) {            // 8 quads, in the order 1..8
                        List<Gltf.Primitive> q = GlbModel.splitGroups(p, 4);
                        for (int i = 0; i < q.size(); i++) draws.add(model.makeDraw(q.get(i), -1, i));
                    } else if (name.equals("Remote_Keycap_Body")) {         // 8 boxes, same order
                        List<Gltf.Primitive> q = GlbModel.splitGroups(p, 24);
                        for (int i = 0; i < q.size(); i++) draws.add(model.makeDraw(q.get(i), -1, i));
                    } else draws.add(model.makeDraw(p, -1, -1));
                }
            }
        }
    }

    /** A plain static model (central body, engine mount, warhead item icons). */
    public static final class StaticSet {
        public final GlbModel model;
        public final List<GlbModel.Draw> draws;

        StaticSet(String key, String file) {
            model = new GlbModel(key, file);
            draws = model.drawsForAllNodes();
        }
    }

    private static MissileSet lightMissile, heavyMissile;
    private static ExplosionSet explosion;
    private static RemoteSet remote;
    private static StaticSet warhead, body, engine, heavyWarhead, heavyBody, heavyEngine;

    public static MissileSet missile(MissileSpec spec) {
        if (spec == MissileSpec.HEAVY) { if (heavyMissile == null) heavyMissile = MissileSet.heavy(); return heavyMissile; }
        if (lightMissile == null) lightMissile = MissileSet.light();
        return lightMissile;
    }
    public static ExplosionSet explosion() { if (explosion == null) explosion = new ExplosionSet(); return explosion; }
    public static RemoteSet remote() { if (remote == null) remote = new RemoteSet(); return remote; }
    public static StaticSet warhead() { if (warhead == null) warhead = new StaticSet("warhead", "light_icbm_warhead.glb"); return warhead; }
    public static StaticSet body() { if (body == null) body = new StaticSet("body", "light_icbm_central_body.glb"); return body; }
    public static StaticSet engine() { if (engine == null) engine = new StaticSet("engine", "light_icbm_engine_mount.glb"); return engine; }
    public static StaticSet heavyWarhead() { if (heavyWarhead == null) heavyWarhead = new StaticSet("hwarhead", "heavy_icbm_warhead.glb"); return heavyWarhead; }
    public static StaticSet heavyBody() { if (heavyBody == null) heavyBody = new StaticSet("hbody", "heavy_icbm_central_body.glb"); return heavyBody; }
    public static StaticSet heavyEngine() { if (heavyEngine == null) heavyEngine = new StaticSet("hengine", "heavy_icbm_engine.glb"); return heavyEngine; }

    public static void clear() {
        lightMissile = null; heavyMissile = null; explosion = null; remote = null;
        warhead = null; body = null; engine = null; heavyWarhead = null; heavyBody = null; heavyEngine = null;
        GlbAssets.clear();
    }
}
