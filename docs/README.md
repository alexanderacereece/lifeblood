# NPClaude design

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

The roadmap (what's built and what's next, including settlements and factions) is a separate page.

## Settled

- Pious NPCs follow the Cube God.
- Sleeping NPCs do not count toward skipping the night.
- Brawls exist, between hotheads and between NPCs who hate each other.
- The player can date, marry and have children with NPCs, under the same rules as NPCs.
- Children exist, grow up over 18 in-game years (a setting), and are invincible.
- The guardian amulet: string, gold ingots and a block of gold, in its own locked slot.
- NPCs use the player's exact mechanics: health, speed, healing, ammo. No buffs.
- Every new system comes with speech lines reacting to it.
- Code that reads old saves is temporary, kept in one place, and goes at content 1.0.
- The build stays ready for multiplayer where that doesn't get in the way of the main vision.

## Open questions

- The age-stopping item: its recipe, whether children can be given one, and whether the profile shows it.
- Whether symbols on banners and shields are doable, for faction colors.
- How occupations are assigned: rolled, chosen by settlements' needs, or by the player.
