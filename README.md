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

The dev game folder is `run/`. NPC skins go in `run/npc_skins/`, appearance parts in `run/npc_parts/`, extra speech files in `run/npc_speech/`, and settings in `run/config/lifeblood.cfg`.

To play it in a normal Babric install, copy `build/libs/lifeblood-*.jar` into the instance's `mods/` folder (with the game closed), and copy `npc_parts/` and `npc_skins/` into the instance's game folder.

## Commands

Everything is under `/npc` (summon, look, personality, family, factions, ownership and debugging tools); type `/npc` in game for the list.
