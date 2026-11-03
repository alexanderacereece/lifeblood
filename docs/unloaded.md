# Life while unloaded (planned)

Not built yet. How NPCs keep living where nobody is watching. This is the biggest new framework planned, and nearly every big plan depends on it: life choices, work, building, settlements, wars, catalysts, rulers you've never met.

## The problem

BTA doesn't tick unloaded chunks, so an NPC in a town you're not near doesn't move, work or decide anything. Without help, the world only lives within sight of the player.

## The approach: real when watched, a ledger when not

- **Loaded,** everything is real: NPCs walk, fish, build and trade by the player's rules.
- **Unloaded,** a world-level system (a [world system](architecture.md), ticking about once an in-game day) advances a simplified version: people make life choices, workplaces produce at measured rates, mines draw on their deposit, travelers move along their route, construction and decay progress.
- **On loading,** the ledger turns back into the real thing: items appear in the right chests, construction places the blocks the time would have built, travelers spawn where they've got to, tunnels catch up.

The rates come from the real rules, measured, so a town that ran unwatched ends up about where it would have if you'd watched.

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
