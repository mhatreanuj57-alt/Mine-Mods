package dev.transport.truck.client;

import com.google.gson.*;
import dev.transport.truck.TransportTruck;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Decoder for binary glTF 2.0 with embedded images and rigid articulated meshes. */
final class Glb {
  final JsonObject doc;
  final ByteBuffer binary;
  final Node[] nodes;
  final Map<String, Clip> clips = new LinkedHashMap<>();

  JsonArray arr(String k) {
    return doc.has(k) ? doc.getAsJsonArray(k) : new JsonArray();
  }

  JsonObject obj(String k, int i) {
    return arr(k).get(i).getAsJsonObject();
  }

  static int num(JsonObject j, String k, int def) {
    return j.has(k) ? j.get(k).getAsInt() : def;
  }

  static float[] values(JsonObject j, String k, float... def) {
    if (!j.has(k)) return def;
    var a = j.getAsJsonArray(k);
    float[] v = new float[a.size()];
    for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsFloat();
    return v;
  }

  static int[] ints(JsonArray a) {
    int[] v = new int[a.size()];
    for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsInt();
    return v;
  }

  static final class Node {
    final Vector3f baseT = new Vector3f(),
        baseS = new Vector3f(1),
        t = new Vector3f(),
        s = new Vector3f();
    final Quaternionf baseR = new Quaternionf(), r = new Quaternionf();
    final Matrix4f world = new Matrix4f(), rest = new Matrix4f();
    String name, role;
    int parent = -1, mesh, skin;
    int[] children;

    void reset() {
      t.set(baseT);
      s.set(baseS);
      r.set(baseR);
    }
  }

  final class Access {
    final int start, stride, type, count, dims, size;
    final boolean normalized;

    Access(int index) {
      var a = obj("accessors", index);
      var v = obj("bufferViews", num(a, "bufferView", -1));
      type = num(a, "componentType", 5126);
      size =
          switch (type) {
            case 5120, 5121 -> 1;
            case 5122, 5123 -> 2;
            default -> 4;
          };
      dims =
          switch (a.get("type").getAsString()) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4", "MAT2" -> 4;
            case "MAT3" -> 9;
            case "MAT4" -> 16;
            default -> throw new IllegalArgumentException("Accessor shape");
          };
      start = num(a, "byteOffset", 0) + num(v, "byteOffset", 0);
      stride = num(v, "byteStride", dims * size);
      count = num(a, "count", 0);
      normalized = a.has("normalized") && a.get("normalized").getAsBoolean();
    }

    int integer(int i, int j) {
      int o = start + stride * i + j * size;
      return switch (type) {
        case 5120 -> binary.get(o);
        case 5121 -> Byte.toUnsignedInt(binary.get(o));
        case 5122 -> binary.getShort(o);
        case 5123 -> Short.toUnsignedInt(binary.getShort(o));
        default -> binary.getInt(o);
      };
    }

    float get(int i, int j) {
      if (type == 5126) return binary.getFloat(start + i * stride + j * size);
      int n = integer(i, j);
      if (!normalized) return n;
      return switch (type) {
        case 5120 -> Math.max(-1, n / 127f);
        case 5121 -> n / 255f;
        case 5122 -> Math.max(-1, n / 32767f);
        case 5123 -> n / 65535f;
        default -> n;
      };
    }
  }

  final class Track {
    final int node;
    final String path;
    final Access time, data;
    final boolean step;

    Track(JsonObject channel, JsonObject sampler) {
      var target = channel.getAsJsonObject("target");
      node = num(target, "node", -1);
      path = target.get("path").getAsString();
      time = new Access(num(sampler, "input", -1));
      data = new Access(num(sampler, "output", -1));
      step =
          sampler.has("interpolation") && sampler.get("interpolation").getAsString().equals("STEP");
    }

    void apply(float seconds) {
      if (time.count == 0 || node < 0) return;
      int a = 0, b = time.count - 1;
      while (b - a > 1) {
        int mid = (a + b) >>> 1;
        if (time.get(mid, 0) <= seconds) a = mid;
        else b = mid;
      }
      float dt = time.get(b, 0) - time.get(a, 0);
      float f = dt <= 0 ? 0 : Math.max(0, Math.min(1, (seconds - time.get(a, 0)) / dt));
      if (step && f < 1) f = 0;
      Node n = nodes[node];
      if (path.equals("rotation")) {
        n.r
            .set(data.get(a, 0), data.get(a, 1), data.get(a, 2), data.get(a, 3))
            .slerp(
                new Quaternionf(data.get(b, 0), data.get(b, 1), data.get(b, 2), data.get(b, 3)), f)
            .normalize();
      } else if (path.equals("translation") || path.equals("scale")) {
        Vector3f v = path.equals("scale") ? n.s : n.t;
        v.set(data.get(a, 0), data.get(a, 1), data.get(a, 2))
            .lerp(new Vector3f(data.get(b, 0), data.get(b, 1), data.get(b, 2)), f);
      }
    }
  }

  record Clip(String name, List<Track> tracks, float length) {}

  Glb(InputStream input, Map<String, String> names) throws IOException {
    ByteBuffer file = ByteBuffer.wrap(input.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
    if (file.getInt() != 0x46546c67 || file.getInt() != 2 || file.getInt() != file.limit())
      throw new IOException("Invalid GLB header");
    JsonObject json = null;
    ByteBuffer bin = null;
    while (file.remaining() >= 8) {
      int len = file.getInt(), tag = file.getInt();
      if (len < 0 || len > file.remaining()) throw new IOException("Invalid chunk length");
      if (tag == 0x4e4f534a) {
        byte[] raw = new byte[len];
        file.get(raw);
        json = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
      } else {
        if (tag == 0x004e4942) bin = file.slice().order(ByteOrder.LITTLE_ENDIAN).limit(len);
        file.position(file.position() + len);
      }
    }
    if (json == null || bin == null) throw new IOException("Missing GLB chunk");
    doc = json;
    binary = bin;
    nodes = new Node[arr("nodes").size()];
    for (int i = 0; i < nodes.length; i++) {
      var j = obj("nodes", i);
      Node n = nodes[i] = new Node();
      n.name = j.has("name") ? j.get("name").getAsString() : "node_" + i;
      n.role = names.getOrDefault(n.name, n.name);
      TransportTruck.LOG.info("Truck node {}: {} ({})", i, n.name, n.role);
      n.mesh = num(j, "mesh", -1);
      n.skin = num(j, "skin", -1);
      n.children = j.has("children") ? ints(j.getAsJsonArray("children")) : new int[0];
      if (j.has("matrix")) {
        var m = new Matrix4f().set(values(j, "matrix"));
        m.getTranslation(n.baseT);
        m.getScale(n.baseS);
        m.getUnnormalizedRotation(n.baseR).normalize();
      } else {
        n.baseT.set(values(j, "translation", 0, 0, 0));
        n.baseS.set(values(j, "scale", 1, 1, 1));
        float[] q = values(j, "rotation", 0, 0, 0, 1);
        n.baseR.set(q[0], q[1], q[2], q[3]);
      }
      n.reset();
    }
    for (int i = 0; i < nodes.length; i++) for (int c : nodes[i].children) nodes[c].parent = i;
    update();
    for (Node n : nodes) n.rest.set(n.world);
    for (JsonElement e : arr("animations")) {
      var j = e.getAsJsonObject();
      String name = j.get("name").getAsString();
      List<Track> tracks = new ArrayList<>();
      float end = 0;
      for (JsonElement ch : j.getAsJsonArray("channels")) {
        var c = ch.getAsJsonObject();
        var samp = j.getAsJsonArray("samplers").get(num(c, "sampler", -1)).getAsJsonObject();
        if (samp.has("interpolation")
            && samp.get("interpolation").getAsString().equals("CUBICSPLINE")) continue;
        Track t = new Track(c, samp);
        tracks.add(t);
        end = Math.max(end, t.time.get(t.time.count - 1, 0));
      }
      clips.put(name, new Clip(name, tracks, end));
    }
  }

  void update() {
    for (Node n : nodes) if (n.parent < 0) update(n);
  }

  private void update(Node n) {
    if (n.parent < 0) n.world.identity();
    else n.world.set(nodes[n.parent].world);
    n.world.translate(n.t).rotate(n.r).scale(n.s);
    for (int c : n.children) update(nodes[c]);
  }
}
