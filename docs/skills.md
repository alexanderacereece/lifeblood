# Skills (planned)

Not built yet. How good an NPC is at something, and how it gets better. Numbers are starting points for playtesting.

Right now every NPC fights like a veteran: any follower is a crack shot and a sharp swordsman, whoever they are. Skills fix that, and add history: a hunter is a fine shot, a sheltered homebody is a danger to everyone near them with a bow, and most people can get better with practice.

## Skills are behavior, not bonuses

**A skill changes how well the NPC plays, never the rules it plays by.** Damage, speed, reach, arrow spread and cooldowns stay exactly the player's ([player parity](README.md#settled)). What changes is the NPC's decisions and precision, the things that separate a good player from a bad one:
- where it aims and when it lets go;
- how it judges distance, movement and danger;
- how it moves in a fight, and when it backs off.

So a master archer is about as good as a good player, never better, and a novice is about as bad as someone holding a bow for the first time. There are no hidden multipliers anywhere.

## The first two skills

### Archery

| | Novice | Expert |
|---|---|---|
| **Aim** | Adds its own error to every shot, on top of the bow's normal spread | Barely any error of its own |
| **Arrow drop** | Misjudges how far arrows fall: over- and undershoots at range | Compensates correctly, as now |
| **Moving targets** | Shoots where the target is | Leads it: aims where it will be |
| **Timing** | Hesitates and fumbles between shots | Shoots as soon as the bow allows |
| **Range** | Lets enemies close in, or shoots from too far | Keeps the distance where it hits best |
| **Friendly fire** | Checks the line of fire carelessly, and sometimes hits a friend | Holds fire when a friend is in the way |
| **Ammo** | Wastes arrows on hopeless shots | Saves them for shots it can make |

### Melee

| | Novice | Expert |
|---|---|---|
| **Reactions** | Slow to turn and answer an attack | Quick |
| **Timing** | Swings wildly, often out of reach | Strikes when the target is in reach and the swing counts |
| **Footwork** | Walks straight in and trades blows | Steps in to hit and back out, circles, uses knockback |
| **Crowds** | Gets surrounded | Keeps enemies in front, fights at a doorway or corner |
| **Creepers** | Stands there | Backs off when one starts to hiss |
| **Knowing when** | Fights until it drops | Pulls back to eat or heal, and comes back |

Brawls between NPCs use melee skill too, so a fistfight between a farmer and a guard looks like one.

## Where skill comes from

- **History.** A new NPC's skills come from the life it's had: hunters and guards start good, a homebody who never left the farm starts poor. Generated homesteads and travelers give people pasts to draw on ([travelers](travelers.md)): a widow who hunted for her family, friends who came out together after a war.
- **Age.** Children start with almost nothing and grow into it.
- **Aptitude.** Each NPC has a hidden knack for each skill, so two people with the same practice end up at different levels. Personality colors it a little: patient and serious for archery, brave and competitive for melee. Courage stays separate from skill: a brave novice charges in and a timid expert keeps its distance.
- **Family.** Children pick up some of their parents' skills while growing up, if their parents spend time with them (part of [parenting](family.md#parenting-planned)).

## Getting better

Skills grow by doing, with real practice counting the most:
- **Using it for real:** every shot, every fight, a kill, surviving something hard. Close calls teach the most.
- **Practice:** NPCs who care about a skill practice: shooting at a target, sparring with a friend. This fits into routines, the competitive and hardworking most of all.
- **Teaching:** a skilled parent, friend or mentor speeds it up, and a player can be one, by fighting alongside them.
- **Diminishing returns:** the first steps come fast, mastery takes a long time, and aptitude sets how far anyone gets.
- **Rust:** a skill left unused for a long time slips a little, never below where practice took it a long time ago.

## Making it part of who they are

- **They know it, more or less.** NPCs talk about their skills: a good shot brags or shrugs it off, a poor one is nervous about it, and anyone notices getting better ("I'm finally getting the hang of this"). Proud NPCs overrate themselves, humble ones underrate themselves.
- **Others notice.** NPCs who fight beside someone see how good they are, the player included: a friend who watched you shoot remembers it, and a skilled fighter earns some respect. This feeds [opinions](personality.md).
- **Confidence.** How good an NPC thinks it is feeds its sense of danger ([combat](personality.md)): a skilled fighter stands its ground a bit longer, a novice who knows it's a novice runs sooner. An overconfident one gets into trouble.
- **Memories and milestones.** A first kill, a narrow escape, a shot nobody else could have made: each leaves a memory and comes up in conversation.
- **The profile** shows each skill in words, not numbers (clumsy, decent, skilled, a master), as far as the player has seen it.

## More skills later

Each new skill has to change behavior, not a yield. Candidates that fit:
- **Hunting:** finding animals, approaching quietly, not scaring the herd (with [travelers](travelers.md)).
- **Wayfinding:** picking good routes on long trips, and not getting lost (travelers).
- **Caving:** lighting the way well, finding the way out, watching for drops and lava.
- **Fishing:** reacting to the bite in time, by the player's own fishing rules ([work](work.md)).

Building, farming and the like stay rules plus time, unless a real behavior turns up that skill would change.

## How it fits the code

- A skills part (an `NpcPart`) holds each skill's level, aptitude and recent practice, saved with the NPC, and records in the [register of people](architecture.md) so it outlives the entity and can be shown about NPCs who aren't loaded.
- Combat reads skill where it makes decisions (aiming in `NpcArchery`, moving and timing in `CombatBehavior`, brawls), through one small interface per skill, so a new skill plugs in without touching the others.
- Every skill comes with speech lines and memories, where NPCs would naturally talk about it.

## Open questions

- How many skills there are, in the end; few and meaningful is the aim.
- Whether the profile ever shows numbers, or only words.
- How quickly a novice becomes decent with steady practice; to be tuned in the playtest.
