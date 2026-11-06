import json, subprocess, sys, tempfile, unittest
from pathlib import Path
from PIL import Image
from tools.glb2gecko import Converter, load_glb

ROOT = Path(__file__).resolve().parent.parent
GLB = ROOT / "assets-src" / "minecraft_jetpack.glb"
CONV = ROOT / "tools" / "glb2gecko.py"

class ConvertTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.out = Path(tempfile.mkdtemp(prefix="geo_out_"))
        r = subprocess.run([sys.executable, str(CONV), str(GLB), "--out", str(cls.out)],
                           capture_output=True, text=True)
        assert r.returncode == 0, f"converter failed:\n{r.stdout}\n{r.stderr}"

    def load(self, rel):
        return json.loads((self.out / rel).read_text(encoding="utf-8"))

    @classmethod
    def walk_bones(cls, bones):
        yield from bones

    def test_geo_counts(self):
        geo = self.load("src/main/resources/assets/custom_jetpack/geo/models/jetpack.geo.json")["minecraft:geometry"][0]
        bones = list(self.walk_bones(geo["bones"]))
        self.assertEqual(len(bones), 103)
        names = {bone["name"] for bone in bones}
        for bone in bones:
            if "parent" in bone:
                self.assertIn(bone["parent"], names)
        verts = poly_bones = 0
        for b in bones:
            self.assertIn("cubes", b)          # cubes: [] always present (Builtin NPE guard)
            self.assertEqual(b["cubes"], [])
            pm = b.get("poly_mesh")
            if pm is None:
                continue
            poly_bones += 1
            self.assertEqual(set(pm), {"normalized_uvs", "positions", "normals", "uvs", "polys", "type"})
            self.assertEqual(pm["type"], "TRI")
            self.assertEqual(len(pm["positions"]) % 3, 0)
            self.assertEqual(len(pm["positions"]) // 3, len(pm["normals"]) // 3)
            self.assertEqual(len(pm["uvs"]) // 2, len(pm["positions"]) // 3)
            self.assertTrue(all(0 <= uv <= 1 for uv in pm["uvs"]))
            verts += len(pm["positions"]) // 3
            for poly in pm["polys"]:
                self.assertEqual(len(poly), 3)          # triangulated
                for pi, ni, ui in poly:
                    self.assertGreaterEqual(pi, 0)
        self.assertEqual(verts, 21425)   # one instance of each of the 90 referenced meshes
        tris = sum(len(b["poly_mesh"]["polys"]) for b in bones if b.get("poly_mesh"))
        self.assertEqual(tris, 22876)
        self.assertEqual(poly_bones, 90)

    def test_animation_clips(self):
        anim = self.load("src/main/resources/assets/custom_jetpack/animations/jetpack.animation.json")
        clips = anim["animations"]
        self.assertEqual({c.lower() for c in clips}, {"idle", "flight"})

        def collect_types(obj, acc):
            """Find GeckoLib bone transform tracks in standard time-keyed form."""
            if isinstance(obj, dict):
                for k, v in obj.items():
                    if k in ("position", "rotation", "scale") and isinstance(v, (dict, list, int, float)):
                        acc.add(k)
                    collect_types(v, acc)
            elif isinstance(obj, list):
                for v in obj:
                    collect_types(v, acc)
            return acc

        def find_bone(obj, needle):
            if isinstance(obj, dict):
                for k, v in obj.items():
                    if needle in k.lower():
                        return v
                    r = find_bone(v, needle)
                    if r is not None:
                        return r
            elif isinstance(obj, list):
                for v in obj:
                    r = find_bone(v, needle)
                    if r is not None:
                        return r
            return None

        def max_time(obj, acc):
            if isinstance(obj, dict):
                if isinstance(obj.get("animation_length"), (int, float)):
                    acc[0] = max(acc[0], obj["animation_length"])
                for value in obj.values():
                    max_time(value, acc)
            elif isinstance(obj, list):
                for value in obj:
                    max_time(value, acc)
            return acc[0]

        idle = clips.get("Idle") or clips.get("idle")
        flight = clips.get("Flight") or clips.get("flight")
        # Idle: exactly one track, type == scale (ExhaustFX)
        self.assertEqual(collect_types(idle, set()), {"scale"})
        self.assertLessEqual(max_time(idle, [0.0]), 2.0)   # GLTF seconds pass-through (a Ã—20 bug â†’ >= 20)
        # Flight: all three track types; fan has rotation + GENERATED position (glTF fan has rotation only)
        self.assertEqual(collect_types(flight, set()), {"position", "rotation", "scale"})
        fan = find_bone(flight, "fan")
        self.assertIsNotNone(fan)
        self.assertEqual(collect_types(fan, set()), {"rotation", "position"})
        rotations = [fan["rotation"][key] for key in sorted(fan["rotation"], key=float)]
        self.assertLessEqual(max(sum((a-b)**2 for a, b in zip(rotations[i], rotations[i-1]))**0.5
                                 for i in range(1, len(rotations))), 25)

    def test_atlas_exists(self):
        p = self.out / "src/main/resources/assets/custom_jetpack/textures/models/jetpack_atlas.png"
        self.assertTrue(p.exists() and p.stat().st_size > 0)
        with Image.open(p) as atlas:
            self.assertGreater(len(atlas.convert("RGBA").getcolors(atlas.width * atlas.height) or []), 1)

    def test_texture_sampler_wrap(self):
        gltf, binary = load_glb(GLB)
        converter = Converter(gltf, binary, self.out)
        converter.atlas_size = (256, 128)
        converter.material_uv = {0: (0, 0)}
        converter.material_wrap = {0: (33071, 33071)}
        edge = converter.atlas_uv(0, (1, 0))
        self.assertEqual(converter.atlas_uv(0, (1.1, -0.1)), edge)
        converter.material_wrap[0] = (10497, 10497)
        self.assertNotEqual(converter.atlas_uv(0, (1.1, -0.1)), edge)

    def test_model_data(self):
        src = (self.out / "src/main/java/com/customjetpack/data/ModelData.java").read_text()
        self.assertIn("EMISSIVE_BONES", src)
        self.assertIn("THRUSTER", src)

if __name__ == "__main__":
    unittest.main()
