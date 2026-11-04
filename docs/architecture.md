# Architecture

How the mod is put together, and where new systems plug in. Everything here is in `src/main/java/npclaude/`.

## Packages

| Package | What's in it |
|---|---|
| `ai` | The brain (`NpcBrain`), saved behaviors (`NpcBehaviors`), the navigator; `ai/behavior` holds follow, stay, go to, wander, recover; `ai/routine` holds schedules |
| `nav` | The pathfinder: the world as walkable spaces (`NavGrid`), the search, paths, the shared per-tick budget |
| `entity` | `EntityNPC` and its parts (life, health, eating, social, awareness, following, brawling), plus small enums (gender, age stage, health mode) |
| `inventory` | What an NPC carries, and the inventory screen's slots |
| `personality` | Trait axes, quirks, tastes, item defaults, surroundings (climate, biome likes) |
| `social` | Memories, opinions, compatibility, brawls, reactions to deaths |
| `family` | Kinship, romance, children, the calendar (ages, birthdays) |
| `people` | The register of everyone who has lived in the world |
| `combat` | Fighting, archery, threat, targeting, factions and relations |
| `dialogue` | Conversation menus and their options: gifts, romance, words, chat limits |
| `speech` | The speech engine, the line library, facts for lines, speech styles, the voice |
| `words` | The word book: words players teach |
| `appearance` | Looks from parts and palettes, slim arms |
| `skin` | Whole-skin files from `npc_skins/` |
| `ownership` | Who placed each block, and who shares what |
| `world` | World systems: logic that runs per world on its own clock |
| `save` | Reading old saves (temporary, see below) |
| `command` | `/npc`, one class per area |
| `interaction` | The bridge from right-clicking an NPC to the screens |
| `item` | The mod's items (guardian amulet, word book) |
| `client` | Everything client-only: renderer, textures, screens |
| `mixin` | Changes to BTA classes, each named after its target |
| `perf`, `util` | Profiling and small shared helpers |

## The NPC

`entity/EntityNPC` is the entity. It does only what every mob must: moving, fighting, being hurt, equipment, saving. Everything else lives in parts it holds:

| Field | Class | What it is |
|---|---|---|
| `brain` | `ai/NpcBrain` | Picks the behavior to run each tick |
| `navigator` | `ai/NpcNavigator` | Pathfinding and moving along paths |
| `inventory` | `inventory/NpcInventory` | Armor, hand and a 35-slot pack: as much as a player carries |
| `personality` | `personality/NpcPersonality` | Trait axes, quirks and tastes |
| `memories` | `social/NpcMemories` | What happened, feeding mood and opinions |
| `relations` | `combat/NpcRelations` | Debug overrides of how it treats players |
| `voice` | `speech/NpcVoice` | Saying lines, with cooldowns and its speech style |
| `life` | `entity/NpcLife` | Birth, growing up, birthdays, homeland |
| `health` | `entity/NpcHealth` | Health mode, being downed, recovering |
| `eating` | `entity/NpcEating` | Eating by the player's rules |
| `social` | `entity/NpcSocial` | Rescues, gift days, chat limits, romance questions |
| `awareness` | `entity/NpcAwareness` | Noticing weather, seasons and places |
| `following` | `entity/NpcFollowing` | Following a player and watching what they do |
| `brawling` | `entity/NpcBrawling` | Fistfights, and telling a brawl punch from an attack |
| `routine` | `ai/routine/NpcRoutine` | Its schedule for the day |

The last eight are `NpcPart`s: each has its own `tick()`, `save()` and `load()`, writing its own keys into the NPC's save compound. **A new per-NPC system is a new part:** write the class, add a field, add it to the `parts` list. The entity calls the rest.

## What drives an NPC

`NpcBrain` runs the highest-priority behavior that can run, in three layers:

1. **Instincts**: combat, brawls, recovering, staying near a parent. Built into every NPC, never saved.
2. **Orders**: follow, stay, go somewhere. Set by players and commands, and saved. Every behavior that can be saved is registered in `ai/NpcBehaviors`.
3. **Routine**: with no orders, the NPC does what its schedule says for the hour.

Routines are in `ai/routine`. A `Schedule` is a list of blocks ("from 6, work; from 18, socialize; from 22, sleep"), and each activity names the behavior that carries it out. Both are registered in `Routines`. Today there's one activity (wander) and one schedule (wander all day). **Occupations plug in here:** register their activities and schedules, then set an NPC's schedule.

## The world

**The register of people** (`people/PeopleRegistry`, saved as `npclaude_people`) has a record for everyone who has lived in the world: name, gender, birth, homeland, faction, where and when they were last seen, whether they're alive, their parents and partnerships. Records outlast entities, so anything that needs to reason about people who aren't loaded (relatives, rulers, voters, gossip) reads the register. NPCs check in as they tick. `loaded(world, id)` finds a loaded NPC by id without searching the world.

**World systems** (`world/WorldSystems`) run on a per-world clock, whether or not any NPC is nearby. A `WorldSystem` has an interval and a `tick(world)`. Register it in `WorldSystems`. The first one keeps the register's list of loaded NPCs tidy. **Settlements, factions, elections, wars and trade between towns go here.** The clock counts steadily even when the time of day jumps (sleeping) or is frozen.

**Ownership** (`ownership/`) records who placed each block, saved with each chunk (`ChunkOwners`, added to chunks by `ChunkMixin` and saved by `ChunkLoaderMixin`). `BlockOwners` is the way in: `ownerAt`, `mayUse`, and `placingAs(owner, action)` so blocks an NPC places are its own; `ItemStackMixin` does the same for players using an item on a block. `Ownership.shares` says who shares what someone owns. Items are owned by where they are: an NPC's inventory, or a container whose block someone owns.

**Factions** (`combat/FactionRegistry`, saved as `npclaude_factions`) hold each faction's default stance, explicit relations between factions, and which faction each player has joined.

**The word book** (`words/WordBook`, saved as `npclaude_words`) holds every word players have taught.

## Talking

**Dialogue menus** (`dialogue/DialogueMenus`): the main menu, the questions menu and the practical menu are built from contributions, each with an order number. **A new system adds options by registering a contributor**, never by editing the menus. The built-in options use steps of 100 (`NpcDialogue`'s static block).

Options act on the conversation through `DialogueSession`, which the client's dialogue screen implements. Option actions change the game directly. That works in single-player. For multiplayer, the session is where a message to the server would go: keep game logic in the option actions and out of the screens, and it stays a contained change.

**Speech** is data: `assets/npclaude/speech/*.speech`, one file per topic, with the format described at the top of `basics.speech`. Extra `.speech` files in the game folder's `npc_speech/` add lines without rebuilding (`/npc speech reload`). Facts and words a line can use are set in `speech/SpeechFacts` and per event. `SpeechFilesTest` checks every condition and `[reference]`: a new fact or word needs adding to its lists.

## Saving and old saves

Each system saves its own data: parts into the NPC's compound, world registers as `SavedData`. Everything that reads older saves lives in `save/SaveUpgrades`, which rewrites old data into the current format before the normal loading code sees it. It's temporary by design: **delete the class and its calls** once old development worlds no longer matter (planned for the content 1.0, before any public release).

## Settings

`NpcConfig` reads `config/npclaude.cfg`: the item ids (in case they clash with another mod) and how many years children take to grow up.

## Commands

`/npc` is assembled in `command/NpcCommand` from one class per area (`NpcBasicsCommands`, `PersonalityCommands`, `FactionCommands`, `FamilyCommands`, `LookCommands`, `OwnershipCommands`), sharing `CommandSupport` for suggestions, finding NPCs and replying. **A new area gets its own class** with an `addTo(root)` method.

## Tests

`./gradlew test` runs the unit tests in `src/test/java`. They cover pure logic: the calendar, kinship, age stages, appearance encoding, slim arms, words, save upgrades, the register of people, schedules, ownership records, and the speech files. Tests run with the default config, without a world.
