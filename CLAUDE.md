# Working on Lifeblood

Read this first, then [docs/README.md](docs/README.md) (what exists and what's decided) and [docs/architecture.md](docs/architecture.md) (where everything lives). The design docs in `docs/` are the source of truth for how each system behaves; the code's class comments say how it's done.

## Principles

- **NPCs use the player's exact mechanics.** Same health, speed, healing, reach, digging speed, ammo, recipes. No buffs, no cheating, no magic placement.
- **Frameworks before features.** A new kind of thing plugs into an existing registry or extension point; a new system gets its own package. Don't special-case.
- **Look for the lines a change earns.** When NPCs would naturally notice or talk about something new, add speech lines for it in the right `.speech` file, across personalities. Don't force it: plumbing, commands and invisible systems often earn none.
- **Nothing needs a command to play.** Every feature is reached through items, screens and NPCs. Commands (`/lb`) are only tools for admins, content and testing; a feature that only works by command isn't finished.
- **Stay multiplayer-ready.** Game logic lives in common code, never in screens. Client-only code is under `client/` and `@Environment(EnvType.CLIENT)`.
- **No old-save code for now.** Development worlds are disposable until the user says long-term testing worlds have started, so breaking save changes are fine and nothing converts old data. After that, code that reads older saves goes in one `save/SaveUpgrades` class and nowhere else, and is deleted at content 1.0.
- **Lifeblood is for new worlds.** Nothing guesses about builds made before the mod was added.

## Code standards

The goal: anyone, including an AI that has never seen the project, can find the code for a behavior, understand it, and fix it.

- **Logical places.** One package per system (see the package table in `docs/architecture.md`). Per-NPC state is an `NpcPart`; world-wide state is a register saved as `SavedData`; periodic world logic is a `WorldSystem`; dialogue options are `DialogueMenus` contributors; commands are a class per area with `addTo(root)`, built with `CommandTree.branch` and taking targets as `TargetArgument`s (see the commands section of `docs/architecture.md`).
- **Reuse.** Use the existing helpers (`BlockOwners`, `Ownership`, `PrefabPlacer`, `Swaps`, `BlockTurns`, `Turns`, `Opinions`, `Kinship`, `PeopleRegistry`, `SpeechFacts`, `CommandSupport`, `NpcInventory`) instead of re-deriving things.
- **Documented.** Every class has a doc comment saying what it is and how it fits. Constants say what they mean and their unit. Non-obvious rules get a comment saying *why*. Match the surrounding style: plain English, tabs, no abbreviations.
- **Identifiable.** Names say what things are (`NpcBrawling`, `ChunkOwners`, `putAwayWeaponWhenSafe`). Mixins are named after their target and say why they exist.
- **Docs move with the code.** A change that alters behavior updates its design doc, `docs/architecture.md` if structure changed, and the settled/open lists in `docs/README.md`, in the same commit.

## Speech files

`src/main/resources/assets/lifeblood/speech/*.speech`, one per topic; the format, facts and words are listed at the top of `basics.speech`. Young speakers use age versions of symbols in `ages.speech` (`greet.child`). A new fact or word set in `speech/SpeechFacts` (or per event) must be added to the lists in `SpeechFilesTest`, which checks every condition and `[reference]`.

## Checking a change

1. `./gradlew build` (with `JAVA_HOME` set to a JDK 21) compiles and runs the tests; keep them passing and add tests for pure logic.
2. Start the dev client (`./gradlew runClient`) and wait for the `Display:` line in the log to confirm the mod loads. Mixins on world classes (chunks) only apply once a world loads: `./gradlew runServer` generates one and should reach `Done`.
3. Say plainly what was and wasn't tested in game; the user tests in their own Babric install.

## Things to know

- BTA's decompiled source isn't in the repo; Loom's `genSources` fails, so decompile `net/minecraft/**` with Vineflower when needed.
- Mixins use `remap = false`. BTA blocks are id + metadata (+ tile entity); chunks are 16 sections of 16 (height 256).
- Don't overwrite the jar in a running game's `mods/` folder: classes loaded later fail to load.
- Commit each finished, tested step with a message saying what changed and why (the user works in checkpoints), then push it to GitHub (`origin`, branch `main`, private); messages end with the attribution line the session asks for.
- Keep the AI memory up to date as decisions and plans are made: it's how later sessions know the state of the project.
