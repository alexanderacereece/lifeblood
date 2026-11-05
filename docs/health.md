# Health modes and the guardian amulet

Three modes:

| Mode | Takes damage | Can die | Use |
|---|---|---|---|
| Mortal | yes | yes | The default |
| Invincible | yes | no | Important characters, like Skyrim's essential NPCs |
| Invulnerable | no | no | Debug only |

An invincible NPC fights, gets hurt and flees like anyone else. Damage that would kill them instead leaves them at 1 health, knocked down for several seconds, after which they get up and recover. Enemies lose interest in a downed NPC.

## Making someone invincible

The player chooses who they want to keep safe: a spouse, children, friends, companions, allies. This is done with a **guardian amulet**, a craftable item that makes any NPC invincible while it's in their inventory.
- **Gifted like any item.** You can give it to any NPC you could normally give gifts to, and they respond to it with their own lines (touched, flustered, suspicious of the gesture, and so on).
- **Theirs to keep.** Once given, they keep it no matter what happens between you later. They can't die while carrying it, so it never drops.
- **Gifts are theirs.** Every gift goes into the NPC's inventory and belongs to them ([ownership](ownership.md)); only family who share what they own can open it, so nothing given can be taken back.
- **A real cost.** It takes a block of gold and more gold besides, so you protect the people who matter, not everyone.
- **Anywhere in their inventory.** The amulet works from any slot. An NPC who already has one turns down a second. It isn't shown on the model: modular appearance ([appearance](appearance.md)) gives enough visual variety.
- **Recipe:** string on top, a gold block in the middle, gold ingots around it.

Children are the exception: they're invincible by default, with no amulet needed.

Brawls use the same rule for everyone involved: nobody can be reduced below 1 health by a brawl, and dropping low makes them yield.
