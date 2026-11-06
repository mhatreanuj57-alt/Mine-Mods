# Custom Jetpack Mod — Design Spec

- Date: 2026-11-06
- Status: awaiting user review
- Target: Minecraft 1.20.1, Forge 47.4.28
- Mod id: `custom_jetpack` · Display name: Custom Jetpack · Package: `com.customjetpack`
- Project root: `C:\Users\ADMIN\Projects\jetpack-mod`

## 1. Overview

Build a new Forge mod from scratch that adds a wearable jetpack driven by the user's AI-generated animated GLB model (`minecraft_jetpack.glb`). The jetpack is equipped in the Curios **back** slot, flies via an H-toggle hover scheme, is powered by FE (Forge Energy), and shows the model's own Idle/Flight animations plus complementary thruster particles and synthesized sounds.

**Success criteria**

1. The exact GLB geometry renders in-game (no cube/placeholder substitution) — in inventory, on the player's back, and from third person.
2. Idle animation loops at rest; Flight animation plays for all watching clients while engaged.
3. H toggles take-off/hover/land; WASD/Space/Shift move the player; server-authoritative, multiplayer-safe.
4. FE capability charges via cables/creative; energy depletes while flying; empty energy auto-disengages with fall grace.
5. Thruster particles + looping thrust sound appear only while engaged.
6. Every milestone builds (`gradlew build`) and is manually testable in `runClient`.

**Constraints (from user)**

- No vanilla-cube/placeholder model geometry, ever.
- Build and test incrementally; no big-bang implementation.
- Act as senior Minecraft mod developer; verify before asserting.

## 2. Verified environment and dependencies

All items below were checked against live sources or extracted jars during brainstorming.

| Component | Version / coordinate | Verification |
|---|---|---|
| Forge MDK | `1.20.1-47.4.28` | MDK zip downloaded & extracted: Gradle **8.8**, ForgeGradle `[6.0,6.2)`, official mappings `1.20.1`, `java.toolchain = 17` |
| JDK 17 | auto-provisioned | MDK `settings.gradle` ships `org.gradle.toolchains.foojay-resolver-convention 0.7.0` → Gradle downloads Temurin 17 into `~/.gradle` on first build. Gradle 8.8 runs on current JAVA_HOME (21). No manual install required. |
| GeckoLib | `software.bernie.geckolib:geckolib-forge-1.20.1:4.8.4` | Maven POM **200** at `https://dl.cloudsmith.io/public/geckolib3/geckolib/maven`; jar extracted and inspected: `Bone.polyMesh`, `PolyMesh`, `PolysUnion`, `TextureMesh`, `BakedModelFactory.register(namespace, factory)` all present → **native poly_mesh support** |
| GeckoLib helper | `com.eliotlash.mclib:mclib:20` | Required per GeckoLib install docs; add `fg.deobf` (POM check in M0) |
| Curios | `top.theillusivec4.curios:curios-forge:5.14.1+1.20.1` | Maven POM **200** at `https://maven.theillusivec4.top/`; jar contains `top.theillusivec4.curios.api.client.ICurioRenderer`, `CuriosRendererRegistry`, `ICurio`, `ICurioItem`, and default slot `data/curios/curios/slots/back.json` (validator `curios:tag`) |
| ffmpeg | 9.0 | On PATH → sound synthesis to `.ogg` |
| Blockbench `gltf_importer` | — | **Not** in the pipeline (drops meshes on GeckoLib export, issue #1814). Optional visual QA only. |
| GeckoMesh mod | — | Not needed: GeckoLib 4.8.4 parses poly_mesh natively. |

**Open verification tasks (bounded, scheduled):** M0 confirms `mclib` POM and whether the mixin Gradle plugin is needed (only add if build complains); M2 confirms the exact Curios item-tag id behind validator `curios:tag` (assumption: tag `custom_jetpack:back`-style — read Curios `TagValidator` source/jar at that point, fallback: ship a slot JSON override).

## 3. Source model analysis

File: `C:\Users\ADMIN\Downloads\minecraft_jetpack.glb` (copy into repo at `assets-src/minecraft_jetpack.glb`)

- glTF 2.0, generator `THREE.GLTFExporter`, no Draco, no skins/morphs, extensionsUsed: `KHR_materials_unlit` only
- Size 946,316 bytes; SHA-256 `A23F10798C65E5F5D15E770A3A7CE34F4482449A3E0DA3C8F9A2089F92A00C8F`
- 103 nodes, 90 meshes, 21,425 verts, 22,876 triangles
- Root node `JetpackWithStraps` scale 0.0625; authored in **player space**: world bbox ≈ 0.56 × 0.84 × 0.69, y ∈ [0.72, 1.56] (torso height — back-slot ready)
- 9 embedded PNG textures (6× 32×32, 1× 16×16, 2× 8×8), NEAREST filtering; 19 materials (15 `KHR_materials_unlit`, several `alphaMode=BLEND` flame/haze, emissive factors on `warning_orange` and `nozzle_glow`, several color-only materials)
- Animations (pure node TRS, both 1.0 s, loopable):
  - `Idle`: 1 channel (`ExhaustFX.scale`, 2 keyframes) — gentle pulse
  - `Flight`: 41 channels (exhaust/flame/ember/haze scale+translation, `fan.rotation` with 31 linear keyframes)
- `ExhaustFX` base scale 0.001 → thruster meshes hidden at rest, scaled up by `Flight` (model animates its own flames; MC particles are a complement, not a replacement)

## 4. Asset pipeline (offline converter)

**Approach selected:** custom converter `tools/glb2gecko.py` (reuses the working GLB parser from analysis) producing GeckoLib-format assets. Rejected: Blockbench GUI path (loses meshes), fully custom renderer (reinvents animation/render stack). Fallback if builtin poly_mesh baking misbehaves: register our own `BakedModelFactory` for namespace `custom_jetpack` (API verified in jar).

**Inputs:** `assets-src/minecraft_jetpack.glb`

**Outputs:**

| Output | Path |
|---|---|
| Geometry | `src/main/resources/assets/custom_jetpack/geo/models/jetpack.geo.json` |
| Animations | `src/main/resources/assets/custom_jetpack/animations/jetpack.animation.json` (`animations.idle`, `animations.flight`) |
| Texture atlas | `src/main/resources/assets/custom_jetpack/textures/models/jetpack_atlas.png` |
| Locator/emit constants | `src/main/java/com/customjetpack/data/ModelData.java` (nozzle offsets, emissive bone list — generated) |
| Converter self-test | `tools/test_glb2gecko.py` |

**Transformations performed by the converter:**

1. **Hierarchy → bones**: node tree → Bedrock-style bones (name, pivot, rotation, parent). One bone per mesh-bearing leaf (multiple meshes per node are split to sibling leaves sharing the parent pivot) so material groups stay separable.
2. **Geometry → `poly_mesh`**: positions/normals/UVs + poly indices per bone (native GeckoLib 4.8.4 format).
3. **Axis conversion glTF (RH) → Bedrock (MC):** X negated for positions, normals, translations; quaternions mapped `(x,y,z,w) → (−x,y,z,w)`; then converted to Euler XYZ degrees per keyframe with ±360° unwrap. GeckoLib also applies its own X handling when baking — the converter exposes `--flip-x` / `--flip-rotation` flags, and **M1 establishes the correct flag combination by visual comparison** against a Blockbench/three.js reference screenshot; the winning mode is documented in `tools/README.md` and made the default.
4. **Texture atlas**: pack all 9 PNGs into one atlas (NEAREST preserved); color-only materials get solid-color tiles; all UVs remapped. Single-texture constraint satisfied (`GeoModel.getTextureResource` returns one resource).
5. **Emissive**: materials with emissive factors or unlit flame semantics are flagged; converter emits `emissive_bones.json` → baked into `ModelData.EMISSIVE_BONES`. Rendered by a custom `GeoRenderLayer` re-drawing only those bones at fullbright packed light (GeckoLib 4.8.4 has no emissive-suffix texture convention — verified by jar inspection; `AutoGlowingGeoLayer` renders the whole model with the base texture, which we don't want).
6. **Locators**: `thruster_l` / `thruster_r` bone locators at the nozzle positions (from mesh bounds) → exported as constant offsets in `ModelData` for particle/sound spawn math (server cannot read baked models, so constants are shared by both sides).

**Converter self-test (`tools/test_glb2gecko.py`, run in M1):** asserts node/mesh/triangle counts match the analysis (103/90/22,876), bbox within 1e-3 of the reference, both animation clips present with 1 and 41 channels, all UVs inside [0,1], atlas dimensions valid, and no orphan materials.

**GeckoLib resource layout (confirmed from 4.8.4 bytecode):** `geo/<subtype>/<name>.geo.json`, `animations/<name>.animation.json` under `assets/<modid>/`, textures under `textures/`. We return explicit `ResourceLocation`s from our `GeoModel` subclass: `custom_jetpack:geo/models/jetpack.geo.json`, `custom_jetpack:animations/jetpack.animation.json`, `custom_jetpack:textures/models/jetpack_atlas.png`.

## 5. Runtime architecture

Package `com.customjetpack`:

| Unit | Responsibility | Depends on |
|---|---|---|
| `JetpackMod` | `@Mod` entry, registration orchestration, config load, network channel init | Forge, GeckoLib, Curios |
| `item.JetpackItem` | `GeoItem` impl, singleton `GeoAnimatable`, energy capability attach, `getBurnTime`-free (FE only) | GeckoLib, Forge caps |
| `item.JetpackGeoModel` / `JetpackItemRenderer` | `GeoModel` + `GeoItemRenderer` (3D model shown in GUI and hand) | GeckoLib |
| `curios.CuriosIntegration` | item tag registration, `CuriosRendererRegistry.register` → `ICurioRenderer` that poses the `GeoRenderer` on the player's back (offsets from `ModelData`, tuned in M2) | Curios API |
| `anim.JetpackAnimationController` | one controller: predicate → `flight` while engaged else `idle`, 0.15 s transition; state read from synced stack data | GeckoLib |
| `energy.JetpackEnergyStorage` | `IEnergyStorage` on the stack (`AttachCapabilitiesEvent<ItemStack>`, `ForgeCapabilities.ENERGY`), NBT persistence, change notifications for sync | Forge |
| `flight.FlightState` (per-player, server) | H-toggle state machine, input handling, movement tick, energy drain, fall grace | server only |
| `flight.FlightInputHandler` | serverbound input packet processing (validated: equipped in back slot, not spectator/passenger, energy > 0) | net |
| `net.JetpackNetwork` | Forge `SimpleChannel` (1.20.1): C2S `flight_input`, S2C `flight_state` | Forge network |
| `fx.ThrusterParticles` | client tick: for every player whose synced state is engaged, spawn via `level.addParticle` (purely client-side, driven by synced state) at `ModelData.THRUSTER_*` offsets rotated by body yaw | client |
| `fx.ThrustSoundManager` | client: map of player → looping `TickableSound` (`custom_jetpack:jetpack_thrust_loop`), start/stop on state change, positional for other players | client |
| `client.ClientState` | holds latest S2C `flight_state` per entity id; drives HUD, particles, sound, animation predicate | client |
| `client.KeyBindings` | H keybind (`key.custom_jetpack.toggle_flight`), GLFW `H` (unbound by default in 1.20.1) | client |
| `client.HudOverlay` | FE bar while jetpack equipped in back slot (config-gated) | client, Forge GUI events |
| `data.ModelData` | **generated** constants: nozzle offsets, emissive bones | converter output |
| `config.JetpackConfig` | `ForgeConfigSpec`: SERVER + CLIENT buckets (see §12) | Forge |

**Data flow (flying):** H press → C2S `flight_input` → server validates + toggles `FlightState` → server tick computes movement (see §6), drains FE, sets `hurtMarked`, zeroes `fallDistance` → S2C `flight_state` to trackers → clients update `ClientState` → animation predicate switches to `flight`, particles/spawn sound start, HUD shows energy.

**Error/edge handling:** energy ≤ 0 → forced server-side disengage + fall grace; player death/logout/respawn → engaged flag cleared on stack NBT; dimension change while engaged → disengage; spectator mode entry → disengage; `player.isPassenger()` → refuse engage.

## 6. Flight control specification (H-toggle hover)

**Inputs (client):** `H` = toggle edge; held state of `W A S D Space Shift` streamed in C2S `flight_input` on change.

**Server state machine (per player):**

- **DISENGAGED → ENGAGED** on H iff: jetpack equipped in Curios back slot, `energy > 0`, not spectator, not a passenger. On engage: `fallDistance = 0`.
- **ENGAGED → DISENGAGED** on H, on energy exhaustion, or on death/dimension/spectator. On disengage: `fallDistance = 0` + apply `MobEffects.SLOW_FALLING` for `fallGraceTicks` (prevents post-landing damage from the disengage altitude).

**While engaged (each server tick):**

| Input | Effect |
|---|---|
| none | hover: target velocity 0 (damped), low drain |
| `W/A/S/D` | horizontal cruise toward input direction at `hoverSpeed` (blocks/tick), damped |
| `Space` held | vertical +`verticalSpeed` |
| `Shift` held (without `W`) | vertical −`verticalSpeed` |
| `Shift + W` held | forward at `boostSpeed` (fast forward) |

- Gravity is cancelled; velocity approaches target via damping factor `movementDamping` per tick (zero drift on key release).
- `player.hurtMarked = true` every engaged tick → forced position sync to server/clients.
- Energy drain each engaged tick: `drainPerTickHover`, or `drainPerTickBoost` while `Shift+W` boost is active.
- Multiplayer: only the server mutates position; clients render from synced movement + `flight_state`.

## 7. Energy system

- Capability: `ForgeCapabilities.ENERGY` on the jetpack `ItemStack` via `AttachCapabilitiesEvent<ItemStack>`; implementation wraps `EnergyStorage` with NBT serialization (persists on the item, survives inventory/curios moves).
- Charging: any FE source (cables, creative tab energy tools) works through the capability; `maxReceive` from config.
- No fuel items (user chose FE-only): right-click fuel behavior is out of scope.
- Sync: S2C `flight_state` carries `engaged` + `energy` for the local player and engaged remote players (sent on change, plus every 10 ticks while engaged) — feeds HUD, particles, sound, animation.

## 8. Curios integration and rendering

- Equip slot: default `back` slot shipped by Curios 5.14.1. Our item joins the slot via the `curios:tag` validator (exact tag id verified in M2; fallback: ship `data/curios/curios/slots/back.json` override adding an allowlist validator).
- Rendering: `CuriosRendererRegistry.register(JetpackItem.class, renderer)` with an `ICurioRenderer` that invokes our `GeoItemRenderer` inside the provided `PoseStack`, applying: player-space offset correction (model was authored at y 0.72–1.56 — renderer translates so the pack sits on the back), body-yaw rotation, sneak pitch behavior (match back-slot conventions: no pitch), and slim-arm irrelevance (not a skin layer).
- Orientation check gate in M2 via F5 in all orientations; constants live in `ModelData`/renderer, not scattered literals.

## 9. Animation control and sync

- One `AnimationController` on `JetpackItem`: predicate returns `PlayState.PLAY` with `animations.flight` when `ClientState.engaged[entityId]`, else `animations.idle`; `transitionLengthTicks(3)` cross-fades.
- Both clips loop (`AnimationLoopType.LOOP`).
- No GeckoLib DataTicket needed for flight state — the custom `flight_state` packet is the single source of truth (also feeds HUD/particles/sound), avoiding duplicate sync paths.
- All watching clients derive animation from the same packet → other players see Flight anim while you fly.

## 10. FX: particles and sounds

**Particles**

- Type: `custom_jetpack:jetpack_thruster` (`SimpleParticleType`), provider registered in `RegisterParticleProvidersEvent` (1.20.1).
- Texture: 8×8 pixel-art flame/smoke (generated in M4 by nearest-upscaling/deriving from the GLB's 8×8 flame textures), `textures/particle/jetpack_thruster.png`.
- Spawn: client tick, for each player with synced `engaged`, spawn **2 particles per nozzle per tick × `particleDensity`** (rounded) at `ModelData.THRUSTER_L/R` offsets rotated by `yRot`, with small random spread and upward velocity. Client-side spawn keeps multiplayer cheap (no server particle packets; every client sees the same synced state).

**Sounds** (synthesized with ffmpeg → Vorbis `.ogg`, M4)

- `jetpack_thrust_loop` — seamless ~4 s loop (pink-noise band-pass hiss + 60–90 Hz rumble with tremolo, crossfaded); played by a looping `TickableSound` per engaged player (positional for remote players), volume `thrustSoundVolume` (client config).
- `jetpack_thrust_start` / `jetpack_thrust_stop` — short one-shots on toggle.
- Registered under `sounds.json`; stop loop on disengage, death, dimension change, disconnect.

## 11. Networking

Forge `SimpleChannel` (1.20.1 `net.minecraftforge.network`):

| Packet | Direction | Payload | Purpose |
|---|---|---|---|
| `flight_input` | C2S | `boolean w,a,s,d,space,shift` + `boolean hToggle` (edge) | input stream; server ignores if not engaged-valid; `hToggle` triggers state machine |
| `flight_state` | S2C | `int entityId`, `boolean engaged`, `int energy` | single sync source for HUD/anim/particles/sound |

Anti-abuse: server re-validates equip/spectator/passenger/energy on every `flight_input`; movement is only ever applied server-side (client never sets its own delta while engaged).

## 12. Configuration

`ForgeConfigSpec` (loaded in `JetpackMod`):

**SERVER** (gameplay; Forge syncs server config to clients):

| Key | Default | Meaning |
|---|---|---|
| `energyCapacity` | 100000 | FE buffer |
| `maxEnergyReceive` | 1000 | FE/t charge limit |
| `drainPerTickHover` | 2 | FE/t while engaged, hovering |
| `drainPerTickBoost` | 8 | FE/t while `Shift+W` boosting |
| `hoverSpeed` | 0.25 | blocks/tick horizontal cruise |
| `boostSpeed` | 0.6 | blocks/tick fast forward |
| `verticalSpeed` | 0.2 | blocks/tick up/down |
| `movementDamping` | 0.25 | velocity approach factor (0–1) |
| `fallGraceTicks` | 40 | slow-falling ticks granted on disengage |

**CLIENT**:

| Key | Default | Meaning |
|---|---|---|
| `thrustSoundVolume` | 0.6 | loop volume |
| `particleDensity` | 1.0 | particle count multiplier |
| `showEnergyHud` | true | FE bar overlay while equipped |

## 13. Content

- **Item:** `custom_jetpack:jetpack`, max stack 1, not damageable. Inventory/GUI shows the 3D geo model via `GeoItemRenderer` (GeckoLib default for `GeoItem` — no flat icon required).
- **Recipe** (shaped, 3×3; `I`=iron ingot, `R`=redstone, `F`=blast furnace):

  ```
  I R I
  I F I
  R R R
  ```

  → 1× `custom_jetpack:jetpack`. Balanced for creative flight access; tunable later.
- **Lang:** `en_us.json` — item name, keybind `key.custom_jetpack.toggle_flight` ("Toggle Jetpack"), any config translation keys Forge needs.
- **HUD:** bottom-center-left FE bar (above hotbar) while jetpack is in the back slot; hidden when `showEnergyHud=false` or jetpack unequipped.

## 14. Identifiers and resource paths (quick reference)

| Thing | Value |
|---|---|
| Mod id | `custom_jetpack` |
| Item | `custom_jetpack:jetpack` |
| Particle | `custom_jetpack:jetpack_thruster` |
| Sounds | `custom_jetpack:jetpack_thrust_loop`, `:jetpack_thrust_start`, `:jetpack_thrust_stop` |
| Keybind | `key.custom_jetpack.toggle_flight` (default H) |
| Geometry | `assets/custom_jetpack/geo/models/jetpack.geo.json` |
| Animations | `assets/custom_jetpack/animations/jetpack.animation.json` |
| Texture | `assets/custom_jetpack/textures/models/jetpack_atlas.png` |
| Generated Java data | `com.customjetpack.data.ModelData` |

## 15. Milestones (each ends green: `gradlew build` + documented manual test gate)

- **M0 — Scaffold:** extract MDK into project root (preserving `docs/`), set `mod_id=custom_jetpack`, group `com.customjetpack`; add GeckoLib + Curios + mclib dependencies and mavens; run `gradlew build` and `runClient` (foojay provisions JDK 17). *Gate: client boots to main menu with mod listed.*
- **M1 — Model pipeline:** write `tools/glb2gecko.py` + self-test; resolve axis-mode flags by visual reference; register `JetpackItem` + `GeoModel`/renderer; render model **in hand/GUI**. *Gate: `test_glb2gecko.py` passes; model visible, correct orientation/UVs, `idle` loop plays.*
- **M2 — Curios back slot:** item tag/slot wiring, `ICurioRenderer`, offset/orientation tuning. *Gate: F5 shows pack correctly on back while walking/sneaking/looking around; equip/unequip clean.*
- **M3 — Flight core:** energy capability + config; keybind H; C2S/S2C packets; server `FlightState` + movement; fall grace; `flight` animation switch via synced state. *Gate: H toggle works; WASD/Space/Shift/W+Shift behaviors match §6; fall damage prevented; second test client sees animation + motion (MP test via `runServer` + `runClient`).*
- **M4 — FX:** particle type + provider + client spawn loop; synthesized `.ogg` loop/start/stop; `TickableSound` manager. *Gate: particles + sound only while engaged, positional for remote players.*
- **M5 — Content & polish:** recipe JSON, lang, HUD overlay, emissive fullbright layer for flagged bones, config comments, item display verification. *Gate: recipe crafts in a fresh world; HUD accurate; emissive materials glow without glowing the whole pack.*
- **M6 — Hardening:** edge-case sweep (water/lava, elytra equipped, death+respawn, dimension change, spectator, chunk unload, creative, energy exhaustion mid-air), performance sanity, release `gradlew build` jar. *Gate: checklist passes; release jar runs on a clean Forge 1.20.1 instance with the two deps.*

Git: repo initialized at M0; spec committed then; each milestone = at least one commit.

## 16. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Axis/winding mismatch on poly_mesh (converter vs GeckoLib baking) | `--flip-x/--flip-rotation` flags; M1 visual gate against reference before anything depends on the model |
| Atlas z-sorting artifacts with translucent render pass | M1/M2 single translucent pass first; if artifacts: split opaque/blended passes via `GeoRenderLayer` bone hiding (M5) |
| Quaternion→Euler conversion glitches | dense keyframes (31/channel) + ±360° unwrap; verified visually in M1 |
| Curios tag id assumption wrong | explicit M2 verification task with slot-JSON-override fallback |
| GeckoLib/mixin plugin build issue | contingency noted in M0: add mixin Gradle plugin only if build/launch complains |
| MP desync of flight | server-authoritative movement + `hurtMarked`; single S2C state packet; MP gate in M3 |
| Server cannot read baked model for particle anchors | converter emits constant offsets (`ModelData`) used by both sides |

## 17. Out of scope (YAGNI)

- Fuel items / burn-time integration (FE-only was chosen)
- Cloth Config GUI (config file only)
- Blockbench or GeckoMesh in the pipeline
- Advancements, JEI/REI integration, durability/damage, charging animation in GUI
- Localizations beyond `en_us`
- Elytra-specific interaction tweaks, swim/vehicle flight
- Server-side config GUI or per-world overrides
