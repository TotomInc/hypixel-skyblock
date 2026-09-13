# Hypixel SkyBlock

Unofficial Fabric client helper for [Hypixel SkyBlock](https://hypixel.net/). Currently focused on The End: it finds Ender Nodes from their particles and draws through-wall frames around them.

Not affiliated with Hypixel or Mojang.

## Requirements

- Minecraft **26.2**
- [Fabric Loader](https://fabricmc.net/use/installer/) **0.19.5+**
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java **25**

## Features

- Detects SkyBlock and **The End** from the Hypixel sidebar
- Confirms Ender Nodes from portal and witch particles at any distance the server sends them
- Draws a cyan through-wall frame (plus a translucent fill) around each confirmed node
- Rechecks about once a second and drops nodes that go silent or whose block is gone
- `/endernodes` status, `/endernodes toggle`, and debug mark/clear commands

## Install

1. Install Fabric Loader for Minecraft 26.2
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) and this mod’s jar in your `mods` folder
3. Join Hypixel SkyBlock and go to The End

Optional: bind **Toggle Ender Node Helper** in Controls. Settings are saved to `.minecraft/config/hypixelskyblock.json`.

## Build

```bash
./gradlew build
```

The playable jar is `build/libs/hypixel-skyblock-0.1.0.jar` (skip the `-sources` jar).

## License

[CC0-1.0](LICENSE)
