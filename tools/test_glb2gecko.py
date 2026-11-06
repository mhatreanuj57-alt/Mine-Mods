import json, subprocess, sys, tempfile, unittest
from pathlib import Path

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
        for b in bones:
            yield b
            yield from cls.walk_bones(b.get("children", []))

    def test_geo_counts(self):
        geo = self.load("assets/customjetpack/geo/models/jetpack.geo.json")
        bones = list(self.walk_bones(geo["bones"]))
        self.assertEqual(len(bones), 103)
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
        anim = self.load("assets/customjetpack/animations/jetpack.animation.json")
        clips = anim["animations"]
        self.assertEqual({c.lower() for c in clips}, {"idle", "flight"})

        def collect_types(obj, acc):
            """Find every track key ({position,rotation,scale} objects that carry keyframes)."""
            if isinstance(obj, dict):
                for k, v in obj.items():
                    if k in ("position", "rotation", "scale") and isinstance(v, dict) and "keyframes" in v:
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
                if "time" in obj and isinstance(obj["time"], (int, float)):
                    acc[0] = max(acc[0], obj["time"])
                for v in obj.values():
                    max_time(v, acc)
            elif isinstance(obj, list):
                for v in obj:
                    max_time(v, acc)
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

    def test_atlas_exists(self):
        p = self.out / "assets/customjetpack/textures/models/jetpack_atlas.png"
        self.assertTrue(p.exists() and p.stat().st_size > 0)

    def test_model_data(self):
        src = (self.out / "src/main/java/com/customjetpack/data/ModelData.java").read_text()
        self.assertIn("EMISSIVE_BONES", src)
        self.assertIn("THRUSTER", src)

if __name__ == "__main__":
    unittest.main()
