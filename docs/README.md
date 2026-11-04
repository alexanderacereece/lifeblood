# Lifeblood design

An NPC framework for Better Than Adventure 8.0.1: villagers with personalities, opinions, families and their own way of talking, built so they can eventually live out their own days. Numbers throughout are starting points for playtesting, not final.

| Doc | Covers |
|---|---|
| [architecture.md](architecture.md) | How the code is organized, and where new systems plug in |
| [personality.md](personality.md) | Trait axes, quirks, mood, opinions, brawls, tastes, archetype labels |
| [family.md](family.md) | Kinship, romance, children, ages and birthdays, the player's love life |
| [health.md](health.md) | Health modes, being downed, the guardian amulet |
| [speech.md](speech.md) | The speech engine, grammar, speech styles, the word book |
| [appearance.md](appearance.md) | Parts, palettes, homelands, the art layout |
| [palette_reference.html](palette_reference.html) | Swatches of every bundled palette |

Planned, not built yet (ownership is partly built):

| Doc | Covers |
|---|---|
| [life-choices.md](life-choices.md) | NPCs choosing jobs, journeys, moves, homesteads and offices; the ambition axis |
| [work.md](work.md) | Jobs, block actions, real item chains, storage, honest mining |
| [building.md](building.md) | Prefabs, the authoring check, honest construction, towns growing and decaying |
| [ownership.md](ownership.md) | Who placed what, protecting player builds, property for crime and law |
| [unloaded.md](unloaded.md) | Keeping the world alive where nobody is watching |

The roadmap (what's built and what's next, including settlements and factions) is a separate page.

## Build order for working life

Each step is useful on its own and proves the next:
1. **Block actions** by the player's rules, and **ownership records**, so NPCs can touch the world without touching your builds.
2. **Job steps and storage:** one NPC who chops trees, replants, and stores logs in a chest proves the chain.
3. **Jobs and life choices**, with the ambition axis and the homebody and wanderer quirks.
4. **Prefabs and the authoring check**, then **construction sites**: a homesteader building a house.
5. **Life while unloaded**, alongside generated settlements.

## Settled

- Pious NPCs follow the Cube God.
- Sleeping NPCs do not count toward skipping the night.
- Brawls exist, between hotheads and between NPCs who hate each other.
- The player can date, marry and have children with NPCs, under the same rules as NPCs.
- Children exist, grow up over 18 in-game years (a setting), and are invincible.
- The guardian amulet: string, gold ingots and a block of gold. It works from anywhere in an NPC's inventory.
- NPCs carry as much as a player: 4 armor slots and 36 more. It's all theirs; gifts are kept, eaten, worn and used.
- Only people who share what an NPC owns (spouse, parents, children) can open its inventory. There is no lending gear.
- NPCs use the player's exact mechanics: health, speed, healing, ammo. No buffs.
- New systems get speech lines where NPCs would naturally talk about them, never forced.
- No code reads old saves while development worlds are disposable. Once long-term worlds start, it lives in one place and goes at content 1.0.
- The build stays ready for multiplayer where that doesn't get in the way of the main vision.
- Lifeblood is meant to start with a new world; builds from before it was added aren't protected.
- One new axis, ambition, plus the homebody and wanderer quirks, for life choices. No more than that.
- NPCs choose by inclination among real options the world offers; balance shapes the options, not the NPC.
- Building is honest: real materials, the player's reach and support rules, prefabs that keep every block detail.
- Mining stays honest; running out of ore is a story, not a problem. Unloaded areas run on ledgers measured from the real rules.

## Open questions

- The age-stopping item: its recipe, whether children can be given one, and whether the profile shows it.
- Whether symbols on banners and shields are doable, for faction colors.
- How often a typical NPC makes a big life change.
- Whether the player can talk an NPC into or out of a life choice.
- What BTA's trommel yields, and whether sifting is worth a job.
- Ownership edge cases: pistons, falling blocks, a player's TNT.
- How far the unloaded simulation reaches.
