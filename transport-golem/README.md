# Transport Golem (Forge 1.20.1 / 47.3.0)

Rideable 3x iron golem with a 3-seat saddle rig and 27-slot chest. Source models: `tools/source_models/*.glb`.

## Build
Needs JDK 17. From PowerShell in this directory, run `.\gradlew.bat build` -> `build/libs/transportgolem-1.20.1-1.0.0.jar`.
Run `.\gradlew.bat runClient` to test.

## How to play
- **Seating Arrangement**: shaped recipe, row 1 `iron ingot, saddle, iron ingot`, row 2 `saddle, chest, saddle`.
- **Golem**: build the pattern, then place a carved pumpkin / jack o'lantern last (top to bottom):
  `. P .` / `I I I` / `I I I` / `. I .` (I = iron block; two rows of three = the "two iron blocks" body).
  The pattern spawns the plain golem (model 1). Right-click it with a Seating Arrangement to attach the rig (model 3).
  Spawn egg gives the plain golem too.
- **Ride**: right-click the seated golem. Riders take the first open seat; use **Ctrl+1** for driver, **Ctrl+2** for left, or **Ctrl+3** for right. An occupied seat cannot be taken. Sneak to dismount.
- **Drive**: WASD + mouse look; **Space = jump** (up to 5 blocks). **Left click = 360° ground smash** (driver, 10-block radius). Unridden golems smash hostile mobs on their own.
- **Durability**: 1000 hearts; fall damage is ignored.
- **Chest**: sneak + right-click the golem.

## Tweaks (src/main/java/com/transportgolem/GolemConfig.java)
Hitbox, walk stride (foot sliding), seat anchors, sit height, smash radius/damage/timings.

## How the model works
`tools/glb_to_json.py` converts the final GLB (node tree, quads, UVs, keyframes) into
`assets/transportgolem/model/transport_golem.json`. `GolemModel` (server-safe) samples `walk` / `ground_smash`;
`TransportGolemRenderer` draws the node tree with the original texture; the seats are positioned from the same
animated Torso_Pivot transform, so riders stay on the seats during walking and the smash leap.
