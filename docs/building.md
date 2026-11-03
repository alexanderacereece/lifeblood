# Building (planned)

Not built yet. NPCs choosing a site and building a real house from a prefab, honestly: block by block, from real materials, within the player's reach and support rules. Used by homesteaders, growing towns and builders for hire. Numbers are starting points for playtesting.

As far as we know, no NPC mod builds this way; village mods place blocks without the player's rules. It's the most ambitious single feature planned, and expected to take several rounds of iteration. What makes it workable is doing most of the thinking when a prefab is **authored**, so the NPC in the game only has to adapt a known plan.

## Prefabs

A prefab is a saved copy of something built in-game.

- **Every detail is kept.** In BTA a placed block is fully described by its block ID, its metadata (facing of stairs, doors, logs, trapdoors, beds and buttons; which half of a door or bed; paint color) and, for chests, signs, furnaces and the like, a tile entity with its own data. Prefabs save all three, so a build comes back exactly, chest contents included.
- **Swap tags.** Each block can be tagged so it changes per place: "log" and "planks" become the biome's wood, "local stone" the stone underground there, and two marker wool colors become the settlement's main and accent colors. Tags are suggested automatically and can be edited.
- **Authoring command.** `/npc prefab save <name>` with two corners, plus where the door and the ground level are. You build houses in-game the same way you make parts now.
- **Files** live in a `npc_prefabs/` folder next to `npc_parts/`, with bundled defaults in the jar.

## The authoring check

When a prefab is saved, the tool simulates building it on flat, empty ground and stores the result with it:
- **Build order**, from a support graph: what holds up what. Nothing can be placed in midair; sand and gravel need something under them; torches, ladders and buttons need a wall; doors and beds are placed as one.
- **Standing spots** for every block, within reach and with a clear view of the face clicked.
- **No trapping itself.** The order never seals the builder in or covers a hole it still needs.
- **Scaffolding** where overhangs and roofs need it: where to put it, and when to take it down.
- **Bill of materials**, after swaps.
- **Warnings** for anything that can't be built honestly, like "this lantern has nothing to hang on until the roof exists", so they're fixed by the author, not discovered by an NPC.

## Placing blocks

The NPC places blocks the way a player does: it stands where the game's own placement logic will give the block the right facing, looks the right way and clicks the right face. Then it checks the result against the prefab. This handles rotation for every block, modded ones included, and keeps NPCs on the player's rules. Writing the metadata directly is the fallback for odd cases.

## Building a house

1. **Choosing a site.** Scan nearby land for somewhere flat enough, dry, unowned and outside every [protection buffer](ownership.md), near a road or friends. Score how much digging and filling it needs. Searches share a budget, like pathfinding.
2. **A construction site.** Saved in the world: the prefab, position, rotation, swaps, progress per block, owner, workers and a stockpile chest. It survives saving and unloading.
3. **Gathering.** The builder brings what it has, then chops, quarries or buys the rest ([work](work.md)). A homesteader puts up a rough shelter first, for the first nights.
4. **Clearing and leveling.** Breaking with real tools, filling holes, keeping water and lava out.
5. **Building** in the planned order: foundation, walls bottom-up, attachments once walls are up, roof from scaffolding, then the inside (beds, chests, furnace).
6. **Cleanup.** Scaffolding down, leftover materials stored, and moving in.

**Disruption** (you or a creeper breaking blocks, a mob attack, nightfall, running out of materials): the builder rescans the site, compares it with the plan and carries on. It gives up only after repeated failures, and talks about it.

**Making it convincing:** arm swings and sounds, walking to the stockpile and carrying loads, stepping back to look at the work, a lunch break, quitting at dusk, and speech lines ("roof's going up tomorrow"). Coming across someone halfway through their house is part of the charm, so it should take a few in-game days.

## Towns rising and falling

- **Growth.** A settlement puts down new construction sites on its free plots as it needs homes and workshops.
- **Decline.** Abandoned buildings decay: torches go out, vines and cobwebs creep in, glass breaks, until ruins are left. A town that only grows and decays builds a real history.
- **Unloaded,** construction and decay catch up when the area loads, placing as many blocks as the time would have ([unloaded](unloaded.md)).

## Open questions

- Mirrored variants of prefabs, or rotations only.
- Roads and paths between buildings: generated, or prefabs too.
- How much a builder for hire charges, once there's currency.
