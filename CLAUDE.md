# Working on Lifeblood

Keeping this project easy to find your way around is the top priority, above any one feature. The rules below and in [docs/architecture.md](docs/architecture.md) apply to every change, however small, and `ArchitectureTest` enforces the mechanical ones on every build.

## Before you change anything

1. **Find it, don't search for it.** Go in this order:
   - [docs/architecture.md](docs/architecture.md): the packages, how they fit, and where new things go;
   - [docs/code-map.md](docs/code-map.md): every class, one line each, generated from the doc comments;
   - the package's `package-info.java`, then the class.

   Reading the code map is cheaper than grepping the whole project, and it's always current.
2. **Check it doesn't exist already.** Search the code map for the idea, not just the name: "turn", "owner", "opinion", "years". If something close exists, use it or extend it, never write a second one. Common helpers: `BlockOwners`, `Ownership`, `PrefabPlacer`, `Swaps`, `BlockTurns`, `Turns`, `Opinions`, `Kinship`, `PeopleRegistry`, `Calendar`, `SpeechFacts`, `CommandSupport`, `NpcInventory`, `ClientHooks`.
3. **Read the design doc** for the system ([docs/README.md](docs/README.md) lists them). The design docs say how each system behaves; the class comments say how it's done.

## Principles

- **NPCs use the player's exact mechanics.** Same health, speed, healing, reach, digging speed, ammo, recipes. No buffs, no cheating, no magic placement.
- **Frameworks before features.** A new kind of thing plugs into an existing registry or extension point; a new system gets its own package. Don't special-case.
- **Look for the lines a change earns.** When NPCs would naturally notice or talk about something new, add speech lines for it in the right `.speech` file, across personalities. Don't force it: plumbing, commands and invisible systems often earn none.
- **Nothing needs a command to play.** Every feature is reached through items, screens and NPCs. Commands (`/lb`) are only tools for admins, content and testing; a feature that only works by command isn't finished.
- **Stay multiplayer-ready.** Game logic lives in common code, never in screens. Client-only code is under `client/` and `@Environment(EnvType.CLIENT)`; common code reaches the client only through `ClientHooks`.
- **No old-save code for now.** Development worlds are disposable until the user says long-term testing worlds have started, so breaking save changes are fine and nothing converts old data. After that, code that reads older saves goes in one `save/SaveUpgrades` class and nowhere else, and is deleted at content 1.0.
- **Time is real time in in-game years.** One in-game year (`world/Calendar`, 56 days by default) stands for a real year in every time-based mechanic, as growing up does (18 years). Write durations in years (or days derived from the calendar), and size them to how long the thing takes in life.
- **Lifeblood is for new worlds.** Nothing guesses about builds made before the mod was added.

## Code standards

The goal: anyone, including an AI that has never seen the project, can find the code for a behavior, understand it, and fix it, without reading code that has nothing to do with it.

- **One system, one package.** Everything about a system lives in its package: its NPC part, its behaviors, its saved data, its helpers. Where each kind of thing goes is in the "Where new things go" table in `docs/architecture.md`. Per-NPC state is an `NpcPart`; world-wide state is a register saved as `SavedData`; periodic world logic is a `WorldSystem`; dialogue options are `DialogueMenus` contributors; commands are a class per area with `addTo(root)`, built with `CommandTree.branch` and taking targets as `TargetArgument`s.
- **New package, new row.** A new package gets a `package-info.java` (what it's for, where to start, what doesn't belong) and a row in the package table of `docs/architecture.md`.
- **Reuse.** Use what exists (see "Before you change anything"). If a helper you need is buried in another class, move it somewhere both can reach, in the package that owns the idea, rather than copying it.
- **Documented.** Every class has a doc comment whose first sentence says what it is: that sentence becomes its line in the code map, so make it something a searcher would recognize. Constants say what they mean and their unit. Non-obvious rules get a comment saying *why*. Match the surrounding style: plain English, tabs, no abbreviations.
- **Identifiable.** Names say what things are (`NpcBrawling`, `ChunkOwners`, `putAwayWeaponWhenSafe`) and are unique across the project. Mixins are named after their target and say why they exist.
- **Small files.** Under 600 lines. When a class grows past that, split it into a part, a helper or a sub-package; don't raise the limit. When a package passes about 15 classes, give it sub-packages, as `ai/behavior` and `ai/routine` are.
- **Leave it tidier.** When a change shows something in the wrong place, a missing comment, or two pieces of code doing the same job, fix it in the same change, or say so if it's too big.
- **Docs move with the code.** A change that alters behavior updates its design doc, `docs/architecture.md` if structure changed, and the settled/open lists in `docs/README.md`, in the same commit. `docs/code-map.md` is generated: never edit it, change the doc comments.

## What the build enforces

`ArchitectureTest` fails the build when:
- a package has no `package-info.java`, or a class has no doc comment;
- a package isn't in the architecture doc;
- common code imports client code, or anything depends on `command/`;
- two classes share a name;
- a file is over 600 lines;
- a mixin is unlisted or misnamed;
- a test isn't beside what it tests.

`CodeMapTest` rewrites `docs/code-map.md` when it's out of date and fails once, so build again and commit the map with the change. Never weaken a rule to get a build through. Rules change only on purpose, in CLAUDE.md, `docs/architecture.md` and the test together.

## Speech files

`src/main/resources/assets/lifeblood/speech/*.speech`, one per topic; the format, facts and words are listed at the top of `basics.speech`. Young speakers use age versions of symbols in `ages.speech` (`greet.child`). A new fact or word set in `speech/SpeechFacts` (or per event) must be added to the lists in `SpeechFilesTest`, which checks every condition and `[reference]`.

## Checking a change

1. `./gradlew build` (with `JAVA_HOME` set to a JDK 21) compiles and runs the tests; keep them passing and add tests for pure logic, in the same package as the class.
2. Start the dev client (`./gradlew runClient`) and wait for the `Display:` line in the log to confirm the mod loads. Mixins on world classes (chunks) only apply once a world loads: `./gradlew runServer` generates one and should reach `Done`.
3. Say plainly what was and wasn't tested in game; the user tests in their own Babric install.

## Things to know

- BTA's decompiled source isn't in the repo; Loom's `genSources` fails, so decompile `net/minecraft/**` with Vineflower when needed.
- Mixins use `remap = false`. BTA blocks are id + metadata (+ tile entity); chunks are 16 sections of 16 (height 256).
- Don't overwrite the jar in a running game's `mods/` folder: classes loaded later fail to load.
- Commit each finished, tested step with a message saying what changed and why (the user works in checkpoints), then push it to GitHub (`origin`, branch `main`, private); messages end with the attribution line the session asks for.
- Keep the AI memory up to date as decisions and plans are made: it's how later sessions know the state of the project.
