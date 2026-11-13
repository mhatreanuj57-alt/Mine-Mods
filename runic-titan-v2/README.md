# Runic Titan: Earthshaker

A Fabric mod for Minecraft 1.20.1 that adds the Earthshaker item and its techniques.

## Requirements

- Minecraft 1.20.1
- Fabric Loader and Fabric API
- JDK 17 or newer to build from source

## Build

From this directory, run:

```sh
# Windows
.\gradlew.bat build

# Linux or macOS
./gradlew build
```

The mod jar is `build/libs/runic-titan-earthshaker-2.0.0.jar`. Use that file, not the `-sources` jar.

## Install

Copy the mod jar into your Minecraft `mods` folder alongside Fabric API. Remove the older `runic-titan-earthshaker-3abs7wzr.jar` first if you have it installed.

## Use

- Give yourself the item with `/give @s runictitan:earthshaker`.
- Right-click to cast a technique.
- Sneak and right-click to switch techniques.

## Item model

The repository includes the generated item model at `src/main/resources/assets/runictitan/models/item/earthshaker.json` and its texture at `src/main/resources/assets/runictitan/textures/item/earthshaker.png`.
