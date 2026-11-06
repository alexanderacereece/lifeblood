# Building (partly built)

Prefabs and generated homesteads are built: saving a build, turning it, swapping its materials for the place, placing it all at once, and the world placing homesteads with people living in them. The rest is planned: NPCs choosing a site and building a real house from a prefab, honestly: block by block, from real materials, within the player's reach and support rules. Used by homesteaders, growing towns and builders for hire. Numbers are starting points for playtesting.

As far as we know, no NPC mod builds this way; village mods place blocks without the player's rules. It's the most ambitious single feature planned, and expected to take several rounds of iteration. What makes it workable is doing most of the thinking when a prefab is **authored**, so the NPC in the game only has to adapt a known plan.

## Prefabs (built)

A prefab is a saved copy of something built in-game (`building/Prefab`).

- **Every detail is kept.** In BTA a placed block is fully described by its block, its metadata (facing of stairs, doors, logs, trapdoors, beds and buttons; which half of a door or bed; paint color) and, for chests, signs, furnaces and the like, a tile entity with its own data. Prefabs save all three, so a build comes back exactly, chest contents included. Blocks are saved by name, not number, and air is saved too, so placing clears the box.
- **Turning.** A prefab remembers which way its front faces and can be placed facing any of the four ways. Each kind of block stores its facing differently, so `building/BlockTurns` has a rule per kind, read from BTA's own placement code: stairs, doors, trapdoors, fence gates, beds, chests, ladders, torches, buttons, levers, signs, rails, repeaters, pistons, furnaces, dispensers, pumpkins and logs. Statues, flags, seats and timers don't turn yet; the check warns about them.
- **Swaps, by convention instead of tags.** Like the grays in skin parts, a prefab is built in placeholders that are filled in where it's placed (`building/Swaps`), with nothing to tag by hand:
  - **Wood:** the local wood is the kind of tree growing most around the site. Logs of any tree become its logs. In BTA each log crafts planks colored by the tree (oak plain, birch white, pine brown, cherry pink, eucalyptus orange, thorn red, palm yellow), all one block underneath, so **plain planks** and everything made from them (stairs, slabs, fences, gates, doors, trapdoors, chests, signs, buttons, pressure plates) become the local wood's planks. Planks already painted a color, and mossy and scorched logs, stay as built.
  - **Stone:** stone, basalt, limestone, granite, permafrost, slate, netherrack and gloomstone each come in the same forms (natural, cobbled, mossy, carved, polished, bricks, slabs, stairs, buttons, pressure plates). Any of them becomes the same form in the local stone, the natural stone found most going down from the surface around the site. A form the local stone lacks stays as built. Sandstone and marble are chosen materials and never swap.
  - **Colors:** anything colorable (wool, painted planks, doors, fences, gates, trapdoors, chests, signs, lamps...) that's **gray takes the primary color** and **light gray the secondary color**. For a building that belongs to a faction, these are the faction's colors. Every other color stays as built.
- **Commands** (`/lb prefab`, an authoring tool): `corner 1` and `corner 2` mark the block you're looking at (or the one you stand on) as opposite corners of the build; `save <name>` saves that box while you stand in front of the build looking at it (that side is its front), or `save <name> <from> <to>` with the corners typed in. Saving says what the box holds, most common blocks first, so a box that missed the build shows at once; `place <name>`, which puts it a couple of blocks ahead of you, facing you, with its bottom layer level with the ground you stand on, in your faction's colors (or `faction <faction>`, or `colors <primary> <secondary>`); `undo` for your last placement; `check <name>`; `list`. Blocks placed this way are owned by whoever placed them.
- **Files** live in the `npc_prefabs/` folder next to `npc_parts/`, one compressed NBT file each. Prefabs bundled with the mod (`assets/lifeblood/prefabs/`) are used when the folder has none of that name. Folders are tags (`homestead/` makes a house a homestead); saving over a bundled prefab puts the file in the same folders, so it keeps its tags.

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

## Generated homesteads (built)

The first use of prefabs, before any NPC builds: homesteads placed by world generation, with people already living there. A house placed by the world needs none of the honest-building machinery, so this came first. The code is in `homestead/`; `/lb homestead generate` puts one up on the spot for testing, and `/lb homestead list` shows where the world has put them.

- **Where:** rarely. The overworld is split into cells 400 blocks across, and each cell has 8 chunks picked from the seed where one is tried (`HomesteadSites`). The first of those generated with fairly flat, dry ground gets the cell's homestead, so a cell has one at most, and cells of water or steep hills have none. Towns and cities with factions will fill the land later. The house goes in when a new chunk is decorated, after its trees and snow, inside the area being decorated, so generation never pulls in new chunks.
- **Fairly flat:** the ground under the house may rise and fall 4 blocks at most (`building/TerrainFit`). Better than Adventure's land is rarely flatter than that over a house's width; the floor goes in at the middle height, so the house is cut into the high side and built up on the low side. Under water, ice or lava never counts.
- **Which prefabs:** only those in a `homestead` folder (`prefabs/homestead/` bundled, `npc_prefabs/homestead/` in the game folder). Folders are tags, as with parts (`building/PrefabFiles`). A house with the right beds isn't automatically a homestead, and a homestead prefab can still be used in towns. `/lb prefab list` shows each prefab's tags.
- **The house:** fitted to the terrain, with swaps for the local wood and stone. Its bottom layer is the floor, sunk into the ground so the door is at ground level. Air in that bottom layer keeps the terrain instead of clearing it, so no trench is dug around the house; this holds wherever a prefab is placed, `/lb prefab place` included. Any gap under the floor's own blocks is filled down with the local ground (dirt, sand...), so the land looks leveled, not built up. Space above the floor is cleared, and logs and leaves left hanging over the roof are removed. The head of the household owns its blocks and chests, which their husband or wife and children share ([ownership](ownership.md)).
- **Goods** (`HomesteadGoods`): what the household would have after a few days or weeks of living there, and nothing they couldn't have: bread, wheat and seeds; maybe apples, cooked pork or venison, fish, eggs, leather, feathers, string, bone; logs, planks and cobblestone left from building, in the local wood and stone; sticks, torches, coal, sometimes flint or a few iron ingots; worn stone tools (a hoe, an axe, a pickaxe, maybe a shovel or sword). More food for more people. Furnaces keep a little coal. Taking it is theft from them.
- **Beds decide the household** (`building/PrefabBeds`, `HouseholdPlanner`). Two single beds side by side, facing the same way, are a double bed: a couple. A single bed alone is one person. Everyone starts on their own bed. The prefab check says how many each house sleeps, and warns about a homestead prefab with no beds, which is never used.
- **Who lives there:**
  - a double bed: a married couple (85%), each spare single bed likely holding a child; or a widow or widower (15%), sometimes with children. A second double bed may hold a couple of friends;
  - one single bed: a lone settler;
  - several single beds: friends who came out together, brothers and sisters, or a widowed parent with children.
- **History** (`HomesteadPeople`), written into the register of people as history, so no news or reactions: real ages and birthdays; a marriage from before their eldest was born, with the bride usually taking her husband's family name; children who take after both parents in personality and looks and carry their father's name; a late husband or wife in the register as dead; siblings' parents still living back home; friends who settled together start out close. Children younger than the homestead were born there.
- **How they came:** each person knows why they live there (`NpcLife.getCameFor`): a fresh start, faith, land, the quiet, or running from something; children came with their family or were born there. Players can ask "What brought you out here?", and NPCs talk it over in chats (`homestead.speech`).

Not yet: a grave for the late husband or wife; adults' homeland is the homestead's own land rather than somewhere else; homesteads don't use faction colors (grays stay gray); and a later neighbor's decoration could in rare cases put something against the house, since it goes in as soon as its own chunk is decorated.

## Towns rising and falling

- **Growth.** A settlement puts down new construction sites on its free plots as it needs homes and workshops.
- **Decline.** Abandoned buildings decay: torches go out, vines and cobwebs creep in, glass breaks, until ruins are left. A town that only grows and decays builds a real history.
- **Unloaded,** construction and decay catch up when the area loads, placing as many blocks as the time would have ([unloaded](unloaded.md)).

## Open questions

- Mirrored variants of prefabs, or rotations only.
- Whether some air above the floor layer should keep the terrain too (for builds set into a hillside).
- Where homesteaders came from: a homeland somewhere else, and a grave for a late husband or wife.
- Which wood and stone to use where nothing's found nearby (worldgen may place a house before its trees): a table by biome.
- Roads and paths between buildings: generated, or prefabs too.
- How much a builder for hire charges, once there's currency.
