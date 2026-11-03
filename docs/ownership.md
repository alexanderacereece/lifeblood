# Ownership and protection (planned)

Not built yet. Keeping NPCs off the player's builds without claim commands, and the property records that crime and law will use. Numbers are starting points for playtesting.

## New worlds only

NPClaude is designed to start with a new world. Builds from before it was added have no owner on record and are not protected; there are no guesses about which blocks look man-made, which would be finicky and hard to test.

## Who placed what

Every block placed by a player, an NPC or a settlement records its owner. Natural terrain belongs to nobody.
- Breaking a block clears its record; an owner can give a block or a building away (later, with houses for sale).
- Crops and trees grown from an owned seed or sapling belong to whoever planted it, so a player's farm and tree farm are protected too.
- Stored sparsely, only for placed blocks, in the world's own save files, loaded with the area.
- Owners are players, NPCs (by their entry in the register of people) or settlements, so it works in multiplayer.

## Rules for NPCs

| Rule | Starting value |
|---|---|
| Never break a block someone else owns | always |
| Never dig or place near a player's blocks | 6 blocks around any player-placed block |
| Keep construction, tree farms, quarries and mines away from player builds | 24 blocks from any built-up area |
| Keep new settlements away from player builds | 96 blocks |
| Keep away from the player's bed and spawn point | 24 blocks |

A **built-up area** is a chunk with at least 20 player-placed blocks, so a lone torch in a cave or a dirt pillar doesn't wall off the land around it. All of these are settings.

The player's own town is the exception that matters: a settlement the player founds or invites NPCs into lets its NPCs build there, on plots the player allows (to be designed with settlements).

## Property and crime

The same records are what law needs:
- Breaking or taking something someone owns is property damage or theft, seen by witnesses, leading to guards, fines and bounties (crime is planned on the roadmap).
- It works both ways: breaking into an NPC's house or emptying their chest is a crime too.
- Hostile factions at war ignore the rules on purpose: raids, burning and looting are what war is.

## Open questions

- Pistons moving owned blocks, and falling sand or gravel: does ownership follow?
- Explosions: a creeper isn't anyone's crime, but whose fault is a player's TNT?
- An optional command to mark an area as protected, for edge cases only.
