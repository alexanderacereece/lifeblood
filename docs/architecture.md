# Architecture

How the mod is put together, and where new systems plug in. Everything here is in `src/main/java/lifeblood/`.

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
| `people` | The register of everyone who has lived in the world, and the name pool (`NamePool`, from `names.txt`) |
| `combat` | Fighting, archery, threat, targeting, factions and relations |
| `dialogue` | Conversation menus and their options: gifts, romance, words, chat limits |
| `speech` | The speech engine, the line library, facts for lines, speech styles, the voice |
| `words` | The word book: words players teach |
| `appearance` | Looks from parts and palettes, slim arms |
| `skin` | Whole-skin files from `npc_skins/` |
| `ownership` | Who placed each block, and who shares what |
| `building` | Prefabs: saving, turning (`BlockTurns`), material and color swaps (`Swaps`, `WoodType`, `StoneFamily`), surveying a site (`LocalMaterials`), placing, the save check |
| `world` | World systems: logic that runs per world on its own clock |
| `command` | `/lb`, one class per area; admin and testing tools only |
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

**The register of people** (`people/PeopleRegistry`, saved as `lifeblood_people`) has a record for everyone who has lived in the world: name, gender, birth, homeland, faction, where and when they were last seen, whether they're alive, their parents and partnerships. Records outlast entities, so anything that needs to reason about people who aren't loaded (relatives, rulers, voters, gossip) reads the register. NPCs check in as they tick. `loaded(world, id)` finds a loaded NPC by id without searching the world.

**World systems** (`world/WorldSystems`) run on a per-world clock, whether or not any NPC is nearby. A `WorldSystem` has an interval and a `tick(world)`. Register it in `WorldSystems`. The first one keeps the register's list of loaded NPCs tidy. **Settlements, factions, elections, wars and trade between towns go here.** The clock counts steadily even when the time of day jumps (sleeping) or is frozen.

**Ownership** (`ownership/`) records who placed each block, saved with each chunk (`ChunkOwners`, added to chunks by `ChunkMixin` and saved by `ChunkLoaderMixin`). `BlockOwners` is the way in: `ownerAt`, `mayUse`, and `placingAs(owner, action)` so blocks an NPC places are its own; `ItemStackMixin` does the same for players using an item on a block. `Ownership.shares` says who shares what someone owns. Items are owned by where they are: an NPC's inventory, or a container whose block someone owns.

**Factions** (`combat/FactionRegistry`, saved as `lifeblood_factions`) hold each faction's default stance, its colors (`FactionColors`: primary and secondary), explicit relations between factions, and which faction each player has joined.

**The word book** (`words/WordBook`, saved as `lifeblood_words`) holds every word players have taught.

## Talking

**Dialogue menus** (`dialogue/DialogueMenus`): the main menu, the questions menu and the practical menu are built from contributions, each with an order number. **A new system adds options by registering a contributor**, never by editing the menus. The built-in options use steps of 100 (`NpcDialogue`'s static block).

Options act on the conversation through `DialogueSession`, which the client's dialogue screen implements. Option actions change the game directly. That works in single-player. For multiplayer, the session is where a message to the server would go: keep game logic in the option actions and out of the screens, and it stays a contained change.

**Speech** is data: `assets/lifeblood/speech/*.speech`, one file per topic, with the format described at the top of `basics.speech`. Extra `.speech` files in the game folder's `npc_speech/` add lines without rebuilding (`/lb reload speech`). Facts and words a line can use are set in `speech/SpeechFacts` and per event. `SpeechFilesTest` checks every condition and `[reference]`: a new fact or word needs adding to its lists.

## Saving and old saves

Each system saves its own data: parts into the NPC's compound, world registers as `SavedData`. Nothing converts older saves right now: development worlds are disposable, so formats change freely. NPCs save a `DataVersion` so that, once long-term worlds exist, a `save/SaveUpgrades` class can rewrite old data into the current format before the normal loading code sees it. That class would be temporary by design and deleted at content 1.0.

## Settings

`NpcConfig` reads `config/lifeblood.cfg`: the item ids (in case they clash with another mod) and how many years children take to grow up.

## Commands

**Commands are tools, never the way to play.** Everything a player does happens through items, screens and NPCs; `/lb` is for admins, building content and testing. It's assembled in `command/LifebloodCommand` from one class per area (`NpcCommands`, `PersonalityCommands` for personality, tastes and social, `FamilyCommands` for family and words, `LookCommands`, `FactionCommands`, `PrefabCommands`, `ToolCommands` for owner, reload and debug), sharing `CommandSupport` for suggestions and replies. **A new area gets its own class** with an `addTo(root)` method.

Rules every command follows:
- **Made with `CommandTree.branch(path, description)`**, so typing any part of the tree on its own lists what's under it with descriptions (`/lb`, `/lb npc`, `/lb npc heal`), and vanilla `/help lb npc` works too.
- **Vanilla's shape:** the verb, then who it's for, then values (`/lb npc heal <npcs>`, like `/heal <entities>`). Given who but no value, a command shows the current value (`/lb npc gender @e[type=lifeblood:npc]`), like `/gamerule`. Commands that change several NPCs reply per NPC, or with a count for many.
- **Targets are `TargetArgument`s**: any vanilla selector (`@s`, `@p`, `@e[type=lifeblood:npc,name=Walter,limit=1,sort=nearest]`), an entity id, or a name (an NPC's, quoted if it has spaces, or a player's). Single targets refuse selectors that could match several, as vanilla does; a name shared by several NPCs picks the nearest.

## Tests

`./gradlew test` runs the unit tests in `src/test/java`. They cover pure logic: the calendar, kinship, age stages, appearance encoding, slim arms, words, the register of people, schedules, ownership records, prefab data, turning (positions, directions and every facing rule going full circle), faction colors, and the speech files. Tests run with the default config, without a world.
