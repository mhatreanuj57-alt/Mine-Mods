# Custom Jetpack Mod — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wearable jetpack mod for Minecraft 1.20.1 Forge: Curios back-slot item rendered from the user's real GLB model (no placeholder cubes), H-toggle flight, FE energy, thruster particles/sounds, recipe.

**Architecture:** Single Gradle project. GeckoLib 4.8.4 renders the converted GLB (`geo` + `animations` + atlas); Curios 5.14.1 equips it on the back; a C2S `flight_input` / S2C `flight_state` SimpleChannel syncs input + engaged/energy; server applies movement in `PlayerTickEvent`; client drives animations via a render-context holder, spawns particles from synced state, loops a synthesized thrust sound, and draws an energy HUD.

**Tech Stack:** Forge 1.20.1-47.4.28 (Gradle 8.8, official mappings, toolchain 17 via foojay), GeckoLib 4.8.4 (+ mclib:20), Curios 5.14.1, Java 17, Python 3.13 (PIL 12.3) + bash/ffmpeg 9.0 for asset tooling, git/gh authenticated as `mhatreanuj57-alt`.

**Repo:** `C:\Users\ADMIN\Projects\jetpack-mod` → GitHub `mhatreanuj57-alt` / "Mine Mods" (created in Task 7; if GitHub rejects the space, use `Mine-Mods` and tell the user).
**Commit rule (user requirement):** every file gets its own individual commit. Never `git add -A`. Commit messages: `chore(scaffold|mod|fx|assets|tools): <file> — <what>` / `feat(...): ...` conventional style. Push after every milestone.

**Approved spec:** `docs/superpowers/specs/2026-11-06-custom-jetpack-design.md` (source of truth for behavior, config keys, packets, milestones M0–M6).

---

## File structure map (target state)

```
jetpack-mod/
├── build.gradle                     # + geckolib/curios/mclib deps (Task 7)
├── gradle.properties                # mod_id etc. (Task 2)
├── settings.gradle, gradlew*, gradle/   # from MDK, untouched
├── .gitignore                       # from MDK or created (Task 5)
├── assets-src/minecraft_jetpack.glb # source model (Task 9)
├── tools/
│   ├── glb_analyze.py               # copied from Temp\opencode (Task 10)
│   ├── glb2gecko.py                 # converter (Tasks 11–14)
│   ├── test_glb2gecko.py            # self-test (Task 10)
│   ├── gen_particle_texture.py      # PIL flame sprite (M4)
│   └── synth_sounds.sh              # ffmpeg loop/start/stop oggs (M4)
└── src/main/
    ├── java/com/customjetpack/
    │   ├── JetpackMod.java          # @Mod, bus wiring (Task 4, edited per milestone)
    │   ├── JetpackConfig.java       # SERVER+CLIENT (M3)
    │   ├── ModItems.java, ModParticles.java, ModSounds.java
    │   ├── data/ModelData.java      # converter-generated constants (emitted Task 15)
    │   ├── item/JetpackItem.java, JetpackGeoModel.java, JetpackItemRenderer.java
    │   ├── curios/JetpackCuriosRenderer.java, RenderContext.java (M2)
    │   ├── network/ModNetwork.java, FlightInputC2S.java, FlightStateS2C.java (M3)
    │   ├── client/ClientSetup.java (Task 16: factory register), PolyMeshBakedModelFactory.java (Task 16)
    │   ├── client/ClientState.java, KeyBindings.java, ClientEvents.java (M3)
    │   ├── server/FlightTick.java   # PlayerTickEvent flight core (M3)
    │   ├── client/ThrustSound.java  # TickableSound loop (M4)
    │   └── client/ClientFx.java     # particles spawn + sound mgmt + HUD (M4/M5)
    └── resources/
        ├── META-INF/mods.toml       # deps geckolib/curios (Task 3)
        ├── assets/custom_jetpack/
        │   ├── geo/models/jetpack.geo.json            # converter code Task 12, emitted Task 15
        │   ├── animations/jetpack.animation.json      # converter code Task 14, emitted Task 15
        │   ├── textures/models/jetpack_atlas.png      # converter code Task 13, emitted Task 15
        │   ├── textures/particle/jetpack_thruster.png # M4
        │   ├── particles/jetpack_thruster.json        # M4
        │   ├── sounds/*.ogg + sounds.json             # M4
        │   ├── models/item/jetpack.json               # Task 16 (builtin/entity)
        │   └── lang/en_us.json                        # Task 16 (+M3/M4/M5 keys)
        └── data/customjetpack/
            ├── recipes/jetpack.json                    # M5
            └── curios/tags/items/back.json             # M2 (slot tag)
```

---

## Task list

### M0 — Scaffold + repo
- [ ] **Task 1: Extract Forge MDK into project root** (preserve `docs/`)
- [ ] **Task 2: Configure `gradle.properties`**
- [ ] **Task 3: Configure `META-INF/mods.toml`**
- [ ] **Task 4: Create `JetpackMod.java`, remove ExampleMod/Config**
- [ ] **Task 5: Ensure `.gitignore`**
- [ ] **Task 6: `git init` + per-file commits**
- [ ] **Task 7: Create GitHub repo + push**
- [ ] **Task 8: Add `build.gradle` dependencies; `gradlew build` green**
- [ ] **Task 9: `runClient` boot gate (mod listed, no crash)**

### M1 — Model pipeline + item render
- [ ] **Task 10: Copy GLB to `assets-src/` + copy `glb_analyze.py`; write `test_glb2gecko.py` (red)**
- [ ] **Task 11: Converter skeleton `glb2gecko.py` (parse + CLI stubs)**
- [ ] **Task 12: Converter fill: bones + poly_mesh (TRI) + pivot/rotation math (G=M)**
- [ ] **Task 13: Converter fill: UV atlas bake (PIL)**
- [ ] **Task 14: Converter fill: animations (seconds, euler, generated position keys) + `ModelData.java` + main()**
- [ ] **Task 15: Run converter → resources (default flags); tests green**
- [ ] **Task 16: `PolyMeshBakedModelFactory` + `ClientSetup` + `JetpackItem`/`JetpackGeoModel`/`JetpackItemRenderer`/registration/lang/item-model**
- [ ] **Task 17: Render gate (runClient, `/give`, in-hand + GUI vs reference, G choice)**

### M2 — Curios back slot
- [ ] **Task 18: Slot tag `data/curios/tags/items/back.json`**
- [ ] **Task 19: `RenderContext` + `JetpackCuriosRenderer` + client registration**
- [ ] **Task 20: F5 gate + offset tuning; push**

### M3 — Flight core (MP-tested)
- [ ] **Task 21: `JetpackConfig` (SERVER+CLIENT) wired into `JetpackMod`**
- [ ] **Task 22: FE capability on jetpack stack (`JetpackEnergyStorage`)**
- [ ] **Task 23: `ModNetwork` + `FlightInputC2S` + `FlightStateS2C`**
- [ ] **Task 24: `KeyBindings` (H) + `ClientState` + input senders**
- [ ] **Task 25: `FlightTick` server: NBT engaged, movement, drain, grace, S2C send**
- [ ] **Task 26: Animation predicate wiring (RenderContext × ClientState)**
- [ ] **Task 27: MP test gate (runServer + 2nd client checklist); push**

### M4 — FX (particles + sound)
- [ ] **Task 28: `gen_particle_texture.py` + particle json + `textures/particle/`**
- [ ] **Task 29: `ModParticles` + `ThrusterProvider` + client spawn loop**
- [ ] **Task 30: `synth_sounds.sh` → 3 oggs + `sounds.json` + `ModSounds`**
- [ ] **Task 31: `ThrustSound` TickableSound loop + start/stop wiring**
- [ ] **Task 32: FX gate in-game; push**

### M5 — Content polish
- [ ] **Task 33: Recipe `recipes/jetpack.json`**
- [ ] **Task 34: Energy HUD overlay**
- [ ] **Task 35: Emissive render layer (converter-emitted bone list)**
- [ ] **Task 36: Lang + config-comment polish; push**

### M6 — Hardening
- [ ] **Task 37: Edge-case checklist (death, unequip, dim change, disconnect, spectator)**
- [ ] **Task 38: Release `gradlew build` + final push**

---

## M0 — Scaffold + repo

### Task 1: Extract Forge MDK into project root

**Files:** `build.gradle`, `gradle.properties`, `settings.gradle`, `gradlew`, `gradlew.bat`, `gradle/`, `src/`, `.gitignore` (if present)

Steps:
- [ ] 1.1 Copy MDK contents (already extracted at `C:\Users\ADMIN\AppData\Local\Temp\opencode\mdk`) into `C:\Users\ADMIN\Projects\jetpack-mod`, skipping files that would clobber `docs/`:
```powershell
$src = 'C:\Users\ADMIN\AppData\Local\Temp\opencode\mdk'
$dst = 'C:\Users\ADMIN\Projects\jetpack-mod'
Get-ChildItem $src -Force | ForEach-Object {
  $target = Join-Path $dst $_.Name
  if (Test-Path $target) { "SKIP (exists): $($_.Name)" }
  else { Copy-Item $_.FullName $target -Recurse -Force; "COPIED: $($_.Name)" }
}
```
- [ ] 1.2 Verify: `Test-Path "$dst\build.gradle"`, `Test-Path "$dst\src\main\java"` and `Test-Path "$dst\docs\superpowers"` all `True`.

**Expected:** MDK files present, `docs/` untouched. No commit yet (Task 6 commits).

### Task 2: Configure `gradle.properties`

**Files:** `gradle.properties` (edit)

- [ ] 2.1 Replace the MDK identity block (exact old→new):
```
mod_id=jetpack -> mod_id=custom_jetpack
mod_name=Example Mod -> mod_name=Custom Jetpack
mod_group_id=com.example -> mod_group_id=com.customjetpack
mod_authors=You -> mod_authors=mhatreanuj57-alt
mod_description=An example mod -> mod_description=Wearable jetpack: Curios back-slot flight with FE energy, thruster FX.
```
(`mod_version`, `minecraft_version=1.20.1`, `forge_version=47.4.28`, `loader_version_range`, `mod_license` keep MDK defaults.)

- [ ] 2.2 Commit: `git add gradle.properties` → `chore(scaffold): gradle.properties — mod identity custom_jetpack`

### Task 3: Configure `META-INF/mods.toml`

**Files:** `src/main/resources/META-INF/mods.toml` (rewrite), commit each logical change? — it is one file: single commit at end of task.

- [ ] 3.1 Rewrite `[[mods]]` block: `modId="custom_jetpack"`, `displayName="Custom Jetpack"`, `description` matching gradle.properties, `logoFile` omitted.
- [ ] 3.2 Keep `[[dependencies.custom_jetpack]]` for `minecraft` (1.20.1, mandatory, `*`) and `forge` (47.x, mandatory). Add mandatory deps:
```
[[dependencies.custom_jetpack]] modId="geckolib" versionRange="[4.8,4.9)" mandatory=true
[[dependencies.custom_jetpack]] modId="curios"    versionRange="[5.14,5.15)" mandatory=true
```
- [ ] 3.3 Commit: `chore(mod): mods.toml — modId custom_jetpack, geckolib/curios deps`

### Task 4: Create `JetpackMod.java`, remove example code

**Files:** `src/main/java/com/customjetpack/JetpackMod.java` (new), `src/main/java/com/example/ExampleMod.java` (delete), `src/main/java/com/example/Config.java` (delete)

- [ ] 4.1 Write `JetpackMod.java` — minimal: `@Mod("custom_jetpack")`, logger, constructor (registers deferred registers as they exist by this point: only none yet — constructor body registers nothing until Task 16 adds `ModItems.ITEMS.register(modBus)`; write constructor with a `// registers added per milestone` comment? NO placeholders — constructor simply empty except comment-free log line is fine? Write constructor that logs "Custom Jetpack loading" and nothing else; later tasks EDIT it to add registrations. Each edit = its own commit).
- [ ] 4.2 `git rm src/main/java/com/example/ExampleMod.java` → commit `chore(scaffold): remove ExampleMod`
- [ ] 4.3 `git rm src/main/java/com/example/Config.java` → commit `chore(scaffold): remove example Config`
- [ ] 4.4 `git add src/main/java/com/customjetpack/JetpackMod.java` → `feat(mod): JetpackMod entry point`

### Task 5: Ensure `.gitignore`

- [ ] 5.1 `Test-Path .gitignore` — if missing, create with: `build/`, `.gradle/`, `run/`, `.idea/`, `*.iml`, `.vscode/`, `out/`.
- [ ] 5.2 Commit if created/edited: `chore(scaffold): .gitignore`

### Task 6: `git init` + per-file commits

- [ ] 6.1 `git init -b main` (in project root).
- [ ] 6.2 Per-file commits (user requirement — one commit per file, no `git add -A`):
```powershell
Get-ChildItem -Recurse -File -Force |
  Where-Object { $_.FullName -notmatch '\\\.git\\' } |
  Sort-Object FullName |
  ForEach-Object {
    $rel = $_.FullName.Substring((Get-Location).Path.Length + 1) -replace '\\','/'
    git add -- $rel
    git commit -q -m "chore(scaffold): add $rel"
  }
```
- [ ] 6.3 Verify: `git log --oneline | Measure-Object -Line` ≥ number of copied files; `git status` clean.

### Task 7: Create GitHub repo + push

- [ ] 7.1 Confirm gh account: `gh auth status` → active as `mhatreanuj57-alt`.
- [ ] 7.2 Create + push (name may contain space; if 422 error, fall back to `Mine-Mods` and note it for the user):
```powershell
gh repo create "Mine Mods" --public --source . --remote origin --push
```
- [ ] 7.3 Verify: `git remote -v` shows origin under `mhatreanuj57-alt`; `gh repo view --json nameWithOwner -q .nameWithOwner` prints `mhatreanuj57-alt/...`.

### Task 8: `build.gradle` dependencies

**Files:** `build.gradle` (edit)

- [ ] 8.1 Add `repositories` block (after the MDK's existing blocks) and `dependencies` additions:
```gradle
repositories {
    maven { name 'GeckoLib';  url 'https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/' }
    maven { name 'Curios';    url 'https://maven.theillusivec4.top/' }
}
dependencies {
    minecraft 'net.minecraftforge:forge:1.20.1-47.4.28'   // keep MDK's line if already present
    implementation fg.deobf('software.bernie.geckolib:geckolib-forge-1.20.1:4.8.4')
    implementation fg.deobf('com.eliotlash.mclib:mclib:20')
    implementation fg.deobf('top.theillusivec4.curios:curios-forge:5.14.1+1.20.1')
}
```
(Verified: geckolib POM 200, mclib POM 200 on cloudsmith, curios POM 200 on theillusivec4. Merge into MDK's existing `dependencies { }` if present.)
- [ ] 8.2 Commit: `feat(build): geckolib 4.8.4 + mclib 20 + curios 5.14.1 deps`
- [ ] 8.3 First build (long: downloads MC/Forge/deps, may take 5–15 min):
```powershell
.\gradlew.bat build --no-daemon
```
**Expected:** `BUILD SUCCESSFUL`. Common first-run fix (only if reported): accept Gradle JVM toolchain — foojay auto-provisions JDK 17; do NOT change `sourceCompatibility`/`targetCompatibility` (leave MDK's 17).

### Task 9: `runClient` boot gate

- [ ] 9.1 `.\gradlew.bat runClient` — **Expected:** Minecraft reaches main menu without crash; Mods screen lists **Custom Jetpack** (mod id `custom_jetpack`).
- [ ] 9.2 Close client. If crash: capture `run/logs/latest.log`, fix (most likely: mods.toml dependency syntax or missing resource), re-run. No commit.

---

## M1 — Model pipeline + item render

### Converter math — normative (G = M default)

All formulas below are derived from verified GeckoLib 4.8.4 bytecode and are the single source of truth for Tasks 11–15. Units: glTF node units = Blockbench px (root scale 0.0625 converts px→blocks: 16 px = 1 block). All JSON values in **root-local px** (poly positions, pivots, position keys).

**Runtime ground truth (bytecode):**
- `prepMatrixForBone` = `translate(−posX/16, posY/16, posZ/16)` → `translate(pivot/16)` (no mirror) → rotate Z then Y then X (post-multiplied: matrix `R = Rz·Ry·Rx`) → `scale(s)` → `translate(−pivot/16)`.
- `Builtin.constructBone`: `rotation JSON [x,y,z]` → fields `(toRadians(−x), toRadians(−y), toRadians(z))`; `pivot JSON [x,y,z]` → field `(−x, y, z)`.
- Rotation keyframes are **deltas** added to rest fields; position/scale keyframes are **absolute** (rest pos field = 0, rest scale = 1). Keyframe JSON times are **seconds** (`length = (t−prev)×20` applied internally).
- `buildKeyframeStack` converts rotation constants at parse: X: `−toRadians(v)`, Y: `−toRadians(v)`, Z: `+toRadians(v)`; position/scale keys raw.
- GeoBone has no rest-scale field → **rest scale must be baked**. Non-identity rest rotation + animated rotation never co-occur in this model (fan rest R = I; tongues/embers animate only scale/translation), but formulas below handle the general case.

**Euler extraction** (matches `R = Rz(γ)·Ry(β)·Rx(α)`), from rotation matrix `r` of the glTF quaternion:
`β = asin(−r20)`, `α = atan2(r21, r22)`, `γ = atan2(r10, r00)`. Unwrap each animated euler to be within ±π of the rest value before differencing.

**Tree walk (pre-order), per node:**
- `c` = product of **strict-ancestor** rest scales (scalar; assert uniform — model rule: non-uniform scales `[1,1,0.5]` occur only on childless mesh nodes; uniform animated scale `ExhaustFX` handled via keys).
- `t̃ = c · t` (node translation, rest or animated value).
- Rest quat → `(α0, β0, γ0)`; `R0 = Rz(γ0)Ry(β0)Rx(α0)`.
- **Pivot JSON:** `p_json = (I − R0)⁻¹ · t̃` (px) — solves 3×3; when `R0 = I`, `p_json = t̃`.
- **Rest rotation JSON (deg):** `(α0, β0, −γ0)`.
- **Poly positions (JSON, natural px):** `v_json = (∏ all rest scales in chain, incl. own, as matrix) · v_mesh` — i.e. `c · S0_own · v_mesh`.
- **Normals:** transform by inverse-transpose of that matrix (identity direction for uniform scales), keep natural; factory negates X.
- Children walk with `c_child = c · S0_own`.

**Animation, per animated node, per keyframe time `t_k` (seconds, passed through unchanged):**
- `R_k`, `S_k`, `t_k` = rotation/scale/translation at `t_k` (rest value if channel absent); `S′_k = S_k · S0⁻¹` per axis (rest = 1).
- **Position key (absolute px):** `m(t_k) = c·t_k − (I − R_k·S′_k) · p_json`.
  Emit position keys whenever `m` is not identically 0 — **required even if the glTF node has no translation channel** (fan: rotation animates with `p_json ≠ 0`; ExhaustFX/halos/tongues: scale animates with `t̃ ≠ 0`). Embers/hazes have translation channels but their values still pass through this formula (scale contribution).
- **Rotation keys (deg):** `(deg(α_k−α0), deg(β_k−β0), −deg(γ_k−γ0))`.
- **Scale keys:** per-axis `S′_k`.

**geo.json emission:** every node → one bone (all 103 nodes qualify: mesh, animated, or ancestor-of). Unnamed glTF nodes get `node{i}` (glTF index) — deterministic, used by animation targets and `EMISSIVE_BONES`. Each bone: `pivot`, `rotation`, `children`, `cubes: []` (always — `Bone.cubes()` must not be null for Builtin), and at most one `poly_mesh`:
```json
"poly_mesh": {
  "normalized_uvs": true,
  "positions": [x,y,z, ...],   // flat doubles, root-local px, natural (unmirrored)
  "normals":   [x,y,z, ...],   // flat doubles, natural
  "uvs":       [u,v, ...],     // flat doubles, atlas space 0..1
  "polys":     [[[pi,ni,ui],[pi,ni,ui],[pi,ni,ui]], ...],  // triangulated, type TRI
  "type": "TRI"
}
```
(Exact key spelling — `poly_mesh`/`normalized_uvs` — confirmed from `Bone`/`PolyMesh` deserializers; `PolysUnion` is a single global TRI|QUAD type → **triangulate all quads** `(0,1,2),(0,2,3)` so type is uniformly `TRI`. **Mesh instancing:** report meshes 67–89 are referenced by 2 nodes each (113 node→mesh refs, 90 unique meshes) — emit poly geometry per *node instance* → test totals: **24110 verts / 24536 tris** (= 21425/22876 unique-mesh basis + 2685/1660 re-instanced), poly bones = **113**.)

**Factory (Java, G = M):** poly positions are stored natural; the factory writes each `GeoVertex` as `(−x/16, y/16, z/16)` (mirror + px→blocks) and normals as `(−nx, ny, nz)`; winding follows the mirrored storage — render relies on `entityTranslucent` (no cull). One `GeoCube` per poly bone: `GeoCube(quads, Vec3.ZERO, Vec3.ZERO, new Vec3(1,1,1), 0, false)` (cube pivot/rotation identity → cube sandwich is a no-op).

**G = I escape (`--flip-x`):** conjugate by `M = diag(−1,1,1)` — storage natural without factory mirror (`x/16`), pivot JSON x-negated, rest rotation `(−α0, −β0, +γ0)`, rotation keys `(−Δα, −Δβ, +Δγ)`, position keys x-negated through the same formula. Task 17's visual gate (warning-label side, nozzles, forward) picks the flag; default remains G = M (matches Builtin's own transform pattern).

### Task 10: Source assets + red test

**Files:** `assets-src/minecraft_jetpack.glb` (new), `tools/glb_analyze.py` (copy), `tools/test_glb2gecko.py` (new, RED)

- [ ] 10.1 `New-Item -ItemType Directory -Path assets-src,tools -Force`; `Copy-Item C:\Users\ADMIN\Downloads\minecraft_jetpack.glb assets-src\` ; `Copy-Item C:\Users\ADMIN\AppData\Local\Temp\opencode\glb_analyze.py tools\`
- [ ] 10.2 Verify SHA: `Get-FileHash assets-src\minecraft_jetpack.glb -Algorithm SHA256` (record in commit message).
- [ ] 10.3 Write `tools/test_glb2gecko.py` (complete code below). Counts from `glb_out\report.txt`: 103 nodes → 103 bones; **113 node→mesh refs** (90 unique meshes; meshes 67–89 double-instanced) → per-instance totals **24110 verts / 24536 tris** (unique basis 21425/22876 + instanced 2685/1660); animations Idle (1 channel: ExhaustFX scale) + Flight (41 channels incl. fan rotation). Converter runs with **default G = M** (no flags).
```python
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
        self.assertEqual(verts, 24110)   # per-node instances (113 refs); unique-mesh basis is 21425
        tris = sum(len(b["poly_mesh"]["polys"]) for b in bones if b.get("poly_mesh"))
        self.assertEqual(tris, 24536)    # unique basis 22876 + 1660 re-instanced
        self.assertEqual(poly_bones, 113)

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
        self.assertLessEqual(max_time(idle, [0.0]), 2.0)   # GLTF seconds pass-through (a ×20 bug → >= 20)
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
```
Note: the clip test recursively scans for `{position,rotation,scale}` keys that contain `keyframes`, so it tolerates nesting differences — but Task 14 must produce exactly the format `BakedAnimationsAdapter` parses (verify against its harvested expectations / a GeckoLib example; if the track-key spelling differs, update converter and test together, never the assertions' counts/types).
- [ ] 10.4 Run red: `python -m unittest tools.test_glb2gecko -v` → **Expected: ImportError/FAIL (converter missing)**.
- [ ] 10.5 Commits (per file): `chore(assets): source minecraft_jetpack.glb (sha256 …)`, `chore(tools): glb_analyze.py`, `test(tools): glb2gecko self-test (red)`

### Task 11: Converter skeleton

**Files:** `tools/glb2gecko.py` (new)

- [ ] 11.1 Write skeleton: CLI (`argparse`: `glb_path`, `--out`, `--flip-x`, `--flip-rotation`) — both flags are **escape hatches only**; default run uses neither (G = M, see Converter math). `load_glb(path) -> (json, bin_chunks)` fully implemented (JSON chunk parse + BIN chunk); `main()` calls the four builders `build_geo`, `build_atlas`, `build_animations`, `emit_model_data` (bodies filled in Tasks 12–14; each fill is complete code for that stage). Also implement in this task: shared helpers `quat_to_matrix(q) -> 3×3`, `matrix_to_euler_zyx(R) -> (α,β,γ)` (Converter math formulas), `accumulate_scale(...)` tree walk, since every later task depends on them.
- [ ] 11.2 Commit: `feat(tools): glb2gecko skeleton + GLB loader`

### Task 12: Bones, poly_mesh, pivot/rotation math

**Files:** `tools/glb2gecko.py` (edit)

- [ ] 12.1 Fill `build_geo` per Converter math block: pre-order walk with cumulative scale `c`; emit one bone per glTF node (all 103 qualify — mesh, animated, or ancestor-of; unnamed → `node{i}` from glTF node index); per bone: `name`, `pivot = (I−R0)⁻¹·(c·t)` (natural px; `t̃` when R0=I), `rotation = (α0, β0, −γ0)` deg, `cubes: []` always, `children`. Rest scale is NOT a bone field — bake into poly positions (`v_json = c·S0_own·v_mesh`).
- [ ] 12.2 Fill poly emission: flatten mesh primitive triangles (componentType 5126 float positions/normals, 5125 uint / 5123 ushort indices; triangulate quads `(0,1,2),(0,2,3)`), flat `positions`/`normals` arrays, `polys` as `[[[pi,ni,ui],×3],…]`, `type: "TRI"`; UVs deferred to Task 13 but reserve `uvs` slot + per-primitive `pi,ni,ui` index alignment. No skinning (model is rigid: glTF `skins` count 0 — assert). Normals via inverse-transpose of `c·S0` (direction-identity under uniform scale; non-uniform only on childless nodes — safe).
- [ ] 12.3 Axis rules for this task (G = M default): store positions/normals **natural** (mirror lives in the factory); quaternion→euler per Converter math; **no X negation in JSON**. `--flip-x` path: conjugate-by-M rules from Converter math block (only used if Task 17 gate fails).
- [ ] 12.4 Commit: `feat(tools): glb2gecko bones + poly_mesh + pivot/rotation math`

### Task 13: Atlas bake

**Files:** `tools/glb2gecko.py` (edit)

- [ ] 13.1 Fill `build_atlas` with PIL: composite all 9 GLB image buffers into one power-of-two RGBA atlas (grid), record per-primitive UV offset/scale, rewrite each poly's UVs into atlas space 0..1 (clamp), `normalized_uvs: true`. Preserve NEAREST intent (integer texel alignment; note: geo json has no filter field — default linear acceptable, note only). Save `textures/models/jetpack_atlas.png` + set geo top-level `texture` path.
- [ ] 13.2 Commit: `feat(tools): glb2gecko texture atlas (PIL)`

### Task 14: Animations + ModelData + main()

**Files:** `tools/glb2gecko.py` (edit)

- [ ] 14.1 Fill `build_animations` per Converter math block:
  - GLTF channels (translation/rotation/scale + samplers) → GeckoLib `animations.json` clips "Idle" and "Flight"; **keyframe times passed through in SECONDS** (GLTF times are seconds; GeckoLib expects seconds — `×20` is internal, do NOT pre-scale); `interpolation: "LINEAR"`.
  - Rotation: per-keyframe quaternion → matrix → euler `(α,β,γ)` (unwrapped ±π vs rest) → rotation keys `(deg Δα, deg Δβ, −deg Δγ)`; rest rotation JSON already `(α0,β0,−γ0)` from Task 12.
  - Position: **generate** `m(t_k) = c·t_k − (I − R_k·S′_k)·p_json` for every animated node with `p_json ≠ 0` or a translation channel — fan (rotation→position), ExhaustFX/halos/tongues (scale→position), embers/hazes (translation+scale); emit only if `m` varies or ≠ 0 (else omit channel). Rest position field stays 0.
  - Scale: per-axis keys `S′_k = S_k/S0`; only when scale channel exists (rest scale 1 default).
  - Assert: animated-rotation nodes have uniform `S0` if they have descendants (model holds: fan rest R = I; tongues rest 180°Z but scale-only).
- [ ] 14.2 Fill `emit_model_data`: `public static final String[] EMISSIVE_BONES` — derived from GLB materials with `emissiveFactor` (report: `warning_orange` + `nozzle_glow` → nodes {21,22,31–40,44,55,73}, as **generated names** — unnamed ones become `node{i}`, converter resolves actual names from data, never hardcoded guesses); assert no node's primitive set mixes emissive/non-emissive materials (else split-fail with message). `THRUSTER_L/R` nozzle offsets from bottom-most verts of the two nozzle meshes converted through the chain (root-local px → divide by 16 for blocks; report cross-check ≈ `[0, 0.74, ±0.315]` z-sign per side). Javadoc cites report source.
- [ ] 14.3 Fill `main()`: write geo json, animation json, atlas, `ModelData.java` (package `com.customjetpack.data`, header comment `GENERATED by tools/glb2gecko.py — do not edit`), print summary (bone count, tri count, clip names, generated-position-key count).
- [ ] 14.4 Commit: `feat(tools): glb2gecko animations + ModelData emission`

### Task 15: Run converter + tests green

- [ ] 15.1 Run (default G = M — **no `--flip-x`**):
```powershell
python tools/glb2gecko.py assets-src/minecraft_jetpack.glb --out .
python -m unittest tools.test_glb2gecko -v
```
**Expected:** converter prints summary; unittest `OK`. If counts mismatch report → fix converter (not the test — test numbers come from independent analysis). Orientation/parity issues are decided by the Task 17 visual gate (flip flags are escape hatches, not defaults).
- [ ] 15.2 Per-file commits: `feat(assets): generated jetpack.geo.json`, `feat(assets): generated jetpack.animation.json`, `feat(assets): generated jetpack_atlas.png`, `feat(mod): generated ModelData constants` (each file its own commit).

### Task 16: Item classes + registration + poly factory

**Files (each own commit):**
- `ModItems.java` — `DeferredRegister.create(ForgeRegistries.ITEMS, "custom_jetpack")` + `JETPACK` supplier (`stacksTo(1)`).
- `ClientSetup.java` (package `com.customjetpack.client`) — registers the factory on the client only:
```java
@EventBusSubscriber(modid = "custom_jetpack", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> BakedModelFactory.register("custom_jetpack", PolyMeshBakedModelFactory.INSTANCE));
    }
}
```
(`BakedModelFactory.register(String ns, factory)` is keyed by the model resource **namespace** — `custom_jetpack` matches `new ResourceLocation("custom_jetpack", "geo/models/…")`. `Dist.CLIENT` guard keeps it off dedicated servers. Order: factory file commit → ClientSetup commit → `JetpackMod.java` edit commit only if an explicit bus wiring is needed — with `@EventBusSubscriber` no JetpackMod edit is required for this.)
- `PolyMeshBakedModelFactory.java` (package `com.customjetpack.client`) — **bytecode-verified design:** `Builtin.constructBone` recurses `children()` calling `this.constructBone` *on itself* (builtin.txt offsets 174–232), so delegating the bone step would bypass our poly-cube injection for every descendant. Our factory therefore owns the single-bone step — replicating Builtin's verified logic (6-arg `GeoBone` ctor `(parent, name, mirror, inflate, neverRender, reset)` → `updateRotation(−x°, −y°, +z°)` → `updatePivot(−x, y, z)` → cubes loop) — and recurses itself; `constructCube` still delegates (unused: our JSON always has `cubes: []`):
```java
public class PolyMeshBakedModelFactory implements BakedModelFactory {
    public static final PolyMeshBakedModelFactory INSTANCE = new PolyMeshBakedModelFactory();
    private final BakedModelFactory builtin = new BakedModelFactory.Builtin();

    @Override public BakedGeoModel constructGeoModel(GeometryTree tree) {
        List<GeoBone> roots = new ArrayList<>();
        for (BoneStructure root : tree.topLevelBones().values())
            roots.add(constructBone(root, tree.properties(), null));
        return new BakedGeoModel(roots, tree.properties());
    }

    @Override public GeoBone constructBone(BoneStructure structure, ModelProperties properties, GeoBone parent) {
        Bone raw = structure.self();
        GeoBone bone = new GeoBone(parent, raw.name(), raw.mirror(), raw.inflate(), raw.neverRender(), raw.reset());
        double[] r = raw.rotation() != null ? raw.rotation() : new double[]{0, 0, 0};
        double[] p = raw.pivot() != null ? raw.pivot() : new double[]{0, 0, 0};
        bone.updateRotation((float) Math.toRadians(-r[0]), (float) Math.toRadians(-r[1]), (float) Math.toRadians(r[2]));
        bone.updatePivot((float) -p[0], (float) p[1], (float) p[2]);
        if (raw.cubes() != null)
            for (Cube cube : raw.cubes())
                bone.getCubes().add(builtin.constructCube(cube, properties, bone));
        addPolyCube(raw, bone);
        for (BoneStructure child : structure.children().values())
            bone.getChildBones().add(constructBone(child, properties, bone));
        return bone;
    }

    @Override public GeoCube constructCube(Cube cube, ModelProperties properties, GeoBone parent) {
        return builtin.constructCube(cube, properties, parent);
    }

    private static void addPolyCube(Bone raw, GeoBone bone) {
        PolyMesh pm = raw.polyMesh();
        if (pm == null) return;
        double[] pos = pm.positions(), nor = pm.normals(), uv = pm.uvs();
        List<GeoQuad> quads = new ArrayList<>(pm.polysUnion().union().length);
        for (double[][] poly : pm.polysUnion().union()) {
            GeoVertex[] corners = new GeoVertex[poly.length == 4 ? 4 : 3];
            int ni = (int) poly[0][1];
            for (int c = 0; c < poly.length; c++) {
                int pi = (int) poly[c][0], ui = (int) poly[c][2];
                // G = M: mirror X + px -> blocks (16 px = 1 block); UVs already atlas space 0..1
                corners[c] = new GeoVertex(new Vector3f((float) (-pos[3 * pi] / 16), (float) (pos[3 * pi + 1] / 16),
                        (float) (pos[3 * pi + 2] / 16)), (float) uv[2 * ui], (float) uv[2 * ui + 1]);
            }
            if (corners.length == 3)
                corners = new GeoVertex[]{corners[0], corners[1], corners[2], corners[2]};
            Vector3f n = new Vector3f((float) -nor[3 * ni], (float) nor[3 * ni + 1], (float) nor[3 * ni + 2]);
            quads.add(new GeoQuad(corners, n, Direction.UP));
        }
        bone.getCubes().add(new GeoCube(quads.toArray(new GeoQuad[0]), Vec3.ZERO, Vec3.ZERO,
                new Vec3(1, 1, 1), 0, false));
    }
}
```
(`Direction` is only consumed by Builtin cube math we bypass — any constant is fine with identity cube pivot/rotation/size. `GeoBone.updateRotation`/`updatePivot`/`getCubes` are public — cross-package linkage confirmed from Builtin bytecode. `--flip-x` G=I variant: drop the X negations in this factory and apply the Converter-math conjugate rules at JSON level instead — final choice via Task 17 gate.)
- `JetpackItem.java` — pattern verified from GeckoLib 4.8.4 `JackInTheBoxItem`:
```java
public class JetpackItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public JetpackItem(Properties p) { super(p); SingletonGeoAnimatable.registerSyncedAnimatable(this); }
    @Override public void initializeClient(Consumer<IClientItemExtensions> c) {
        c.accept(new IClientItemExtensions() {
            private JetpackItemRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new JetpackItemRenderer();
                return renderer;
            }
        });
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> state.setAndContinue(
            isFlightActive() ? RawAnimation.begin().thenLoop("animation.jetpack.flight")
                             : RawAnimation.begin().thenLoop("animation.jetpack.idle"))));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    /** Wired to RenderContext × ClientState in Task 26 (planned edit, not a placeholder). */
    private static boolean isFlightActive() { return false; }
}
```
(`isFlightActive()` stub is a planned Task 26 edit — not a placeholder; the Idle/Flight animation data must already be valid JSON by Task 15.)
- `JetpackGeoModel.java` — extends `GeoModel<JetpackItem>`: `getModelResource` → `new ResourceLocation("custom_jetpack","geo/models/jetpack.geo.json")`, `getTextureResource` → `textures/models/jetpack_atlas.png`, `getAnimationResource` → `animations/jetpack.animation.json`.
- `JetpackItemRenderer.java` — `extends GeoItemRenderer<JetpackItem>`; ctor `super(new JetpackGeoModel())`; (emissive layer added Task 35).
- `models/item/jetpack.json` — `{"parent":"builtin/entity","display":{…}}` — copy display transforms from GeckoLib example `jack_in_the_box.json` (verified content in extracted jar) as starting values.
- `lang/en_us.json` — `"item.custom_jetpack.jetpack": "Jetpack"`.
- `JetpackMod.java` edit — constructor adds `ModItems.ITEMS.register(modEventBus)`.
- [ ] 16.x `gradlew build` green after these files.

### Task 17: Render gate (manual)

- [ ] 17.1 `.\gradlew.bat runClient`; `/give @s custom_jetpack:jetpack`; hold in hand, open inventory GUI, F5 third person.
- [ ] 17.2 **Check:** geometry = real jetpack (tanks/tubes), not cubes; Idle clip animating (subtle exhaust pulse); **parity:** warning-label side and nozzle positions match the reference render (`glb_out` / Blender); nozzles point down/back, pack faces forward when worn.
- [ ] 17.3 G choice: default **G = M** must already be correct (it mirrors Builtin's own transform pattern). Only if parity is wrong → re-run Task 15 with `--flip-x` (G = I conjugate rules, Converter math block) and/or `--flip-rotation` (channel sign escape hatch); commit the chosen flags in the converter's CLI help line and re-run the test suite (test must pass under the chosen default).
- [ ] 17.4 Push: `git push` (M1 complete).

---

## M2 — Curios back slot

### Task 18: Slot tag

**Files:** `src/main/resources/data/curios/tags/items/back.json`
```json
{ "values": [ "custom_jetpack:jetpack" ] }
```
(Tag id `curios:back` is the Curios built-in slot tag consumed by validator `curios:tag`; fallback if equip rejected at Task 20: create slot override `data/curios/curios/slots/back.json` including `"whitelistItems": ["custom_jetpack:jetpack"]` — check curios docs/jar resources during implementation.)
- [ ] Commit: `feat(mod): curios back-slot tag`

### Task 19: Curios renderer

**Files (each own commit):**
- `RenderContext.java` — static `int entityId = -1` holder with `begin/end`; document: set only during curios/item render so the animation predicate knows we're the back-slot local render.
- `JetpackCuriosRenderer.java`:
```java
public class JetpackCuriosRenderer implements ICurioRenderer {
    private final JetpackItemRenderer geo = new JetpackItemRenderer();
    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext,
            PoseStack poseStack, RenderLayerParent<T, M> layerParent, MultiBufferSource buffer, int packedLight,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        RenderContext.begin(slotContext.entity());
        poseStack.pushPose();
        poseStack.translate(ModelData.CURIOS_OFFSET_X, ModelData.CURIOS_OFFSET_Y, ModelData.CURIOS_OFFSET_Z);
        geo.renderByItem(stack, ItemDisplayContext.NONE, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        RenderContext.end();
    }
}
```
- `ClientSetup.java` — `@Mod.EventBusSubscriber(modid="custom_jetpack", bus=MOD, value=Dist.CLIENT)` + `FMLClientSetupEvent` → `CuriosRendererRegistry.register(ModItems.JETPACK.get(), JetpackCuriosRenderer::new)` (method reference shape: `() -> new JetpackCuriosRenderer()` per `register(Item, Supplier<ICurioRenderer>)` signature).
- `JetpackMod.java` edit — no change needed if ClientSetup is annotated subscriber; verify event bus.

### Task 20: F5 gate + offset tuning

- [ ] 20.1 runClient → put jetpack in curios back slot (Curios inventory default key **C**? verify in-game control hints).
- [ ] 20.2 F5: jetpack sits on back, not floating/inside player; Idle animation plays while equipped.
- [ ] 20.3 Tune `ModelData.CURIOS_OFFSET_*` (these three are hand-tuned constants — keep them in ModelData with comment `hand-tuned, not generated`): adjust, rebuild, re-check until flush with back.
- [ ] 20.4 Per-file commits + `git push` (M2 complete).

---

## M3 — Flight core

### Task 21: `JetpackConfig.java`

**Files:** `JetpackConfig.java` (new), `JetpackMod.java` (edit: `ModLoadingContext.get().registerConfig(...)` in ctor, DistExecutor-safe client config registration).

- [ ] 21.1 SERVER spec (`ForgeConfigSpec.Builder`): `energyCapacity=100000`, `maxEnergyReceive=1000`, `drainPerTickHover=2`, `drainPerTickBoost=8`, `hoverSpeed=0.25`, `boostSpeed=0.6`, `verticalSpeed=0.2`, `movementDamping=0.25`, `fallGraceTicks=40` — with `.comment(...)` per key.
- [ ] 21.2 CLIENT spec: `thrustSoundVolume=0.6`, `particleDensity=1.0`, `showEnergyHud=true`.
- [ ] 21.3 Commit: `feat(config): jetpack server/client config`

### Task 22: FE capability

**Files:** `JetpackEnergyStorage.java` (new), `JetpackMod.java` (edit: `AttachCapabilitiesEvent<ItemStack>` listener, or `RegisterCapabilitiesEvent` + per-stack attach — use `AttachCapabilitiesEvent<ItemStack>` filtered `event.getObject().getItem() == ModItems.JETPACK.get()`), `EnergyApi`? — use `net.minecraftforge.energy.EnergyStorage` subclass with public setter for drain; commit each.

- [ ] 22.1 Class: `extends EnergyStorage` (capacity/receive from config at attach time), `serializeNBT/deserializeNBT` for stack persistence; capability `ForgeCapabilities.ENERGY`.
- [ ] 22.2 `gradlew build` green.

### Task 23: Network channel

**Files (each own commit):** `ModNetwork.java`, `FlightInputC2S.java`, `FlightStateS2C.java` — all in `network/`.

- [ ] 23.1 `ModNetwork`: `SimpleChannel` builder (`NetworkRegistry.newSimpleChannel(new ResourceLocation("custom_jetpack","main"), () -> "1", "1"::equals, "1"::equals)`), `protocolVersion` strings equal; `register()` called from mod ctor; ids: 0 = input, 1 = state.
- [ ] 23.2 `FlightInputC2S` — fields `w,a,s,d,space,shift,hToggle` booleans; encode/decode via `FriendlyByteBuf.writeBoolean`; `handle` → store into server-side `ServerInputs` map (static `Map<UUID,Input>` in this class or FlightTick) on main thread (`ctx.get().enqueueWork`).
- [ ] 23.3 `FlightStateS2C` — `entityId:int, engaged:boolean, energy:int`; handle → `ClientState.put(...)` via enqueueWork.
- [ ] 23.4 `gradlew build` green.

### Task 24: Keybinds + client input

**Files (each own commit):** `KeyBindings.java` (`KeyMapping("key.custom_jetpack.toggle_flight", GLFW.GLFW_KEY_H, "key.categories.custom_jetpack")` registered in `RegisterKeyMappingsEvent`), `ClientState.java` (static `Map<Integer,Entry{engaged,energy}>` + `engaged(id)`/`energy(id)`), `ClientEvents.java` (`@EventBusSubscriber(Dist.CLIENT)`):
- [ ] 24.1 `InputEvent.Key` (and `InputEvent.Mouse`? — keyboard only) → on H press send `FlightInputC2S` with hToggle=true (and current held states of W/A/S/D/Space/Shift from `KeyMapping.isDown()`); on WASD/Space/Shift press/release send update with hToggle=false.
- [ ] 24.2 lang: `"key.custom_jetpack.toggle_flight": "Toggle Jetpack"`, `"key.categories.custom_jetpack": "Custom Jetpack"` — commit `feat(lang): keybind strings`.

### Task 25: Server flight tick

**Files:** `FlightTick.java` (new, registered `PlayerTickEvent` in `JetpackMod` — edit, own commit), `ModItems`? no.

Logic (complete in next pass — this task's code block):
- skip client side; skip if player not living / spectator.
- find jetpack: `CuriosApi.getCuriosHelper().findFirstCurio(player, s -> s.is(ModItems.JETPACK.get()))` → `Optional<SlotResult>` → stack; if absent: if engaged was set, clear NBT + send S2C disengage; return.
- H toggle: if input.hToggle → flip `stack.getOrCreateTag().getBoolean("Engaged")` (persist via `stack.setTag`), on engage: `player.fallDistance=0`, send S2C engage.
- engaged path:
  - energy: `stack.getCapability(ForgeCapabilities.ENERGY)` → if `extractEnergy(drain,false)==0` → disengage (clear NBT, S2C, grant `MobEffects.SLOW_FALLING` for `fallGraceTicks`, `fallDistance=0`).
  - input → target velocity: forward = look yaw direction × `boostSpeed` (space held) or `hoverSpeed`; vertical: space +0 / shift −`verticalSpeed`; strafe A/D; damped: `setDeltaMovement(cur.lerp(target, movementDamping))`; `hurtMarked=true`; `fallDistance=0`; `player.setNoGravity` NOT used (vanilla-safe: overwrite delta after travel; fall grace handles cut-off).
  - S2C every tick? spec: on change + every 10 ticks engaged + idle heartbeat — implement: send if `tickCount % 10 == 0 || changed`.
- disengage edge (Task 37): death event clears NBT — `PlayerEvent.Clone`/death: handle in same class or PlayerEvent listener (M6).
- [ ] 25.x `gradlew build` green; commits: `feat(flight): FlightTick server flight core`, `feat(mod): wire FlightTick`.

### Task 26: Animation predicate wiring

- [ ] 26.1 `JetpackItem.registerControllers` — replace `isFlightActive()` body: `RenderContext.entityId() >= 0 && ClientState.engaged(RenderContext.entityId())`; commit `feat(item): flight/Idle animation predicate`.
- [ ] 26.2 Note: renders for OTHER players' jetpacks on client receive their entityId via S2C (heartbeat) ✓.

### Task 27: MP test gate

- [ ] 27.1 Terminal A: `.\gradlew.bat runServer` — wait for `Done`; keep running.
- [ ] 27.2 Terminal B: `.\gradlew.bat runClient` — `localhost` test; Terminal C optional second client (another user dir? same machine second client = skip; use `/setblock`? — realistic: single player world on server + one client is enough for H-toggle/energy; second player optional if available).
- [ ] 27.3 Checklist: give jetpack, equip back, H → lifts off, WASD moves, Shift descends, Shift+W boosts, H → falls with slow-fall grace, energy drains (S2C HUD not yet — verify via server log or `/data get`? — simplest: add temporary debug? No — energy verified in M5 HUD; here verify flight + toggle), relog → state sane, unequip mid-air → grace works.
- [ ] 27.4 Fix issues (own commits), `git push` (M3 complete).

---

## M4 — FX

### Task 28: Particle texture + json

**Files:** `tools/gen_particle_texture.py` (PIL 8×8→ upscale? MC particle textures can be small; generate 16×16 RGBA radial flame: orange core→transparent edge, NEAREST), `src/main/resources/assets/customjetpack/textures/particle/jetpack_thruster.png`, `src/main/resources/assets/customjetpack/particles/jetpack_thruster.json` `{"textures":["custom_jetpack:jetpack_thruster"]}`.
- [ ] Commits per file: `feat(tools)`, `feat(fx): particle texture`, `feat(fx): particle json`.

### Task 29: Particle type + provider + spawn loop

**Files:** `ModParticles.java` (`DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES,...)`; `JETPACK_THRUST = register("jetpack_thruster", () -> new SimpleParticleType(false))`), `JetpackMod.java` (wire register), `client/ParticleEvents.java` (`RegisterParticleProvidersEvent` → `event.registerSpriteSet(ModParticles.JETPACK_THRUST.get(), sprites -> (opts, level, x,y,z, xs,ys,zs) -> new ThrusterParticle(level, x,y,z, xs,ys,zs, sprites))`), `client/ThrustParticle.java` (`extends TextureSheetParticle`: setSprite(sprites.get(rand)), lifetime ~10–14, gravity 0.02, quadSize 0.1, color orange→fade via `setAlpha`/`rCol`), `client/ClientFx.java` (`ClientTickEvent.Post`: if local `ClientState.engaged(playerId)` → per nozzle `THRUSTER_L/R` world-pos (rotate by `yBodyRot`), `level.addParticle(JETPACK_THRUST, …)` count `2 × particleDensity`, small random velocity downward-outward).
- [ ] `gradlew build` green; commit each file.

### Task 30: Sounds synthesis

**Files:** `tools/synth_sounds.sh` (ffmpeg: loop = 4s pink-noise + 70Hz sine tremolo crossfaded ends; start = 0.4s rise; stop = 0.6s decay — full script in next pass), outputs → `src/main/resources/assets/customjetpack/sounds/{jetpack_thrust_loop,jetpack_thrust_start,jetpack_thrust_stop}.ogg`, `sounds.json` (subtitle entries), `ModSounds.java` (`DeferredRegister<SoundEvent>` + 3 `SoundEvent.createVariableRangeEvent`), wire in `JetpackMod`.
- [ ] Run `bash tools/synth_sounds.sh` (ffmpeg on PATH ✓); verify files non-zero size; commit each.

### Task 31: Loop sound manager

**Files:** `client/ThrustSound.java` (`extends TickableSound` — compile gate: if member names differ from plan code, fix from compiler errors; expected members: ctor sets `this.looping=true`, `this.delay=0`, volume from config, `x/y/z`; `tick()` updates pos from entity lookup + stops when `!ClientState.engaged(entityId)` or entity gone; `isFinished()` when stopped), `client/ClientFx.java` (edit: on engage edge → `Minecraft.getSoundManager().play(new ThrustSound(id))`; on disengage edge → play start/stop one-shots appropriately: start on engage, stop on disengage; guard single instance with `soundPlaying` flag).
- [ ] `gradlew build` green; commits.

### Task 32: FX gate

- [ ] 32.1 In-game: thruster particles stream from nozzles while flying (not while idle in GUI), loop sound starts/stops with H, volume config respected.
- [ ] 32.2 Fix + commits + `git push` (M4 complete).

---

## M5 — Content polish

### Task 33: Recipe
**Files:** `data/customjetpack/recipes/jetpack.json` — `I R I / I F I / R R R`, `I=minecraft:iron_ingot`, `R=minecraft:redstone`, `F=minecraft:blast_furnace`, `result: custom_jetpack:jetpack`. Commit `feat(mod): jetpack recipe`.

### Task 34: Energy HUD
**Files:** `client/ClientHud.java` (`RegisterGuiOverlaysEvent` → `registerAboveAll("jetpack_energy", (gui, g, pt, sw, sh) -> …)` — render only when `Minecraft.getInstance().player` has jetpack energy known: read `ClientState.energy(localId)`; bar 90×5 bottom-center above hotbar, color lerp red→green, text `"" + energy + " FE"`; respect `showEnergyHud` config), wire listener (ClientSetup edit), lang? numbers only — no lang keys needed.
- [ ] commit + gate in-game.

### Task 35: Emissive layer
**Files:** `client/JetpackEmissiveLayer.java` (`extends GeoRenderLayer<JetpackItem>`; ctor takes `GeoRenderer`; override `render(...)`: hide all bones not in `ModelData.EMISSIVE_BONES` (walk `bakedModel.topLevelBones` recursively via `GeoBone.getChildBones()`), `getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, RenderType.entityTranslucentEmissive(texture), buffer, partialTick, 0xF00000 /*fullbright*/, OverlayTexture.NO_OVERLAY, 1,1,1,1)`, then restore hidden flags), `JetpackItemRenderer` ctor `addRenderLayer(new JetpackEmissiveLayer(this))`, `JetpackCuriosRenderer` uses same renderer (already `new JetpackItemRenderer()` — layer comes from ctor ✓).
- [ ] `gradlew build`; gate: emissive bits glow in dark.

### Task 36: Lang + polish + push
- [ ] 36.1 Fill any missing lang keys (`subtitles.custom_jetpack.thrust` etc. from sounds.json).
- [ ] 36.2 Config `.comment` review; `git push` (M5 complete).

---

## M6 — Hardening

### Task 37: Edge-case checklist (manual, fix with own commits)
- [ ] Die while flying → on respawn: not engaged, no lingering slow-fall beyond grace (clear in `PlayerEvent.Clone` if new player clone tag carries Engaged — explicitly clear or keep per spec: engaged flag cleared on death).
- [ ] Unequip mid-air → grace applies; re-equip → stays disengaged.
- [ ] Dimension change while engaged → disengage (server: `PlayerEvent.PlayerChangedDimensionEvent`).
- [ ] Disconnect/relog → NBT Engaged persists? (spec: state on stack NBT; decide per spec §5 — if spec says persist, restore; else clear on logout `PlayerEvent.PlayerLoggedOutEvent`).
- [ ] Spectator/creative edge: spectator cannot engage (guard in toggle).
- [ ] Energy = 0 → auto disengage + grace.
- [ ] No jetpack equipped → H does nothing (no packet spam: client checks equipped via curios helper client-side before sending? acceptable either way — verify no server log spam).

### Task 38: Release build + final push
- [ ] 38.1 `.\gradlew.bat clean build` → `BUILD SUCCESSFUL`; jar at `build/libs/custom_jetpack-<ver>.jar`.
- [ ] 38.2 `git status` clean; `git push`.
- [ ] 38.3 Report to user: repo URL, jar path, controls summary.

---

## Self-review (to be completed after fills)

- [ ] No `TODO`/placeholder/`NotImplementedError` left in plan code (converter skeleton stage-only exceptions are resolved by Tasks 12–14).
- [ ] Every step has file paths + expected results.
- [ ] Per-file commit rule honored in every task.
- [ ] Config keys/packets match spec §7/§8 exactly.
- [ ] Converter math consistency: every Task 12–15 instruction matches the normative "Converter math (G = M)" block — pivot `(I−R0)⁻¹t̃`, rest rot `(α0,β0,−γ0)`, rot keys `(+Δα,+Δβ,−Δγ)`, position keys `m(t)=c·t−(I−RS′)p_json` (generated where needed), scale keys `S/S0`, times in seconds, `cubes: []` always, poly `type: TRI`, unnamed → `node{i}`, default run without flip flags.
- [ ] Factory: single-bone step owned by `PolyMeshBakedModelFactory` (no `builtin.constructBone` delegation), children recursed by us, `constructCube` delegates; `ClientSetup` registers namespace `custom_jetpack` on `FMLClientSetupEvent` (Dist.CLIENT).
- [ ] Test asserts exact counts (103 bones / 113 poly bones / 24110 verts / 24536 tris — per-node instances of 90 unique meshes) and both clips; test runs with converter defaults.
