# Lifeblood

An NPC framework mod for **Better Than Adventure 8.0.1** (Babric/Fabric, HalpLibe 6.2.0): villagers with personalities, opinions, memories, families and their own way of talking, living in homesteads the world generates, doing their own chores, and going on trips, with their days carrying on while nobody is near. Working lives, towns and politics come next.

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

To play it in a normal Babric install, copy `build/libs/lifeblood-*.jar` into the instance's `mods/` folder (with the game closed). Appearance parts, speech and prefabs come bundled in the jar; copy `npc_parts/`, `npc_skins/`, `npc_speech/` or `npc_prefabs/` into the instance's game folder only to add your own. Development builds are also posted as pre-releases on GitHub.

## Commands

Nothing in Lifeblood needs a command to play: everything is done with items, screens and the NPCs themselves. The commands are tools for admins, making content and testing, all under `/lb`, grouped into categories (`npc`, `personality`, `tastes`, `social`, `family`, `look`, `faction`, `prefab`, `homestead`, `travel`, `words`, `owner`, `reload`, `debug`). Type `/lb`, or any part of a command, to list what's under it. Targets take vanilla selectors like `@s` and `@e[type=lifeblood:npc,name=Walter]`, or an NPC's name.
