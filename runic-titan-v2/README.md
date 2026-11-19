# Runic Titan Earthshaker 3.0

Forge mod for Minecraft 1.20.1 (Forge 47.x). Adds the Earthshaker, five techniques, and a chargeable hammer throw with a returning Loyalty enchantment.

## Build

From this directory, run in PowerShell. The build uses JDK 17; Gradle can download it if needed. If `JAVA_HOME` points to a missing folder, fix it before building:

```powershell
.\gradlew.bat build
```

The jar is `build/libs/runictitan-3.0.0.jar`. Install it with Forge 1.20.1. This Forge build does not run on Fabric. Remove older Earthshaker jars before launching.

## Use

Get the hammer with `/give @s runictitan:earthshaker` or craft it from the Combat tab recipe.

- Right-click to use the selected technique.
- Sneak and right-click to switch techniques.
- Hold right-click for at least 0.4 seconds, then release to throw. A 2 second charge reaches full power; charge controls throw speed and damage (25–120).
- Add a Loyalty book in an anvil to make the thrown hammer return.

Techniques: Ground Smash, Meteor Strike, Gravity Well, Thunder Crash, Earthquake, and Titanic Throw.

The source tunes charge duration, throw damage, and speed in `EarthshakerItem.java`. Its melee damage is configured as 85.
