RUNIC TITAN EARTHSHAKER v2 (Fabric 1.20.1)

BUILD
  1. Install JDK 17 or newer.
  2. Open a terminal in this folder and run:   gradlew build        (Windows)
                                                ./gradlew build     (Linux/Mac)
  3. The mod jar is build/libs/runic-titan-earthshaker-2.0.0.jar  (NOT the -sources jar)
  4. Put it in your Fabric 1.20.1 mods folder together with Fabric API.
     Delete the old runic-titan-earthshaker-3abs7wzr.jar first.

USE
  Item: /give @s runictitan:earthshaker
  Right-click = cast.  Sneak + right-click = switch technique.

MODEL
  The in-game item model is generated from hammer_bulky.glb (128-voxel model converted to
  ~1100 boxes + a 1024x1024 colour texture). Files: assets/runictitan/models/item/earthshaker.json
  and assets/runictitan/textures/item/earthshaker.png
