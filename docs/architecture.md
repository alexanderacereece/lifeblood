# Architecture

How the mod is put together, where things go, and the rules that keep it that way. Everything is in `src/main/java/lifeblood/`.

## Finding things

Four layers, each finer than the last. Read down only as far as you need:

1. **[CLAUDE.md](../CLAUDE.md):** the principles and the working rules.
2. **This page:** the packages, how the systems fit together, and where new things go.
3. **[code-map.md](code-map.md):** every package and class with one line on what it is. It's generated from the doc comments on every build, so it's never out of date. **Search it before writing anything new**: if something close exists, use it or extend it.
4. **`package-info.java` in each package:** what the package is for, which class to start with, and what doesn't belong in it. Then the class doc comments.

## Packages

**One system, one package: everything about a system lives in its package.** That covers its NPC part, its behaviors, its saved data and its helpers. Commands, screens and mixins are the exceptions, since they're layers over every system.

| Package | What's in it |
|---|---|
| `lifeblood` | The entry point (`Lifeblood`), the list of every system's setup (`Systems`), the mod id and log (`Mod`), settings (`NpcConfig`), and `ClientHooks`, the only way common code reaches the client |
| `entity` | `EntityNPC`, `NpcPart` and `NpcGender`. Only the core; parts live with their systems |
| `event` | The event hub (`LifeEvents`) and its events: deaths, births, real attacks, gifts, couples changing |
| `ai` | The brain (`NpcBrain`), the registry of saved behaviors (`NpcBehaviors`), and the NPC's home (`NpcHome`); `ai/behavior` holds the plain orders (go to, stay, wander), `ai/routine` the daily schedules |
| `nav` | Getting around: the walkable-space grid, the path search, the per-tick budget, and `NpcNavigator`, which walks paths |
| `health` | Health modes, being downed and recovering (`RecoverBehavior`), eating |
| `family` | Kinship, romance, children, life stages and birthdays (`NpcLife`, `AgeStage`), a child keeping near a parent |
| `people` | The register of everyone who has lived in the world, and the name pool |
| `personality` | Trait axes, quirks, tastes, item defaults, attraction, surroundings, and noticing them (`NpcAwareness`) |
| `social` | Memories, opinions, compatibility, social events, dealings with particular people (`NpcSocial`) |
| `brawl` | Fistfights: the brawl, the behavior, and the NPC's part |
| `following` | Following a player: the order, the distances, the NPC's part |
| `combat` | Real fights: fight or flight, threat, melee (`NpcMelee`) and archery, attackers and grudges, mobs targeting NPCs |
| `faction` | Factions, their colors and relations, and how an NPC regards a person (`NpcFactions`) |
| `inventory` | What an NPC carries, and its menu |
| `dialogue` | Conversations (`NpcConversation`), their menus and options: gifts, romance, words, chat limits |
| `speech` | The speech engine, the line library, facts for lines, speech styles, the voice |
| `words` | The word book: words players teach |
| `appearance` | An NPC's looks (`NpcLooks`): parts and palettes, whole skin files, slim arms |
| `ownership` | Who placed each block, and who shares what |
| `building` | Prefabs: saving, turning, material and color swaps, surveying a site, placing, the save check |
| `world` | The calendar (`Calendar`), and world systems that run per world on their own clock |
| `item` | The mod's items |
| `command` | `/lb`, one class per area; admin and testing tools only |
| `client` | Everything client-only: renderer, textures, screens |
| `mixin` | Changes to BTA classes, each named after its target |
| `perf`, `util` | Profiling, and small helpers shared by several systems |

## Layers

Packages are stacked in layers. A package may use its own layer and the ones below, never the ones above. So the basics (time, the register of people, the NPC itself) can be understood and changed without the systems built on them:

| Layer | Packages | What it is |
|---|---|---|
| 1. Foundations | `util`, `perf`, `world`, `Mod`, `NpcConfig` | Time, world systems, the log and settings |
| 2. The NPC | `entity`, `event`, `people`, `nav`, `inventory`, `item`, `appearance`, `ai`, `ClientHooks` | The entity, its body, its brain, the register of people, the event hub |
| 3. Who they are | `personality`, `social`, `faction`, `family`, `ownership`, `speech`, `words` | Character, feelings, family, property, voice |
| 4. What they do | `combat`, `brawl`, `health`, `following`, `building` | Systems that act in the world |
| 5. Conversation | `dialogue` | Talking with the player, which draws on everything |
| 6. Tools and sides | `command`, `client`, `mixin`, `Lifeblood`, `Systems` | Wiring, screens, hooks into BTA, `/lb` |

There are two exceptions: anything may use `entity`, since every part needs its NPC, and `entity` may use anything, since it holds the parts. When a lower package needs something to happen above it, turn the dependency around:
- post an **event** and let the higher system listen;
- **register** the higher system's piece from its setup class;
- or move the code up, if it really belongs higher.

## Wiring: setups and events

**Setups.** Everything a system brings, it registers in its own setup class (`FamilySetup`, `CombatSetup`...): behaviors and instincts (`ai/NpcBehaviors`), activities and schedules (`ai/routine/Routines`), world systems (`world/WorldSystems`), dialogue options (`dialogue/DialogueMenus`) and event listeners. `lifeblood/Systems` calls every setup in one ordered list, which is the one place to see everything wired into the mod. The order matters only where it says so (world systems run in registration order; death listeners too).

**Events** (`event/LifeEvents`). When something happens that other systems may care about, the code where it happens posts an event, and systems that care listen from their setup. A new system reacts to the world without editing anyone else's code.

| Event | Posted when | Listened to by |
|---|---|---|
| `DeathEvent` | Any mob dies (`MobDeathMixin`) | `social` (witnesses, grief), `people` (records the death), `following` (followers go home) |
| `BirthEvent` | A child is born (`family/Children`) | Nothing yet |
| `PartnershipEvent` | A couple starts dating, gets engaged, marries or splits (`people/PeopleRegistry`) | Nothing yet |
| `AttackedEvent` | An NPC is attacked for real (`combat/NpcAttackers`) | Nothing yet |
| `GiftEvent` | An NPC takes a gift (`dialogue/NpcGifts`) | Nothing yet |

Use an event when others should react to something; call directly when one system asks another for something. A new kind of event is a record in `event/` and a row in this table.

## Where new things go

| Adding... | Goes... |
|---|---|
| A new system (skills, work, settlements, travel) | Its own package with a `package-info.java`, plus a row in the table above |
| State every NPC has | An `NpcPart` in its system's package: a field and an entry in `parts` in `EntityNPC` |
| Something an NPC does | A behavior in its system's package, registered from the system's setup: as an order in `ai/NpcBehaviors` if it's saved, an instinct if every NPC has it, an activity in `ai/routine/Routines` if a schedule uses it |
| World-wide state | A register saved as `SavedData`, in its system's package |
| Logic that runs on its own clock | A `WorldSystem`, registered from the system's setup |
| Anything a system registers | Its setup class (`XSetup.register()`), added to `lifeblood/Systems` |
| Reacting to something that happens elsewhere | A listener on an event, from the system's setup; a new kind of event is a record in `event/` |
| Something to say in conversation | A `DialogueMenus` option, registered from `dialogue/DialogueSetup` (or the setup of a system above dialogue) |
| Lines NPCs say | A `.speech` file, with facts in `speech/SpeechFacts` |
| A command | A branch in the area's class in `command/`, or a new area class |
| A screen | `client/`, opened through a hook in `ClientHooks` |
| A hook into BTA | A thin mixin in `mixin/` that calls the system's own code |
| A helper | Wherever the system that owns the idea lives. Only truly general code goes in `util` |

## The rules, and what checks them

`ArchitectureTest` fails the build when one of these breaks. Its messages say how to fix it. Rules change only on purpose, in this page, CLAUDE.md and the test together.

| Rule | Why |
|---|---|
| Every package has a `package-info.java` with a doc comment | Says what goes where |
| Every class has a doc comment | It feeds the code map, and says what the class is |
| Every top-level package is in the table above | New systems get placed on purpose |
| Packages only use their own layer and below, except for `entity` (layer table in the test and above) | The basics stay understandable on their own |
| Registrations and event listeners only happen in setup classes, and every setup is called from `Systems` | All wiring is in one list, and nothing registers by accident of class loading |
| Common code never imports `lifeblood.client` or `net.minecraft.client` (only `client/` and the client mixins in `lifeblood.mixins.json` may) | A dedicated server has no client classes; touching one crashes it |
| Nothing outside `command/` imports it (except the entry point) | Commands are tools over the game, never part of it |
| Class names are unique across the project | A name says where to look; a second `X` means the first wasn't found |
| Source files stay under 600 lines, with no exceptions | Big files hide code and invite duplication |
| Every mixin is listed in `lifeblood.mixins.json` and named after its target | Mixins are easy to find and never silently unused |
| Tests sit in the same package as what they test | Moving a class moves its test with it |

`CodeMapTest` rewrites [code-map.md](code-map.md) whenever it's out of date and fails once, so the new map gets committed with the change.

## The NPC

`entity/EntityNPC` is the entity. It does only what every mob must: moving, being hurt, equipment, synced data, saving. Everything else lives in the pieces it holds, each in its own system's package.

Three are driven by the entity at set moments:

| Field | Class | What it is |
|---|---|---|
| `brain` | `ai/NpcBrain` | Picks the behavior to run, more often the nearer a player is |
| `navigator` | `nav/NpcNavigator` | Pathfinding and moving along paths |
| `conversation` | `dialogue/NpcConversation` | Who it's talking to, and turning players away mid-fight |
| `attackers` | `combat/NpcAttackers` | Who last attacked it for real, grudges, and how it takes a hit |

The rest are `NpcPart`s. Each has its own `tick()`, `save()` and `load()`, and saves into its own section of the NPC's save, named by `saveKey()`:

| Field | Class | What it is |
|---|---|---|
| `personality` | `personality/NpcPersonality` | Trait axes, quirks and tastes |
| `voice` | `speech/NpcVoice` | Saying lines, with cooldowns and its speech style |
| `memories` | `social/NpcMemories` | What happened, feeding mood and opinions |
| `relations` | `faction/NpcRelations` | Debug overrides of how it treats players |
| `inventory` | `inventory/NpcInventory` | Armor, hand and a 35-slot pack: as much as a player carries |
| `looks` | `appearance/NpcLooks` | Its skin file or look from parts |
| `home` | `ai/NpcHome` | The spot stay, wander and recovering are anchored to |
| `life` | `family/NpcLife` | Birth, growing up, birthdays, homeland |
| `health` | `health/NpcHealth` | Health mode, being downed, recovering |
| `eating` | `health/NpcEating` | Eating by the player's rules |
| `social` | `social/NpcSocial` | Rescues, gift days, chat limits, romance questions |
| `awareness` | `personality/NpcAwareness` | Noticing weather, seasons and places |
| `following` | `following/NpcFollowing` | Following a player and watching what they do |
| `brawling` | `brawl/NpcBrawling` | Fistfights, and telling a brawl punch from an attack |
| `melee` | `combat/NpcMelee` | Drawing, swinging and putting away weapons, by the player's rules |
| `routine` | `ai/routine/NpcRoutine` | Its schedule for the day |

**A new per-NPC system is a new part in its own package:** write the class, add a field, add it to the `parts` list. The entity calls the rest. Behavior that grows inside the entity belongs in a part instead; the file limit keeps it honest.

## What drives an NPC

`NpcBrain` runs the highest-priority behavior that can run, in three layers:

1. **Instincts:** combat, brawls, recovering, staying near a parent. Every NPC gets them (registered by each system's setup), never saved.
2. **Orders:** follow, stay, go somewhere. Set by players and commands, and saved. Every behavior that can be saved is registered in `ai/NpcBehaviors`.
3. **Routine:** with no orders, the NPC does what its schedule says for the hour.

Routines are in `ai/routine`.
- A `Schedule` is a list of blocks ("from 6, work; from 18, socialize; from 22, sleep"), and each activity names the behavior that carries it out.
- Both are registered in `Routines`.
- Today there's one activity (wander) and one schedule (wander all day).
- **Occupations plug in here:** register their activities and schedules, then set an NPC's schedule.

## The world

**The register of people** (`people/PeopleRegistry`, saved as `lifeblood_people`) has a record for everyone who has lived in the world. A record holds their name, gender, birth, homeland, faction, where and when they were last seen, whether they're alive, their parents and partnerships.
- Records outlast entities, so anything that needs to reason about people who aren't loaded (relatives, rulers, voters, gossip) reads the register.
- NPCs check in as they tick.
- `loaded(world, id)` finds a loaded NPC by id without searching the world.

**Time** is `world/Calendar`. An in-game year (56 days by default) stands for a real year in every time-based mechanic, so durations are written in years.

**World systems** (`world/WorldSystems`) run on a per-world clock, whether or not any NPC is nearby.
- A `WorldSystem` has an interval and a `tick(world)` (`WorldSystem.every` makes one); register it from the system's setup.
- The first one keeps the register's list of loaded NPCs tidy.
- **Settlements, factions, elections, wars and trade between towns go here.**
- The clock counts steadily even when the time of day jumps (sleeping) or is frozen.

**Ownership** (`ownership/`) records who placed each block, saved with each chunk.
- `ChunkOwners` is added to chunks by `ChunkMixin` and saved by `ChunkLoaderMixin`.
- `BlockOwners` is the way in: `ownerAt`, `mayUse`, and `placingAs(owner, action)` so blocks an NPC places are its own. `ItemStackMixin` does the same for players using an item on a block.
- `Ownership.shares` says who shares what someone owns.
- Items are owned by where they are: an NPC's inventory, or a container whose block someone owns.

**Factions** (`faction/FactionRegistry`, saved as `lifeblood_factions`) hold each faction's default stance, its colors (`FactionColors`: primary and secondary), explicit relations between factions, and which faction each player has joined. `faction/NpcFactions` combines these with opinions and overrides into how one NPC regards one person.

**The word book** (`words/WordBook`, saved as `lifeblood_words`) holds every word players have taught.

## Client and server

Game logic lives in common code; `client/` only shows things. When common code needs the client (open a screen, rebuild textures), it calls `ClientHooks`. The client fills the hooks in at startup (`LifebloodClient`), and on a dedicated server they do nothing. A new thing the client does for common code gets a new hook in the same shape.

## Talking

**Dialogue menus** (`dialogue/DialogueMenus`) build the main, questions and practical menus from contributions, each with an order number. **A new system adds options by registering a contributor**, never by editing the menus. The built-in options use steps of 100 (`dialogue/DialogueSetup`).

Options act on the conversation through `DialogueSession`, which the client's dialogue screen implements, and their actions change the game directly. That works in single-player. For multiplayer, the session is where a message to the server would go. Keep game logic in the option actions and out of the screens, and that stays a contained change.

**Speech** is data: `assets/lifeblood/speech/*.speech`, one file per topic.
- The format is described at the top of `basics.speech`.
- Extra `.speech` files in the game folder's `npc_speech/` add lines without rebuilding (`/lb reload speech`).
- Facts and words a line can use are set in `speech/SpeechFacts` and per event.
- `SpeechFilesTest` checks every condition and `[reference]`, so a new fact or word needs adding to its lists.

## Saving and old saves

Each system saves its own data: each part into its own section of the NPC's save (under `Parts`, named by its `saveKey()`, so parts can't overwrite each other), world registers as `SavedData`. Nothing converts older saves right now: development worlds are disposable, so formats change freely.

NPCs save a `DataVersion`. Once long-term worlds exist, a `save/SaveUpgrades` class can rewrite old data into the current format before the normal loading code sees it. That class would be temporary by design and deleted at content 1.0.

Moving a class between packages never touches saves: save keys and entity ids don't depend on class names.

## Settings

`NpcConfig` reads `config/lifeblood.cfg`: the item ids (in case they clash with another mod) and how many years children take to grow up.

## Commands

**Commands are tools, never the way to play.** Everything a player does happens through items, screens and NPCs; `/lb` is for admins, building content and testing. It's assembled in `command/LifebloodCommand` from one class per area, which share `CommandSupport` for suggestions and replies:

| Class | Covers |
|---|---|
| `NpcCommands` | NPCs |
| `PersonalityCommands` | Personality, tastes and social |
| `FamilyCommands` | Family and words |
| `LookCommands` | Looks |
| `FactionCommands` | Factions |
| `PrefabCommands` | Prefabs |
| `ToolCommands` | Owner, reload and debug |

**A new area gets its own class** with an `addTo(root)` method.

Rules every command follows:
- **Made with `CommandTree.branch(path, description)`.** Typing any part of the tree on its own lists what's under it, with descriptions (`/lb`, `/lb npc`, `/lb npc heal`). Vanilla `/help lb npc` works too.
- **Vanilla's shape:** the verb, then who it's for, then values (`/lb npc heal <npcs>`, like `/heal <entities>`).
  - Given who but no value, a command shows the current value (`/lb npc gender @e[type=lifeblood:npc]`), like `/gamerule`.
  - Commands that change several NPCs reply per NPC, or with a count for many.
- **Targets are `TargetArgument`s**, which accept:
  - any vanilla selector (`@s`, `@p`, `@e[type=lifeblood:npc,name=Walter,limit=1,sort=nearest]`);
  - an entity id;
  - a name: an NPC's (quoted if it has spaces) or a player's.

  Single targets refuse selectors that could match several, as vanilla does. A name shared by several NPCs picks the nearest.

## Tests

`./gradlew test` runs the unit tests in `src/test/java`, each in the same package as the class it tests.
- They cover pure logic: the calendar, kinship, age stages, appearance encoding, slim arms, words, the register of people, schedules, ownership records, prefab data, turning (positions, directions and every facing rule going full circle), faction colors, the command tree and the speech files.
- `ArchitectureTest` and `CodeMapTest` check the layout itself.
- Tests run with the default config, without a world.
