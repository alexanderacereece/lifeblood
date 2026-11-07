# Ownership and protection

Keeping NPCs off the player's builds without claim commands, and the property records that crime and law will use. Numbers are starting points for playtesting.

**Built:** block ownership records, item ownership by location, shared ownership in families, kept gifts, `/lb owner`, and NPCs' own work keeping to it (below). **Planned:** the protection buffers around player builds, and theft.

## New worlds only

Lifeblood is designed to start with a new world. Builds from before it was added have no owner on record and are not protected; there are no guesses about which blocks look man-made, which would be finicky and hard to test.

## Who placed what

Every block placed by a player, an NPC or a settlement records its owner. Natural terrain belongs to nobody.
- A block someone places is theirs. While a player uses an item on a block, every block that appears because of it is theirs: both halves of a door or bed, farmland they till, a whole tree grown with bone meal.
- Anything that clears a block (breaking it, fire, an explosion, leaves decaying) clears its record. Changes in place keep it: a furnace lighting up, dirt turning to grass, a door opening, paint on a block. Changing someone else's block never makes it yours.
- A sapling grown naturally keeps its owner only where the sapling stood; the rest of the tree is nobody's. (Possibly later: the whole tree.)
- Stored with each chunk, only for placed blocks, as compact number arrays, and loaded with the chunk.
- Owners are players, NPCs or (later) settlements, all by id, so it works in multiplayer.
- `/lb owner <x> <y> <z>` tells who owns a block.

## Items

Items have no ownership tags, so nothing interferes with stacking or other mods. An item belongs to whoever holds it:
- everything in an NPC's inventory is that NPC's;
- everything in a container belongs to whoever owns the container's block;
- the player's inventory, and the chests they placed, are theirs.

NPCs carry as much as a player: 4 armor slots, the hand and 35 more. Gifts go into it and are theirs to eat, wear and use; they put on gifted armor if it's better than what they wear. If they have no room, they say so and don't take it. Only the first gift each day moves their opinion; later ones are kept and used, with a plain thanks.

## Sharing

Spouses, parents and children share what each of them owns, players and NPCs alike. So a player married into a family can open their spouse's inventory and their family's chests, and an NPC family shares its house. Everyone else, however friendly, can only give. There's no lending gear: equipping a companion means giving it to them, and it's theirs. (A divorce, breakup or death changes who shares, by itself.)

## Rules for NPCs

**Built:** blocks NPCs place, and farmland they till, are theirs (`BlockOwners.placingAs`); a generated homestead's blocks belong to the head of the household. Chores only take from nature or the household: trees and gravel nobody placed, the household's own field, chests and furnaces. Adventurers empty only chests nobody owns (dungeons). Not yet: a traveler digging in for the night or cutting a stair into a cave doesn't check who owns the ground.

**Planned:**

| Rule | Starting value |
|---|---|
| Never break a block someone else owns | always |
| Never dig or place near a player's blocks | 6 blocks around any player-placed block |
| Keep construction, tree farms, quarries and mines away from player builds | 24 blocks from any built-up area |
| Keep new settlements away from player builds | 96 blocks |
| Keep away from the player's bed and spawn point | 24 blocks |

A **built-up area** is a chunk with at least 20 player-placed blocks, so a lone torch in a cave or a dirt pillar doesn't wall off the land around it. All of these are settings.

The player's own town is the exception that matters: a settlement the player founds or invites NPCs into lets its NPCs build there, on plots the player allows (to be designed with settlements).

## Property and crime (planned)

The same records are what law needs:
- Breaking or taking something someone owns is property damage or theft, seen by witnesses, leading to guards, fines and bounties (crime is planned on the roadmap).
- It works both ways: breaking into an NPC's house or emptying their chest is a crime too.
- Looting what a dead NPC dropped is theft from their family.
- Hostile factions at war ignore the rules on purpose: raids, burning and looting are what war is.

## Open questions

- Pistons moving owned blocks, and falling sand or gravel: does ownership follow? (Today it doesn't.)
- Explosions: a creeper isn't anyone's crime, but whose fault is a player's TNT?
- Whether grown-up children who've moved out still share with their parents.
- An optional command to mark an area as protected, for edge cases only.
