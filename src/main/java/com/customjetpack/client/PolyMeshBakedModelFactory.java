package com.customjetpack.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.loading.json.raw.Bone;
import software.bernie.geckolib.loading.json.raw.Cube;
import software.bernie.geckolib.loading.json.raw.ModelProperties;
import software.bernie.geckolib.loading.json.raw.PolyMesh;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.BoneStructure;
import software.bernie.geckolib.loading.object.GeometryTree;

public final class PolyMeshBakedModelFactory implements BakedModelFactory {
    public static final PolyMeshBakedModelFactory INSTANCE = new PolyMeshBakedModelFactory();
    private final BakedModelFactory builtin = new BakedModelFactory.Builtin();

    private PolyMeshBakedModelFactory() {}

    @Override
    public BakedGeoModel constructGeoModel(GeometryTree tree) {
        List<GeoBone> roots = new ArrayList<>();
        for (BoneStructure root : tree.topLevelBones().values()) {
            roots.add(constructBone(root, tree.properties(), null));
        }
        return new BakedGeoModel(roots, tree.properties());
    }

    @Override
    public GeoBone constructBone(BoneStructure structure, ModelProperties properties, GeoBone parent) {
        Bone raw = structure.self();
        GeoBone bone = new GeoBone(parent, raw.name(), raw.mirror(), raw.inflate(), raw.neverRender(), raw.reset());
        double[] rotation = raw.rotation() != null ? raw.rotation() : new double[]{0, 0, 0};
        double[] pivot = raw.pivot() != null ? raw.pivot() : new double[]{0, 0, 0};
        bone.updateRotation((float) Math.toRadians(-rotation[0]), (float) Math.toRadians(-rotation[1]),
                (float) Math.toRadians(rotation[2]));
        bone.updatePivot((float) -pivot[0], (float) pivot[1], (float) pivot[2]);
        if (raw.cubes() != null) {
            for (Cube cube : raw.cubes()) {
                bone.getCubes().add(builtin.constructCube(cube, properties, bone));
            }
        }
        addPolyCube(raw, bone);
        for (BoneStructure child : structure.children().values()) {
            bone.getChildBones().add(constructBone(child, properties, bone));
        }
        return bone;
    }

    @Override
    public GeoCube constructCube(Cube cube, ModelProperties properties, GeoBone parent) {
        return builtin.constructCube(cube, properties, parent);
    }

    private static void addPolyCube(Bone raw, GeoBone bone) {
        PolyMesh mesh = raw.polyMesh();
        if (mesh == null) return;
        double[] positions = mesh.positions();
        double[] normals = mesh.normals();
        double[] uvs = mesh.uvs();
        double[][][] polygons = mesh.polysUnion().union();
        List<GeoQuad> quads = new ArrayList<>(polygons.length);
        for (double[][] polygon : polygons) {
            GeoVertex[] corners = new GeoVertex[4];
            for (int corner = 0; corner < 3; corner++) {
                int position = (int) polygon[corner][0];
                int uv = (int) polygon[corner][2];
                corners[corner] = new GeoVertex(new Vector3f((float) -positions[3 * position],
                        (float) positions[3 * position + 1], (float) positions[3 * position + 2]),
                        (float) uvs[2 * uv], (float) uvs[2 * uv + 1]);
            }
            corners[3] = corners[2];
            int normal = (int) polygon[0][1];
            Vector3f faceNormal = new Vector3f((float) -normals[3 * normal], (float) normals[3 * normal + 1],
                    (float) normals[3 * normal + 2]);
            quads.add(new GeoQuad(corners, faceNormal, Direction.UP));
        }
        bone.getCubes().add(new GeoCube(quads.toArray(new GeoQuad[0]), Vec3.ZERO, Vec3.ZERO,
                new Vec3(1, 1, 1), 0, false));
    }
}
