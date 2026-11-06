# Travelers and adventures (planned)

Not built yet. NPCs who are out in the world instead of at home: wanderers passing through, adventurers going into caves and ruins, hunters and gatherers out for the day, and people making a long trip to somewhere. With [generated homesteads](building.md#generated-homesteads-built), this is the **first playtest**: a world with a mix of NPCs living in places and NPCs showing up on their own, so the pace of the mod can be felt. Numbers are starting points for playtesting.

## What it should feel like

Meeting someone out in the world should feel like meeting a person with somewhere to be, not a mob that spawned. They're going somewhere for a reason, doing it by the player's rules, and they remember it afterwards. You might find a hunter dressing a deer at dusk, pass a traveler on the road who tells you where they're headed, or come across an adventurer deep in a cave, low on torches.

## Where travelers come from

Nobody appears from nowhere: every traveler is a real person in the [register of people](architecture.md), with a home and a reason.
- **From homesteads and settlements.** Most outings start from a home: a hunter leaves in the morning and comes back with meat, a wanderer sets off on a journey, someone walks to visit a relative two homesteads over.
- **From far away.** Some people come from places the player hasn't found yet. They're created when they set out, with a home off in the unexplored world, a homeland and a history, so a stranger arriving at the edge of where you've been has somewhere to have come from. Later, when generated settlements exist, these become real places.
- **How often** is a dial in `config/lifeblood.cfg`, tuned in the playtest. Personality decides who goes: wanderers, the curious and the brave most, homebodies hardly ever ([life choices](life-choices.md)).

## Kinds of outing

Each is a plan with a destination, steps along the way, and a reason to come home.

| Outing | Who | What happens |
|---|---|---|
| Hunting | brave, hardworking; hunters by trade | Goes to where animals are, kills them with real weapons, brings back meat and leather. |
| Gathering | anyone with a need | Wood, stone, flowers, sugarcane, clay: by [block actions](work.md#the-pieces), with real tools, into their own inventory. |
| Adventuring | brave, curious, greedy, wanderers | Goes into caves, dungeons and ruins: lights the way, fights what's there, loots chests, may get hurt or lost, and comes back with loot and stories. |
| A journey | wanderers, the curious | A long trip to a far place and back, camping on the way. |
| Commuting | anyone with somewhere to be | Visiting family, going to trade, moving house, a job in another place. |

## Doing it honestly

The same rules as everything else ([player parity](README.md#settled)):
- **Travel** is real walking with the [navigator](architecture.md), over long distances in legs, resting at night: a camp, a bed at a friend's, or pushing on if brave and in a hurry.
- **Supplies** come from their inventory: they eat what they packed, light caves with torches they carry, and turn back when they run low or get hurt.
- **Danger is real.** Monsters can hurt or kill an adventurer (children never go). A death out there is grieved at home like any other, and someone may come looking.
- **Taking things** follows [ownership](ownership.md): loot in a ruin is nobody's; a chest in someone's house is theft.

## Coming home

The outing leaves memories: what they did, what they found, who they met. They talk about it ("found a dungeon past the river, barely made it out"), give some of what they brought to family, store the rest, and their mood and confidence shift with how it went. Meeting the player on the way is remembered too.

## Talking to them

A traveler can be asked where they're going and why, what they've seen, and later to travel together or to trade. Lines react to the kind of outing, how it's going (lost, hurt, loaded with loot) and how far from home they are. Every kind of outing gets lines where NPCs would naturally talk about it.

How well a traveler hunts, fights or finds its way is its [skill](skills.md), and outings are where most of it grows.

## What it needs first

- **[Generated homesteads](building.md#generated-homesteads-built)**, so there are homes to set out from and come back to.
- **[Block actions](work.md#the-pieces)** for gathering and lighting caves (break and place by the player's rules).
- **Long-distance travel** in the navigator: routes in legs across loaded areas, and how a trip carries on when its area unloads ([unloaded](unloaded.md): a traveler is a record moving along a route, made real again where they've got to).
- **Outings as plans:** a small framework for a destination, steps and a reason to return, reused by jobs later ([work](work.md)).

## Open questions

- How far travelers go, and how long a typical journey takes.
- Whether travelers can join the player for a while, and how that differs from following.
- How strangers from unexplored places later become real settlements when the player finds them.
