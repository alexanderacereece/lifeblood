# Travelers and adventures (built)

NPCs who are out in the world instead of at home: homesteaders off on a journey, an adventure in the caves or a visit to the neighbors, and strangers passing through from beyond where the player has been. With [generated homesteads](building.md#generated-homesteads-built) and [daily life](daily-life.md), this is the **first playtest**: a world with a mix of NPCs living in places and NPCs showing up on their own, so the pace of the mod can be felt. Numbers are starting points for playtesting. The code is in `travel/`.

## What it should feel like

Meeting someone out in the world should feel like meeting a person with somewhere to be, not a mob that spawned. They're going somewhere for a reason, doing it by the player's rules, and they remember it afterwards. You might pass a traveler on the road who tells you where they're headed, find someone dug in for the night, or come across an adventurer deep in a cave lighting the way with their last torches.

## Who goes, and when

Every traveler is a real person in the [register of people](architecture.md), with a home and a reason.
- **Homesteaders** think about setting out once each morning (6 to 9). A typical adult takes about 6 trips a year (`Game.tripsPerYear` in `config/lifeblood.cfg`): **wanderers** four times as many, **homebodies** a tenth (a new quirk pair, see [personality](personality.md)), the curious half again as many, the brave a little more and the timid a little less. Children never go, and nobody leaves under orders (following, staying).
- **Strangers** set out about 220 blocks beyond a player, for a home some 1500 blocks off on the far side, so their road passes near (`Strangers`; `Game.strangersPerDay`, 0.75 per player by default). They're made as real people: a name, an age, food, torches, often a sword or a bow, and a place in the register.

## Kinds of trip

Each kind is an `OutingKind` registered from `TravelSetup`, so jobs and later systems can add more.

| Trip | Who | What happens |
|---|---|---|
| Journey (`JourneyKind`) | anyone; wanderers go twice as far, the curious more often | Walks 150 to 400 blocks (wanderers 300 to 800) in a random direction, looks around for an hour or three, walks home. Takes a weapon. |
| Adventure (`AdventureKind`) | the brave, curious and greedy; only with a pickaxe and torches to take | Walks 60 to 200 blocks off, goes down into the caves there (`CaveExplorer`), and comes home with what they found. |
| Visit (`VisitKind`) | extroverts most; only with another homestead within 500 blocks | Walks to the nearest other homestead, spends a few hours around it (chatting with whoever's there, as nearby NPCs do), walks home. |

## Doing it honestly

- **Packing:** homesteaders take food from the household's chests and what the trip needs (a pickaxe and torches for caves, a weapon, a bow and arrows), and put everything they bring back into the chests on coming home, but for a little food.
- **Walking far** (`LongWalk`): the path search only plans so far, so trips go in legs of about 24 blocks, bending left and right around cliffs and lakes; a long run of legs that won't plan means they're lost, and turn for home.
- **Nights:** from 20 to 6 they take shelter. Near another homestead (within 32 blocks) they stay by its door. Otherwise they dig in as a player would (`Shelter`): two blocks down where they stand, a block over their head from what they dug, and in the morning they open it and cut a step to climb out. Where the ground won't do (water, sand, bedrock-hard stone) they try a few spots, then sleep rough. Homebodies are homesick at night.
- **Caves** (`CaveExplorer`): they look for dark open space underground near where they've come to and walk in; if the paths only lead near, after a few tries they dig a short stair down (16 steps at most). Inside they light the way with torches at their feet where it's dark, mine ore they come across with their pickaxe, empty any chest nobody owns (dungeons), and fight what's there (the fight-or-flight instinct). They turn back when badly hurt, out of torches in the dark, out of food and hurt, loaded down, or near the end of their stay, and climb out, digging a stair up if they must. Underground they don't shelter for the night.
- **Danger is real:** monsters can hurt or kill a traveler in the world. Out of sight, an adventure has a small chance of not coming back (about 1.5%, more for the brave and reckless, double for the unarmed, half for the armored, more with no food); a death out there is posted like any other, so the family grieves and the news travels.

## Life while out of sight

A trip never stops because nobody's watching (`TravelRegister`, `TravelSimulation`):
- A traveler more than 96 blocks from every player is saved whole and taken out of the world. A world system carries their trip on: walking at a little under a player's pace by day, resting at night, staying as long as they meant to, and heading home. What happened during the stay is worked out by the trip's kind: what they found (coal, iron, mob drops, now and then a dungeon's treasure), whether they got hurt, whether they died.
- Where their road comes within 80 blocks of a player, they're put back in the world just as they were, with what they found and the health they lost. A traveler who's got home waits there as a record until a player is near enough (128 blocks) to keep the house loaded, then is put back inside it. Packing and putting things away happen at home, in the world.
- A traveler whose area unloaded with them (the player flew off) catches up on the time when it loads again. Time skipped by sleeping counts the same way.
- A stranger who reaches their far-off home leaves these parts for good; their record stays in the register of people.

## Coming home, and talking about it

Coming home leaves a memory that lifts their mood for a while: went on a journey, had an adventure, found treasure, came back hurt, visited the neighbors. They say how it went ("Mostly in one piece.", "Look what I found!"), bring it up in chats (the travels topic: "Found a chest down there. A whole chest!"), and the listener answers by their own nature ("I'd never go that far from home."). Met on the road, they greet you by what they're at ("Just passing through.", "Shh. Trying to get some rest in here.") and can be asked **"Where are you headed?"**: where, which way and how far ("Home. North-east of here, a day or two's walk."). The lines are in `travel.speech`.

## Testing

`/lb travel send <npcs> <journey|adventure|visit>` sends NPCs now, `/lb travel stranger` starts one toward you, `/lb travel list` shows who's out of sight and how far along. On a dedicated server with nobody on it, `/lb debug keepreal on` keeps travelers in the world as if a player were near, and `/lb debug speed` runs the game faster.

## Not yet

- Homesteads far from every player don't live while unloaded: their people only start trips when their area is loaded, and chores don't catch up ([unloaded](unloaded.md)).
- How well a traveler fights, aims and finds its way is its [skill](skills.md), planned next; for now every NPC is as good as the code.
- Strangers' homes are a point on the map; when generated settlements exist, they become real places.
- Gathering trips, hunting trips farther from home, and travelers joining the player for a while.
