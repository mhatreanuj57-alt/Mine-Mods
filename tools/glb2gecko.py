import argparse
import io
import json
import math
import struct
from pathlib import Path

from PIL import Image


COMPONENTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}
COMPONENT_FORMATS = {5120: "b", 5121: "B", 5122: "h", 5123: "H", 5125: "I", 5126: "f"}
COMPONENT_SIZES = {5120: 1, 5121: 1, 5122: 2, 5123: 2, 5125: 4, 5126: 4}
IMAGE_EXTENSIONS = {"image/png": "png", "image/jpeg": "jpg"}
CELL = 32
ATLAS_COLUMNS = 8


def load_glb(path):
    data = Path(path).read_bytes()
    if len(data) < 20:
        raise ValueError("GLB is shorter than its header and JSON chunk")
    magic, version, total_length = struct.unpack_from("<III", data)
    if magic != 0x46546C67 or version != 2 or total_length != len(data):
        raise ValueError("invalid GLB header or length")
    json_data = None
    binary_chunks = []
    offset = 12
    while offset < total_length:
        if offset + 8 > total_length:
            raise ValueError("truncated GLB chunk header")
        length, kind = struct.unpack_from("<II", data, offset)
        offset += 8
        chunk = data[offset:offset + length]
        if len(chunk) != length:
            raise ValueError("truncated GLB chunk")
        offset += length
        if kind == 0x4E4F534A:
            json_data = json.loads(chunk.decode("utf-8").rstrip(" \t\r\n\0"))
        elif kind == 0x004E4942:
            binary_chunks.append(chunk)
    if json_data is None:
        raise ValueError("GLB contains no JSON chunk")
    return json_data, binary_chunks


class Converter:
    def __init__(self, gltf, binary_chunks, out, flip_x=False, flip_rotation=False):
        self.gltf = gltf
        self.binary = binary_chunks
        self.out = Path(out)
        self.flip_x = flip_x
        self.flip_rotation = flip_rotation
        if gltf.get("skins"):
            raise ValueError("skinned GLB meshes are not supported")
        if "KHR_draco_mesh_compression" in gltf.get("extensionsRequired", []):
            raise ValueError("Draco-compressed GLB is not supported")
        self.nodes = gltf.get("nodes", [])
        self.parents = {child: parent for parent, node in enumerate(self.nodes)
                        for child in node.get("children", [])}
        self.roots = [i for i in range(len(self.nodes)) if i not in self.parents]
        self.node_rest = {}
        self.world_matrices = {}
        self.material_uv = {}
        self.material_wrap = {}
        self.atlas_size = (0, 0)
        self.generated_position_keys = 0

    def read_accessor(self, index):
        accessor = self.gltf["accessors"][index]
        if "sparse" in accessor or "bufferView" not in accessor:
            raise ValueError(f"unsupported sparse or missing accessor {index}")
        view = self.gltf["bufferViews"][accessor["bufferView"]]
        chunk = self.binary[view.get("buffer", 0)]
        size = COMPONENT_SIZES[accessor["componentType"]]
        fmt = COMPONENT_FORMATS[accessor["componentType"]]
        count = COMPONENTS[accessor["type"]]
        stride = view.get("byteStride", size * count)
        start = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
        values = []
        for i in range(accessor["count"]):
            parts = struct.unpack_from("<" + fmt * count, chunk, start + i * stride)
            values.append(parts[0] if count == 1 else parts)
        return values

    @staticmethod
    def quat_matrix(q):
        x, y, z, w = q
        length = math.sqrt(x*x + y*y + z*z + w*w)
        if length == 0:
            raise ValueError("zero-length rotation quaternion")
        x, y, z, w = x/length, y/length, z/length, w/length
        return [
            [1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w)],
            [2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w)],
            [2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y)],
        ]

    @staticmethod
    def matrix_mul(a, b):
        return [[sum(a[r][k] * b[k][c] for k in range(3)) for c in range(3)] for r in range(3)]

    @staticmethod
    def matrix_vec(a, v):
        return [sum(a[r][c] * v[c] for c in range(3)) for r in range(3)]

    @staticmethod
    def matrix_to_euler_zyx(r):
        beta = math.asin(max(-1.0, min(1.0, -r[2][0])))
        if abs(math.cos(beta)) > 1e-8:
            alpha = math.atan2(r[2][1], r[2][2])
            gamma = math.atan2(r[1][0], r[0][0])
        else:
            alpha = math.atan2(-r[1][2], r[1][1])
            gamma = 0.0
        return alpha, beta, gamma

    @staticmethod
    def solve3(a, b):
        matrix = [list(a[i]) + [b[i]] for i in range(3)]
        for col in range(3):
            pivot = max(range(col, 3), key=lambda row: abs(matrix[row][col]))
            if abs(matrix[pivot][col]) < 1e-10:
                return list(b)
            matrix[col], matrix[pivot] = matrix[pivot], matrix[col]
            scale = matrix[col][col]
            matrix[col] = [x / scale for x in matrix[col]]
            for row in range(3):
                if row == col:
                    continue
                factor = matrix[row][col]
                matrix[row] = [matrix[row][j] - factor * matrix[col][j] for j in range(4)]
        return [matrix[i][3] for i in range(3)]

    def rest_for_node(self, index, parent_scale):
        node = self.nodes[index]
        if "matrix" in node:
            raise ValueError(f"node {index} uses a matrix transform; TRS is required")
        translation = node.get("translation", [0, 0, 0])
        scale = node.get("scale", [1, 1, 1])
        quat = node.get("rotation", [0, 0, 0, 1])
        rotation = self.quat_matrix(quat)
        euler = self.matrix_to_euler_zyx(rotation)
        scaled_translation = [parent_scale * x for x in translation]
        if max(abs(rotation[i][j] - (1 if i == j else 0)) for i in range(3) for j in range(3)) < 1e-8:
            pivot = scaled_translation
        else:
            system = [[(1 if i == j else 0) - rotation[i][j] for j in range(3)] for i in range(3)]
            pivot = self.solve3(system, scaled_translation)
        rest = {"translation": translation, "scale": scale, "quat": quat,
                "rotation": rotation, "euler": euler, "pivot": pivot,
                "parent_scale": parent_scale}
        self.node_rest[index] = rest
        return rest

    def image_bytes(self, image):
        view = self.gltf["bufferViews"][image["bufferView"]]
        chunk = self.binary[view.get("buffer", 0)]
        start, length = view.get("byteOffset", 0), view["byteLength"]
        return chunk[start:start + length]

    def build_atlas(self):
        materials = self.gltf.get("materials", [])
        slots = max(1, len(materials))
        rows = 1 << (math.ceil(math.log2(math.ceil(slots / ATLAS_COLUMNS))))
        width, height = ATLAS_COLUMNS * CELL, rows * CELL
        atlas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        images = self.gltf.get("images", [])
        textures = self.gltf.get("textures", [])
        for index in range(slots):
            material = materials[index] if index < len(materials) else {}
            pbr = material.get("pbrMetallicRoughness", {})
            factor = pbr.get("baseColorFactor", [1, 1, 1, 1])
            tex_info = pbr.get("baseColorTexture")
            if tex_info is not None:
                texture = textures[tex_info["index"]]
                image_index = texture["source"]
                sampler_index = texture.get("sampler")
                sampler = self.gltf.get("samplers", [])[sampler_index] if sampler_index is not None else {}
                self.material_wrap[index] = (sampler.get("wrapS", 10497), sampler.get("wrapT", 10497))
                source = Image.open(io.BytesIO(self.image_bytes(images[image_index]))).convert("RGBA")
                tile = source.resize((CELL - 2, CELL - 2), Image.Resampling.NEAREST)
                channels = tile.split()
                tile = Image.merge("RGBA", tuple(
                    channel.point(lambda value, f=component: max(0, min(255, round(value * f))))
                    for channel, component in zip(channels, factor)
                ))
            else:
                self.material_wrap[index] = (33071, 33071)
                color = tuple(max(0, min(255, round(component * 255))) for component in factor)
                tile = Image.new("RGBA", (CELL - 2, CELL - 2), color)
            col, row = index % ATLAS_COLUMNS, index // ATLAS_COLUMNS
            atlas.paste(tile, (col * CELL + 1, row * CELL + 1))
            self.material_uv[index] = (col, row)
        self.atlas_size = atlas.size
        path = self.out / "src/main/resources/assets/custom_jetpack/textures/models/jetpack_atlas.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        atlas.save(path)

    def atlas_uv(self, material, uv):
        col, row = self.material_uv.get(material, self.material_uv.get(0, (0, 0)))
        wrap_s, wrap_t = self.material_wrap.get(material, (33071, 33071))
        def wrap(value, mode):
            if mode == 33071:
                return max(0.0, min(1.0, value))
            if mode == 33648:
                return 1.0 - abs(value % 2.0 - 1.0)
            return value % 1.0
        u = col * CELL + 1.5 + wrap(uv[0], wrap_s) * (CELL - 3)
        v = row * CELL + 1.5 + wrap(uv[1], wrap_t) * (CELL - 3)
        return [u / self.atlas_size[0], v / self.atlas_size[1]]

    def build_poly_mesh(self, node_index, parent_scale):
        node = self.nodes[node_index]
        mesh = self.gltf["meshes"][node["mesh"]]
        if len(mesh.get("primitives", [])) != 1:
            raise ValueError(f"node {node_index} mesh must contain one material primitive")
        primitive = mesh["primitives"][0]
        if primitive.get("mode", 4) != 4:
            raise ValueError(f"node {node_index} primitive must use TRIANGLES mode")
        attrs = primitive["attributes"]
        positions = self.read_accessor(attrs["POSITION"])
        normals = self.read_accessor(attrs["NORMAL"])
        uvs = self.read_accessor(attrs["TEXCOORD_0"])
        scale = node.get("scale", [1, 1, 1])
        effective = [parent_scale * scale[i] for i in range(3)]
        transformed_positions = [[v[i] * effective[i] for i in range(3)] for v in positions]
        transformed_normals = []
        for normal in normals:
            value = [normal[i] / effective[i] if abs(effective[i]) > 1e-12 else normal[i] for i in range(3)]
            length = math.sqrt(sum(x*x for x in value)) or 1.0
            transformed_normals.append([x / length for x in value])
        material = primitive.get("material", 0)
        transformed_uvs = [self.atlas_uv(material, uv) for uv in uvs]
        indices = self.read_accessor(primitive["indices"]) if "indices" in primitive else list(range(len(positions)))
        if len(indices) % 3:
            raise ValueError(f"node {node_index} triangle index count is not divisible by 3")
        polys = []
        for start in range(0, len(indices), 3):
            polys.append([[int(i), int(i), int(i)] for i in indices[start:start + 3]])
        return {
            "normalized_uvs": True,
            "positions": [x for v in transformed_positions for x in v],
            "normals": [x for v in transformed_normals for x in v],
            "uvs": [x for v in transformed_uvs for x in v],
            "polys": polys,
            "type": "TRI",
        }

    def build_geo(self):
        bones = []

        def make_bone(index, parent_scale, parent_name=None):
            node = self.nodes[index]
            rest = self.rest_for_node(index, parent_scale)
            alpha, beta, gamma = rest["euler"]
            rotation = [alpha, beta, -gamma]
            pivot = list(rest["pivot"])
            if self.flip_x:
                pivot[0] *= -1
                rotation = [-alpha, -beta, gamma]
            if self.flip_rotation:
                rotation = [-v for v in rotation]
            bone = {
                "name": node.get("name") or f"node{index}",
                "pivot": pivot,
                "rotation": [math.degrees(x) for x in rotation],
                "cubes": [],
            }
            if parent_name is not None:
                bone["parent"] = parent_name
            if "mesh" in node:
                bone["poly_mesh"] = self.build_poly_mesh(index, parent_scale)
            bones.append(bone)
            children = node.get("children", [])
            if children:
                if max(rest["scale"]) - min(rest["scale"]) > 1e-8:
                    raise ValueError(f"node {index} has non-uniform scale and children")
                child_scale = parent_scale * rest["scale"][0]
                for child in children:
                    make_bone(child, child_scale, bone["name"])

        for root in self.roots:
            make_bone(root, 16.0)  # Convert GLTF block units to GeckoLib model pixels.
        description = {
            "identifier": "geometry.custom_jetpack",
            "texture_width": self.atlas_size[0],
            "texture_height": self.atlas_size[1],
            "visible_bounds_width": 2.0,
            "visible_bounds_height": 2.0,
            "visible_bounds_offset": [0, 1, 0],
        }
        self.write_json("src/main/resources/assets/custom_jetpack/geo/models/jetpack.geo.json", {
            "format_version": "1.12.0",
            "minecraft:geometry": [{"description": description, "bones": bones}],
        })

    @staticmethod
    def quat_slerp(a, b, factor):
        dot = sum(x*y for x, y in zip(a, b))
        if dot < 0:
            b = [-x for x in b]
            dot = -dot
        if dot > 0.9995:
            q = [x + factor*(y-x) for x, y in zip(a, b)]
            length = math.sqrt(sum(x*x for x in q)) or 1.0
            return [x/length for x in q]
        theta = math.acos(max(-1.0, min(1.0, dot)))
        scale = math.sin(theta)
        x = math.sin((1-factor)*theta) / scale
        y = math.sin(factor*theta) / scale
        return [x*a[i] + y*b[i] for i in range(4)]

    def sample_track(self, track, time, path):
        times, values, interpolation = track
        if time <= times[0]:
            return list(values[0])
        if time >= times[-1]:
            return list(values[-1])
        for i in range(len(times) - 1):
            if times[i] <= time <= times[i + 1]:
                if interpolation == "STEP":
                    return list(values[i])
                if interpolation != "LINEAR":
                    raise ValueError(f"unsupported {interpolation} animation interpolation")
                amount = (time - times[i]) / (times[i + 1] - times[i])
                if path == "rotation":
                    return self.quat_slerp(values[i], values[i + 1], amount)
                return [a + (b-a)*amount for a, b in zip(values[i], values[i + 1])]
        return list(values[-1])

    @staticmethod
    def unwrap(angle, reference):
        while angle - reference > math.pi:
            angle -= 2 * math.pi
        while angle - reference < -math.pi:
            angle += 2 * math.pi
        return angle

    @classmethod
    def unwrap_euler(cls, angles, reference):
        alpha, beta, gamma = angles
        candidates = [
            (alpha, beta, gamma),
            (alpha + math.pi, math.pi - beta, gamma + math.pi),
        ]
        candidates = [tuple(cls.unwrap(value, reference[i]) for i, value in enumerate(candidate))
                      for candidate in candidates]
        return min(candidates, key=lambda candidate: sum((candidate[i] - reference[i])**2 for i in range(3)))

    def transform_position(self, rest, translation, scale, quat):
        rotation = self.quat_matrix(quat)
        ratio = [scale[i] / rest["scale"][i] if abs(rest["scale"][i]) > 1e-12 else 1.0 for i in range(3)]
        rotation_scale = [[rotation[r][c] * ratio[c] for c in range(3)] for r in range(3)]
        delta = [[(1 if r == c else 0) - rotation_scale[r][c] for c in range(3)] for r in range(3)]
        offset = self.matrix_vec(delta, rest["pivot"])
        result = [rest["parent_scale"] * translation[i] - offset[i] for i in range(3)]
        if self.flip_x:
            result[0] *= -1
        return result

    def build_animations(self):
        animations = {}
        for animation in self.gltf.get("animations", []):
            channels_by_node = {}
            for channel in animation.get("channels", []):
                node_index = channel["target"]["node"]
                path = channel["target"]["path"]
                sampler = animation["samplers"][channel["sampler"]]
                if sampler.get("interpolation", "LINEAR") == "CUBICSPLINE":
                    raise ValueError("CUBICSPLINE animation interpolation is not supported")
                times = self.read_accessor(sampler["input"])
                values = self.read_accessor(sampler["output"])
                if path in channels_by_node.setdefault(node_index, {}):
                    raise ValueError(f"duplicate {path} animation channel for node {node_index}")
                channels_by_node[node_index][path] = (times, values, sampler.get("interpolation", "LINEAR"))

            bones = {}
            max_time = 0.0
            for node_index, tracks in channels_by_node.items():
                rest = self.node_rest.get(node_index)
                if rest is None:
                    raise ValueError(f"animated node {node_index} is missing from the node hierarchy")
                times = sorted({time for track in tracks.values() for time in track[0]})
                max_time = max(max_time, times[-1])
                translation_keys, scale_keys, eulers = [], [], []
                for time in times:
                    translation = self.sample_track(tracks["translation"], time, "translation") if "translation" in tracks else list(rest["translation"])
                    scale = self.sample_track(tracks["scale"], time, "scale") if "scale" in tracks else list(rest["scale"])
                    quat = self.sample_track(tracks["rotation"], time, "rotation") if "rotation" in tracks else list(rest["quat"])
                    euler = self.matrix_to_euler_zyx(self.quat_matrix(quat))
                    eulers.append(euler)
                    ratio = [scale[i] / rest["scale"][i] if abs(rest["scale"][i]) > 1e-12 else 1.0 for i in range(3)]
                    scale_keys.append((time, ratio))
                    translation_keys.append((time, self.transform_position(rest, translation, scale, quat)))

                bone_tracks = {}
                if "scale" in tracks:
                    bone_tracks["scale"] = {self.format_time(t): [round(v, 9) for v in values] for t, values in scale_keys}
                if "rotation" in tracks:
                    previous = rest["euler"]
                    for i, (time, euler) in enumerate(zip(times, eulers)):
                        eulers[i] = self.unwrap_euler(euler, previous)
                        previous = eulers[i]
                    rotation_values = []
                    for euler in eulers:
                        rotation_values.append([euler[0]-rest["euler"][0], euler[1]-rest["euler"][1], -(euler[2]-rest["euler"][2])])
                    if self.flip_x:
                        rotation_values = [[-v[0], -v[1], -v[2]] for v in rotation_values]
                    if self.flip_rotation:
                        rotation_values = [[-v for v in row] for row in rotation_values]
                    bone_tracks["rotation"] = {self.format_time(t): [round(math.degrees(v), 6) for v in values] for t, values in zip(times, rotation_values)}
                transform_changes = any(
                    max(abs(scale_keys[i][1][axis] - 1) for axis in range(3)) > 1e-7
                    or max(abs(rotation_values[i][axis]) for axis in range(3)) > 1e-7
                    for i in range(len(times))
                ) if "rotation" in tracks else any(
                    max(abs(scale_keys[i][1][axis] - 1) for axis in range(3)) > 1e-7
                    for i in range(len(times))
                )
                position_changes = "translation" in tracks or (
                    transform_changes and max(abs(value) for value in rest["pivot"]) > 1e-7
                )
                if position_changes:
                    bone_tracks["position"] = {self.format_time(t): [round(v, 9) for v in values] for t, values in translation_keys}
                    self.generated_position_keys += len(translation_keys)
                bones[self.node_name(node_index)] = bone_tracks

            name = animation.get("name", "animation")
            animations[name] = {"animation_length": round(max_time, 6), "loop": True, "bones": bones}
        self.write_json("src/main/resources/assets/custom_jetpack/animations/jetpack.animation.json", {
            "format_version": "1.8.0", "animations": animations,
        })

    @staticmethod
    def format_time(value):
        return format(float(value), ".8g")

    def node_name(self, index):
        return self.nodes[index].get("name") or f"node{index}"

    def local_matrix(self, node):
        if "matrix" in node:
            raw = node["matrix"]
            return [[raw[c*4+r] for c in range(4)] for r in range(4)]
        rotation = self.quat_matrix(node.get("rotation", [0, 0, 0, 1]))
        scale = node.get("scale", [1, 1, 1])
        translation = node.get("translation", [0, 0, 0])
        return [[rotation[r][c] * scale[c] for c in range(3)] + [translation[r]] for r in range(3)] + [[0, 0, 0, 1]]

    @staticmethod
    def matrix4_mul(a, b):
        return [[sum(a[r][k]*b[k][c] for k in range(4)) for c in range(4)] for r in range(4)]

    def build_world_matrices(self):
        def walk(index, parent):
            matrix = self.matrix4_mul(parent, self.local_matrix(self.nodes[index]))
            self.world_matrices[index] = matrix
            for child in self.nodes[index].get("children", []):
                walk(child, matrix)
        identity = [[1 if r == c else 0 for c in range(4)] for r in range(4)]
        for root in self.roots:
            walk(root, identity)

    def emit_model_data(self):
        emissive_materials = set()
        for i, material in enumerate(self.gltf.get("materials", [])):
            factor = material.get("emissiveFactor", [0, 0, 0])
            if any(abs(value) > 1e-8 for value in factor) or "KHR_materials_unlit" in material.get("extensions", {}):
                emissive_materials.add(i)
        emissive_nodes, nozzle_points = [], []
        for index, node in enumerate(self.nodes):
            if "mesh" not in node:
                continue
            materials = {primitive.get("material", 0) for primitive in self.gltf["meshes"][node["mesh"]].get("primitives", [])}
            if materials & emissive_materials:
                if materials - emissive_materials:
                    raise ValueError(f"node {index} mixes emissive and non-emissive primitives")
                emissive_nodes.append(self.node_name(index))
            if any("nozzle" in self.gltf.get("materials", [])[m].get("name", "").lower() for m in materials if m < len(self.gltf.get("materials", []))):
                positions = self.read_accessor(self.gltf["meshes"][node["mesh"]]["primitives"][0]["attributes"]["POSITION"])
                matrix = self.world_matrices[index]
                for point in positions:
                    nozzle_points.append([sum(matrix[r][c] * point[c] for c in range(3)) + matrix[r][3] for r in range(3)])
        if not nozzle_points:
            raise ValueError("no nozzle geometry found for ModelData")
        groups = [[point for point in nozzle_points if point[2] * sign > 1e-6] for sign in (-1, 1)]
        if all(groups):
            nozzles = []
            for group in groups:
                bottom = min(point[1] for point in group)
                edge = [point for point in group if point[1] <= bottom + 0.01]
                nozzles.append([sum(p[i] for p in edge) / len(edge) for i in range(3)])
        else:
            # shortcut: assumes mirrored nozzle positions from the sole emissive ring, upgrade when the source GLB exposes both nozzle anchors.
            bottom = min(point[1] for point in nozzle_points)
            edge = [point for point in nozzle_points if point[1] <= bottom + 0.01]
            anchor = [sum(p[i] for p in edge) / len(edge) for i in range(3)]
            nozzles = [[anchor[0], anchor[1], -abs(anchor[2])],
                       [anchor[0], anchor[1], abs(anchor[2])]]
        self.out.joinpath("src/main/java/com/customjetpack/data/ModelData.java").parent.mkdir(parents=True, exist_ok=True)
        def java_vec(v):
            return "new double[]{" + ", ".join(f"{x:.9g}" for x in v) + "}"
        source = "\n".join([
            "package com.customjetpack.data;", "", "/** Generated by tools/glb2gecko.py from the source GLB. */",
            "public final class ModelData {", "    private ModelData() {}",
            "    public static final String[] EMISSIVE_BONES = {" + ", ".join(json.dumps(n) for n in emissive_nodes) + "};",
            "    public static final double[] THRUSTER_L = " + java_vec(nozzles[0]) + ";",
            "    public static final double[] THRUSTER_R = " + java_vec(nozzles[1]) + ";", "}", "",
        ])
        (self.out / "src/main/java/com/customjetpack/data/ModelData.java").write_text(source, encoding="utf-8")

    def write_json(self, relative, value):
        path = self.out / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, separators=(",", ":"), ensure_ascii=False) + "\n", encoding="utf-8")

    def run(self):
        self.out.mkdir(parents=True, exist_ok=True)
        self.build_world_matrices()
        self.build_atlas()
        self.build_geo()
        self.build_animations()
        self.emit_model_data()
        triangles = 0
        for node in self.nodes:
            if "mesh" not in node:
                continue
            for primitive in self.gltf["meshes"][node["mesh"]].get("primitives", []):
                count = len(self.read_accessor(primitive["indices"])) if "indices" in primitive else len(self.read_accessor(primitive["attributes"]["POSITION"]))
                triangles += count // 3
        print(f"Generated {len(self.nodes)} bones, {triangles} triangles, animations: {', '.join(a.get('name', 'animation') for a in self.gltf.get('animations', []))}; generated {self.generated_position_keys} position keys.")


def main():
    parser = argparse.ArgumentParser(description="Convert a GLB model into GeckoLib assets")
    parser.add_argument("glb_path", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--flip-x", action="store_true", help="use the G=I coordinate escape hatch")
    parser.add_argument("--flip-rotation", action="store_true", help="invert emitted rotations")
    args = parser.parse_args()
    gltf, binary_chunks = load_glb(args.glb_path)
    Converter(gltf, binary_chunks, args.out, args.flip_x, args.flip_rotation).run()


if __name__ == "__main__":
    main()
