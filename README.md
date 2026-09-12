# Hypixel SkyBlock

Fabric client mod for Hypixel SkyBlock, targeting Minecraft 26.2. The first feature is an Ender Node tracker for The End, modeled on [Skyblocker](https://github.com/SkyblockerMod/Skyblocker)'s helper.

## Features

- Detects SkyBlock and **The End** from the Hypixel sidebar
- Confirms Ender Nodes from portal and witch particles at any distance the server sends them (far unloaded chunks confirm from a single complete face)
- Draws a cyan **through-wall frame** (plus a translucent fill) around each confirmed node
- Rechecks about once a second and drops nodes that go silent or whose block is gone
- `/endernodes` status, `/endernodes toggle`, and debug commands to mark/clear nodes

## Setup

1. Install JDK 25
2. Import the Gradle project in your IDE, or run `./gradlew genSources` then `./gradlew runClient`
3. Put the built jar from `build/libs/` into your Minecraft `mods` folder along with Fabric Loader and Fabric API

## In-game

- Open **Controls** and bind **Toggle Ender Node Helper** if you want a hotkey
- Config is written to `.minecraft/config/hypixelskyblock.json`

Detection only runs while the sidebar shows SkyBlock and the area is The End.

## License

CC0-1.0, same as the Fabric example mod this project started from.
