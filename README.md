# Lifeblood

An NPC framework mod for **Better Than Adventure 8.0.1** (Babric/Fabric, HalpLibe 6.2.0): villagers with personalities, opinions, memories, families, their own way of talking, and eventually their own working lives, towns and politics.

- **What's built and how it works:** [docs/README.md](docs/README.md) (index of every design doc, settled decisions, open questions).
- **How the code is organized:** [docs/architecture.md](docs/architecture.md).
- **Working on it with an AI:** [CLAUDE.md](CLAUDE.md).

## Building and running

Needs JDK 21 to build (the mod itself targets Java 17, which BTA runs on).

```bash
./gradlew build        # jar in build/libs/, runs the unit tests too
./gradlew test         # unit tests only
./gradlew runClient    # dev copy of the game (or double-click run-dev.bat)
./gradlew runServer    # dedicated server
```

The dev game folder is `run/`. NPC skins go in `run/npc_skins/`, appearance parts in `run/npc_parts/`, extra speech files in `run/npc_speech/`, saved prefabs in `run/npc_prefabs/`, and settings in `run/config/lifeblood.cfg`.

To play it in a normal Babric install, copy `build/libs/lifeblood-*.jar` into the instance's `mods/` folder (with the game closed), and copy `npc_parts/` and `npc_skins/` into the instance's game folder.

## Commands

Nothing in Lifeblood needs a command to play: everything is done with items, screens and the NPCs themselves. The commands are tools for admins, making content and testing, all under `/lb`, grouped into categories (`npc`, `personality`, `tastes`, `social`, `family`, `look`, `faction`, `prefab`, `words`, `owner`, `reload`, `debug`). Type `/lb`, or any part of a command, to list what's under it. Targets take vanilla selectors like `@s` and `@e[type=lifeblood:npc,name=Walter]`, or an NPC's name.
