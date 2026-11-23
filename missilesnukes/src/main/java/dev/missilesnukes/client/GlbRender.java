package dev.missilesnukes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.missilesnukes.glb.Mat4;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.List;

/** Emits glTF triangles into Minecraft's vertex buffers. */
public final class GlbRender {
    private GlbRender() {}

    /**
     * @param world     animated world matrices per node (may be null for static geometry)
     * @param pressMask bit i set = remote keycap i is drawn pressed in
     * @param pressDepth how far a pressed key sinks, in model units
     */
    public static void draw(List<GlbModel.Draw> draws, float[][] world, PoseStack ps, MultiBufferSource buffers,
                            int light, int overlay, int pressMask, float pressDepth) {
        PoseStack.Pose pose = ps.last();
        float[] tmp = new float[3];
        for (GlbModel.Draw d : draws) {
            VertexConsumer vc = buffers.getBuffer(d.renderType());
            float[] m = world != null && d.node >= 0 ? world[d.node] : null;
            float oz = d.key >= 0 && ((pressMask >> d.key) & 1) != 0 ? -pressDepth : 0f;
            int lt = d.bright ? LightTexture.FULL_BRIGHT : light;
            float[] pos = d.prim.pos, nor = d.prim.nor, uv = d.prim.uv;
            int[] idx = d.prim.idx;
            for (int t = 0; t + 2 < idx.length; t += 3) {
                for (int k = 0; k < 4; k++) {                   // entity render types are quad based: repeat the last vertex
                    int vi = idx[t + Math.min(k, 2)];
                    float x = pos[vi * 3], y = pos[vi * 3 + 1], z = pos[vi * 3 + 2];
                    float nx = nor[vi * 3], ny = nor[vi * 3 + 1], nz = nor[vi * 3 + 2];
                    if (m != null) {
                        Mat4.transformPoint(m, x, y, z, tmp);
                        x = tmp[0]; y = tmp[1]; z = tmp[2];
                        Mat4.transformDir(m, nx, ny, nz, tmp);
                        float len = (float) Math.sqrt(tmp[0] * tmp[0] + tmp[1] * tmp[1] + tmp[2] * tmp[2]);
                        if (len > 1e-6f) { nx = tmp[0] / len; ny = tmp[1] / len; nz = tmp[2] / len; }
                    }
                    vc.addVertex(pose, x, y, z + oz)
                        .setColor(d.r, d.g, d.b, d.a)
                        .setUv(uv[vi * 2], uv[vi * 2 + 1])
                        .setOverlay(overlay)
                        .setLight(lt)
                        .setNormal(pose, nx, ny, nz);
                }
            }
        }
    }

    public static void draw(List<GlbModel.Draw> draws, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        draw(draws, null, ps, buffers, light, overlay, 0, 0f);
    }
}
