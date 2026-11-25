package dev.transport.truck.client;

import static dev.transport.truck.client.Glb.*;
import dev.transport.truck.client.Glb.Access;
import dev.transport.truck.client.Glb.Track;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.*;
import dev.transport.truck.*;
import it.unimi.dsi.fastutil.floats.*;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.*;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class TruckModel implements AutoCloseable {
  static final ResourceLocation MODEL_TEXTURE = TransportTruck.id("textures/white.png");

  record Material(ResourceLocation texture, int r, int g, int b, int a, boolean blend) {}

  record Key(int material, int owner) {}

  record Draw(Key key, float[] vertices, int[] indices) {}

  static final class Builder {
    final FloatArrayList vertices = new FloatArrayList();
    final IntArrayList indices = new IntArrayList();
    final Long2IntOpenHashMap clusters = new Long2IntOpenHashMap();

    Builder() {
      clusters.defaultReturnValue(-1);
    }
  }

  final Glb g;
  final Material[] materials;
  final Set<Integer> bones = new HashSet<>(), moving = new HashSet<>();
  final List<ResourceLocation> owned = new ArrayList<>();
  final List<List<Draw>> lods = new ArrayList<>();

  TruckModel(ResourceManager rm) throws IOException {
    Map<String, String> alias = new HashMap<>();
    var mapping = rm.getResource(TransportTruck.id("models/vehicle/nodes.json"));
    if (mapping.isPresent())
      try (var r = mapping.get().openAsReader()) {
        JsonParser.parseReader(r)
            .getAsJsonObject()
            .entrySet()
            .forEach(e -> alias.put(e.getKey(), e.getValue().getAsString()));
      }
    try (var in = rm.open(TransportTruck.id("models/vehicle/truck.glb"))) {
      g = new Glb(in, alias);
    }
    materials = new Material[g.arr("materials").size() + 1];
    Map<Integer, ResourceLocation> images = new HashMap<>();
    try {
      for (int i = 0; i < materials.length; i++) {
        var m = i == materials.length - 1 ? new JsonObject() : g.obj("materials", i);
        var p =
            m.has("pbrMetallicRoughness")
                ? m.getAsJsonObject("pbrMetallicRoughness")
                : new JsonObject();
        float[] c = values(p, "baseColorFactor", 1, 1, 1, 1);
        ResourceLocation tex = MODEL_TEXTURE;
        if (p.has("baseColorTexture")) {
          int image =
              num(
                  g.obj("textures", num(p.getAsJsonObject("baseColorTexture"), "index", 0)),
                  "source",
                  0);
          tex = images.get(image);
          if (tex == null) {
            tex = loadImage(image);
            images.put(image, tex);
          }
        }
        materials[i] =
            new Material(
                tex,
                gamma(c[0]),
                gamma(c[1]),
                gamma(c[2]),
                Math.round(c[3] * 255),
                m.has("alphaMode") && m.get("alphaMode").getAsString().equals("BLEND"));
      }
      for (var e : g.arr("skins"))
        for (int j : ints(e.getAsJsonObject().getAsJsonArray("joints"))) bones.add(j);
      for (int i = 0; i < g.nodes.length; i++) {
        String r = g.nodes[i].role;
        if (bones.contains(i)
            || r.matches(
                "(wheel|hub|shock|axle_shaft|driveshaft|steering_knuckle|side_wing|door|leaf_spring|rear_bearing_carrier)_.*|hood|steering_wheel|front_axle|rear_axle"))
          moving.add(i);
      }
      for (float cell : new float[] {.035f, .09f, .20f}) lods.add(bake(cell));
    } catch (IOException | RuntimeException e) {
      close();
      throw e;
    }
  }

  private static int gamma(float f) {
    return Mth.clamp(Math.round(255 * (float) Math.pow(f, 1 / 2.2)), 0, 255);
  }

  private ResourceLocation loadImage(int i) throws IOException {
    var im = g.obj("images", i);
    var view = g.obj("bufferViews", num(im, "bufferView", -1));
    int length = num(view, "byteLength", 0);
    byte[] bytes = new byte[length];
    g.binary.duplicate().position(num(view, "byteOffset", 0)).get(bytes);
    var img = ImageIO.read(new ByteArrayInputStream(bytes));
    if (img == null) throw new IOException("Unsupported image " + i);
    var nativeImg = new NativeImage(img.getWidth(), img.getHeight(), false);
    for (int y = 0; y < img.getHeight(); y++)
      for (int x = 0; x < img.getWidth(); x++) {
        int c = img.getRGB(x, y);
        nativeImg.setPixelRGBA(x, y, (c & 0xff00ff00) | ((c >> 16) & 255) | ((c & 255) << 16));
      }
    var key = TransportTruck.id("dynamic/truck_" + i);
    Minecraft.getInstance().getTextureManager().register(key, new DynamicTexture(nativeImg));
    owned.add(key);
    return key;
  }

  private int owner(int i) {
    while (i >= 0 && !moving.contains(i)) i = g.nodes[i].parent;
    return i;
  }

  private Access access(JsonObject o, String k) {
    return o.has(k) ? g.new Access(num(o, k, -1)) : null;
  }

  private static long quant(float x, float s) {
    return Math.round(x / s) & 32767L;
  }

  private List<Draw> bake(float cell) {
    Map<Key, Builder> buckets = new LinkedHashMap<>();
    for (int i = 0; i < g.nodes.length; i++) {
      var n = g.nodes[i];
      if (n.mesh < 0) continue;
      for (var e : g.obj("meshes", n.mesh).getAsJsonArray("primitives")) {
        var p = e.getAsJsonObject();
        if (num(p, "mode", 4) != 4) continue;
        var attrs = p.getAsJsonObject("attributes");
        var pos = access(attrs, "POSITION");
        var norm = access(attrs, "NORMAL");
        var uv = access(attrs, "TEXCOORD_0");
        var joints = access(attrs, "JOINTS_0");
        var ix = access(p, "indices");
        int[] bone = n.skin < 0 ? null : ints(g.obj("skins", n.skin).getAsJsonArray("joints"));
        int mat = num(p, "material", materials.length - 1), o = owner(i);
        Matrix4f trans =
            bone != null
                ? new Matrix4f()
                : o < 0 ? new Matrix4f(n.rest) : new Matrix4f(g.nodes[o].rest).invert().mul(n.rest);
        Matrix3f normalMat = new Matrix3f(trans).invert().transpose();
        Vector3f v = new Vector3f(), normal = new Vector3f();
        int[] mapped = new int[pos.count];
        Builder[] group = new Builder[pos.count];
        Map<Integer, Builder> owners = new HashMap<>();
        for (int k = 0; k < pos.count; k++) {
          int id = bone != null && joints != null ? bone[joints.integer(k, 0)] : o;
          Builder b =
              owners.computeIfAbsent(
                  id, z -> buckets.computeIfAbsent(new Key(mat, z), ignored -> new Builder()));
          group[k] = b;
          trans.transformPosition(v.set(pos.get(k, 0), pos.get(k, 1), pos.get(k, 2)));
          if (norm == null) normal.set(0, 1, 0);
          else
            normalMat
                .transform(normal.set(norm.get(k, 0), norm.get(k, 1), norm.get(k, 2)))
                .normalize();
          int normalBin =
              (normal.x > .48 ? 1 : normal.x < -.48 ? 2 : 0)
                  + 3 * (normal.y > .48 ? 1 : normal.y < -.48 ? 2 : 0)
                  + 9 * (normal.z > .48 ? 1 : normal.z < -.48 ? 2 : 0);
          long key =
              quant(v.x, cell)
                  | (quant(v.y, cell) << 15)
                  | (quant(v.z, cell) << 30)
                  | ((long) normalBin << 45);
          int index = b.clusters.get(key);
          if (index < 0) {
            index = b.vertices.size() / 8;
            b.clusters.put(key, index);
            b.vertices.add(v.x);
            b.vertices.add(v.y);
            b.vertices.add(v.z);
            b.vertices.add(uv == null ? 0 : uv.get(k, 0));
            b.vertices.add(uv == null ? 0 : uv.get(k, 1));
            b.vertices.add(normal.x);
            b.vertices.add(normal.y);
            b.vertices.add(normal.z);
          }
          mapped[k] = index;
        }
        int count = ix == null ? pos.count : ix.count;
        for (int k = 0; k + 2 < count; k += 3) {
          int a = ix == null ? k : ix.integer(k, 0),
              b = ix == null ? k + 1 : ix.integer(k + 1, 0),
              c = ix == null ? k + 2 : ix.integer(k + 2, 0);
          if (group[a] != group[b]
              || group[a] != group[c]
              || mapped[a] == mapped[b]
              || mapped[a] == mapped[c]
              || mapped[b] == mapped[c]) continue;
          var dest = group[a].indices;
          dest.add(mapped[a]);
          dest.add(mapped[b]);
          dest.add(mapped[c]);
        }
      }
    }
    List<Draw> result = new ArrayList<>();
    for (var e : buckets.entrySet()) {
      Builder b = e.getValue();
      if (!b.indices.isEmpty())
        result.add(new Draw(e.getKey(), b.vertices.toFloatArray(), b.indices.toIntArray()));
    }
    result.sort(Comparator.comparing(d -> materials[d.key.material].blend));
    TransportTruck.LOG.info(
        "Truck LOD {}: {} triangles",
        cell,
        result.stream().mapToInt(d -> d.indices.length / 3).sum());
    return result;
  }

  private void pose(TruckEntity t, float a) {
    for (Node n : g.nodes) n.reset();
    float steer = Mth.lerp(a, t.previousSteer, t.steer),
        turn = (t.previousSpin + a * Mth.wrapDegrees(t.spin - t.previousSpin)) * Mth.DEG_TO_RAD;
    float[] h = new float[4];
    for (int i = 0; i < 4; i++) h[i] = Mth.lerp(a, t.oldCompression[i], t.compression[i]);
    Clip c = g.clips.get(steer >= 0 ? "steer_left" : "steer_right");
    if (c != null)
      for (Track tr : c.tracks())
        if (bones.contains(tr.node)) tr.apply(Math.abs(steer) * c.length());
    for (int i = 0; i < g.nodes.length; i++) {
      Node n = g.nodes[i];
      String r = n.role;
      int k =
          r.endsWith("_FL")
              ? 0
              : r.endsWith("_FR") ? 1 : r.endsWith("_RL") ? 2 : r.endsWith("_RR") ? 3 : -1;
      if (k >= 0 && r.startsWith("steering_knuckle_")) {
        n.t.y += h[k];
        boolean inside = steer > 0 ? k == 0 : k == 1;
        n.r.rotateY(steer * (inside ? 35 : 28) * Mth.DEG_TO_RAD);
      } else if (k >= 0
          && (r.startsWith("wheel_") || r.startsWith("hub_") || r.startsWith("axle_shaft_"))) {
        boolean spun = false, raised = false;
        for (int j = n.parent; j >= 0; j = g.nodes[j].parent) {
          String p = g.nodes[j].role;
          boolean rot =
              p.startsWith("hub_") || p.startsWith("wheel_") || p.startsWith("axle_shaft_");
          spun |= rot;
          raised |= rot || p.startsWith("steering_knuckle_") || p.endsWith("_axle");
        }
        if (!spun) n.r.rotateX(turn);
        if (!raised) n.t.y += h[k];
      } else if (r.startsWith("driveshaft_")) {
        boolean hit = false;
        Clip drive = g.clips.get("drive_forward");
        if (drive != null)
          for (Track tr : drive.tracks())
            if (tr.node == i && tr.path.equals("rotation")) {
              tr.apply(((turn / (2 * Mth.PI) % 1 + 1) % 1) * drive.length());
              hit = true;
              break;
            }
        if (!hit) n.r.rotateZ(3 * turn);
      } else if (r.equals("steering_wheel")) n.r.rotateY(steer * 2 * Mth.PI);
      else if (k >= 0 && r.startsWith("shock_")) {
        n.t.y += h[k] * .5f;
        n.s.y *= 1 - h[k] * .65f;
      } else if (r.endsWith("_axle") || r.startsWith("leaf_spring_")) {
        int j = r.contains("front") ? 0 : 2;
        float avg = (h[j] + h[j + 1]) * .5f;
        n.t.y += avg * (r.startsWith("leaf") ? .5f : 1);
        n.r.rotateZ((float) Math.atan2(h[j] - h[j + 1], 2.2));
        if (r.startsWith("leaf")) n.s.y *= 1 + avg * .6f;
      } else if (r.startsWith("rear_bearing_carrier_")) n.t.y += h[r.endsWith("_L") ? 2 : 3];
      int panel =
          r.startsWith("side_wing_")
              ? 0
              : r.equals("hood") ? 1 : r.equals("door_L") ? 2 : r.equals("door_R") ? 3 : -1;
      if (panel >= 0) {
        float f = Mth.lerp(a, t.oldPanels[panel], t.panels[panel]);
        boolean applied = false;
        String word = panel == 0 ? "wing" : panel == 1 ? "hood" : "door";
        for (Clip clip : g.clips.values()) {
          String name = clip.name().toLowerCase(Locale.ROOT);
          if (!name.contains(word) || !(name.contains("open") || name.contains("fold"))) continue;
          for (Track tr : clip.tracks())
            if (tr.node == i) {
              tr.apply(f * clip.length());
              applied = true;
            }
        }
        if (!applied) {
          if (panel == 0) n.r.rotateZ((r.endsWith("_L") ? -1 : 1) * f * Mth.HALF_PI);
          else if (panel == 1) n.r.rotateX(-f * 1.15f);
          else n.r.rotateY((panel == 2 ? -1 : 1) * f * 1.35f);
        }
      }
    }
    g.update();
  }

  // Transform each indexed vertex once before emitting it to the active buffer.
  private float[] transformed = new float[0];
  private final Vector3f position = new Vector3f(), normal = new Vector3f();

  void render(
      TruckEntity truck,
      float a,
      PoseStack stack,
      MultiBufferSource source,
      int light,
      double distance) {
    pose(truck, a);
    for (Draw d : lods.get(distance < 10 ? 0 : distance < 36 ? 1 : 2)) {
      Material m = materials[d.key.material];
      VertexConsumer vc =
          source.getBuffer(
              m.blend
                  ? RenderType.entityTranslucent(m.texture)
                  : RenderType.entityCutoutNoCull(m.texture));
      stack.pushPose();
      if (d.key.owner >= 0) {
        Matrix4f world = g.nodes[d.key.owner].world;
        stack.mulPoseMatrix(world);
        stack.last().normal().mul(new Matrix3f(world).invert().transpose());
      }
      var pose = stack.last();
      float[] v = d.vertices;
      if (transformed.length < v.length) transformed = new float[v.length];
      for (int k = 0; k < v.length; k += 8) {
        pose.pose().transformPosition(position.set(v[k], v[k + 1], v[k + 2]));
        pose.normal().transform(normal.set(v[k + 5], v[k + 6], v[k + 7])).normalize();
        transformed[k] = position.x;
        transformed[k + 1] = position.y;
        transformed[k + 2] = position.z;
        transformed[k + 5] = normal.x;
        transformed[k + 6] = normal.y;
        transformed[k + 7] = normal.z;
      }
      float red = m.r / 255f, green = m.g / 255f, blue = m.b / 255f, alpha = m.a / 255f;
      for (int i = 0; i < d.indices.length; i += 3) {
        for (int j = 0; j < 4; j++) {
          int k = d.indices[i + Math.min(j, 2)] * 8;
          vc.vertex(
              transformed[k],
              transformed[k + 1],
              transformed[k + 2],
              red,
              green,
              blue,
              alpha,
              v[k + 3],
              v[k + 4],
              OverlayTexture.NO_OVERLAY,
              light,
              transformed[k + 5],
              transformed[k + 6],
              transformed[k + 7]);
        }
      }
      stack.popPose();
    }
  }

  @Override
  public void close() {
    var tex = Minecraft.getInstance().getTextureManager();
    owned.forEach(tex::release);
    owned.clear();
  }
}
