# Building (partly built)

Prefabs are built: saving a build, turning it, swapping its materials for the place, and placing it all at once. The rest is planned: generated homesteads, and NPCs choosing a site and building a real house from a prefab, honestly: block by block, from real materials, within the player's reach and support rules. Used by homesteaders, growing towns and builders for hire. Numbers are starting points for playtesting.

As far as we know, no NPC mod builds this way; village mods place blocks without the player's rules. It's the most ambitious single feature planned, and expected to take several rounds of iteration. What makes it workable is doing most of the thinking when a prefab is **authored**, so the NPC in the game only has to adapt a known plan.

## Prefabs (built)

A prefab is a saved copy of something built in-game (`building/Prefab`).

- **Every detail is kept.** In BTA a placed block is fully described by its block, its metadata (facing of stairs, doors, logs, trapdoors, beds and buttons; which half of a door or bed; paint color) and, for chests, signs, furnaces and the like, a tile entity with its own data. Prefabs save all three, so a build comes back exactly, chest contents included. Blocks are saved by name, not number, and air is saved too, so placing clears the box.
- **Turning.** A prefab remembers which way its front faces and can be placed facing any of the four ways. Each kind of block stores its facing differently, so `building/BlockTurns` has a rule per kind, read from BTA's own placement code: stairs, doors, trapdoors, fence gates, beds, chests, ladders, torches, buttons, levers, signs, rails, repeaters, pistons, furnaces, dispensers, pumpkins and logs. Statues, flags, seats and timers don't turn yet; the check warns about them.
- **Swaps, by convention instead of tags.** Like the grays in skin parts, a prefab is built in placeholders that are filled in where it's placed (`building/Swaps`), with nothing to tag by hand:
  - **Wood:** the local wood is the kind of tree growing most around the site. Logs of any tree become its logs. In BTA each log crafts planks colored by the tree (oak plain, birch white, pine brown, cherry pink, eucalyptus orange, thorn red, palm yellow), all one block underneath, so **plain planks** and everything made from them (stairs, slabs, fences, gates, doors, trapdoors, chests, signs, buttons, pressure plates) become the local wood's planks. Planks already painted a color, and mossy and scorched logs, stay as built.
  - **Stone:** stone, basalt, limestone, granite, permafrost, slate, netherrack and gloomstone each come in the same forms (natural, cobbled, mossy, carved, polished, bricks, slabs, stairs, buttons, pressure plates). Any of them becomes the same form in the local stone, the natural stone found most going down from the surface around the site. A form the local stone lacks stays as built. Sandstone and marble are chosen materials and never swap.
  - **Colors:** anything colorable (wool, painted planks, doors, fences, gates, trapdoors, chests, signs, lamps...) that's **gray takes the primary color** and **light gray the secondary color**. For a building that belongs to a faction, these are the faction's colors. Every other color stays as built.
- **Commands** (`/npc prefab`): `save <name> <from> <to>` while standing in front of the build looking at it (that side is its front); `place <name>`, which puts it a couple of blocks ahead of you, facing you, with its bottom layer level with the ground you stand on, in your faction's colors (or `faction <faction>`, or `colors <primary> <secondary>`); `undo` for your last placement; `check <name>`; `list`. Blocks placed this way are owned by whoever placed them.
- **Files** live in the `npc_prefabs/` folder next to `npc_parts/`, one compressed NBT file each. Prefabs bundled with the mod (`assets/lifeblood/prefabs/`) are used when the folder has none of that name.

## The authoring check

Built so far (`building/PrefabCheck`, shown when saving and by `check`): doors, beds and double chests cut in half by the box, blocks that won't turn, and a count of what will swap.

Planned with NPC building: when a prefab is saved, the tool simulates building it on flat, empty ground and stores the result with it:
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

## Generated homesteads (next)

The first use of prefabs, before any NPC builds: homesteads placed by world generation, with people already living there. A house placed by the world needs none of the honest-building machinery, so this comes first.

- **Where:** occasionally, on fairly flat ground in the overworld, during the same decoration step that places dungeons. Each house stays inside the area being decorated, so generation never pulls in new chunks.
- **The house:** a prefab fitted to the terrain (foundation filled, space above cleared), with swaps for the local wood, stone and colors. The household owns its blocks and chests ([ownership](ownership.md)), and the chests hold household goods.
- **Who lives there.** Most are families, but not all:
  - a family: a couple, often with children;
  - a lone settler;
  - someone who *had* someone: a widow or widower whose late spouse is in the register of people, sometimes with children, sometimes a grave nearby;
  - friends or like-minded people who came out together, each with their own reasons (a shared faith, running from something, a fresh start).
- **History:** real ages and birthdays, marriages and family links in the register, memories and opinions of each other, a homeland, and the homestead as home. They talk about how they came to live there.

## Towns rising and falling

- **Growth.** A settlement puts down new construction sites on its free plots as it needs homes and workshops.
- **Decline.** Abandoned buildings decay: torches go out, vines and cobwebs creep in, glass breaks, until ruins are left. A town that only grows and decays builds a real history.
- **Unloaded,** construction and decay catch up when the area loads, placing as many blocks as the time would have ([unloaded](unloaded.md)).

## Open questions

- Mirrored variants of prefabs, or rotations only.
- Whether some air in a prefab should keep the terrain instead of clearing it (for builds set into a hillside).
- Which wood and stone to use where nothing's found nearby (worldgen may place a house before its trees): a table by biome.
- Roads and paths between buildings: generated, or prefabs too.
- How much a builder for hire charges, once there's currency.
