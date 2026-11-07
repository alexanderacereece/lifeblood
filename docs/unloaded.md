# Life while unloaded (partly built)

How NPCs keep living where nobody is watching. **Built:** travelers ([travelers](travelers.md#life-while-out-of-sight): a record moving along their road) and homesteads (below). The rest (settlements, construction, mines) comes with them. This is the biggest new framework planned, and nearly every big plan depends on it: life choices, work, building, settlements, wars, catalysts, rulers you've never met.

## The problem

BTA doesn't tick unloaded chunks, so an NPC in a town you're not near doesn't move, work or decide anything. Without help, the world only lives within sight of the player.

## The approach: real when watched, a ledger when not

- **Loaded,** everything is real: NPCs walk, fish, build and trade by the player's rules.
- **Unloaded,** a world-level system (a [world system](architecture.md), ticking about once an in-game day) advances a simplified version: people make life choices, workplaces produce at measured rates, mines draw on their deposit, travelers move along their route, construction and decay progress.
- **On loading,** the ledger turns back into the real thing: items appear in the right chests, construction places the blocks the time would have built, travelers spawn where they've got to, tunnels catch up.

The rates come from the real rules, measured, so a town that ran unwatched ends up about where it would have if you'd watched.

## Homesteads (built)

A homestead nobody's near doesn't tick. When its house is loaded again after more than two in-game hours unwatched, its household catches up on the time at once (`homestead/life/HouseholdCatchUp`), up to four weeks:
- **The field:** wheat on the household's farmland is grown on, or taken as harvested and sown again for each two days passed; a harvest gives each plot's wheat and a spare seed.
- **Chores, by the tools they keep:** about 6 logs a day with an axe (of the trees around the house, up to 64 kept), 1.5 fish a day with a rod, 0.8 raw meat a day with a sword or bow if anyone hunts.
- **The furnace:** wheat beyond a little seed corn made into dough and baked into bread (with a crafting table), raw meat and fish cooked, burning coal (8 items each) or logs (1.5 each), as far as the fuel goes.
- **Eating:** about a third of a food item per person per day (they eat to heal).
- **Trips** (`travel/TripsWhileAway`, on a `TimeAwayEvent`): each grown-up may have set out as often as they would in sight; each trip is worked out as a trip out of sight is, its finds put in the chests, its wounds carried, and now and then someone didn't come back (a real death: the family grieves, the news travels). A recent trip is remembered.

Everything goes into the chests as far as they hold, the rest by the door. Time skipped by sleeping counts too. Not yet: births and courtship while unloaded, and new trips starting from an unloaded house (they start once it's loaded).

## What it needs

1. **The register keeps whole NPCs.** Today the [register of people](architecture.md) keeps a summary: name, family, home, last place seen. To move an NPC or spawn it somewhere new, it needs the NPC's full save data, captured when its chunk unloads (BTA has no per-entity unload callback, so this hooks chunk saving).
2. **One home for each NPC's state.** An NPC is either a live entity or a record, never both, so nobody is duplicated or lost when a chunk loads twice or a traveler arrives.
3. **Ledgers** per workplace, mine and construction site: what was made, used and built since the area was last loaded.
4. **Catch-up rules** that respect what changed: construction only places blocks into air or unowned natural ground and replans otherwise; items go into chests up to what fits.
5. **Pacing.** Catch-up is spread over several ticks when a big area loads, so loading a town doesn't stutter.

## Open questions

- How far the simulation reaches: every settlement in the world, or only within some distance of a player.
- Whether combat (raids, wars) is ever resolved while unloaded, and how.
- Births and deaths while unloaded: allowed, with the register as the record.
