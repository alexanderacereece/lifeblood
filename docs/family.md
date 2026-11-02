# Family, romance and children

Only direct links are stored: **parent**, **spouse** and **fiancé**. Everything else is derived from the graph:
- siblings share a parent;
- grandparents are parents' parents;
- aunts and uncles are parents' siblings;
- cousins are children of aunts and uncles;
- in-laws come through a spouse.

Generated homesteads start with whole families already linked.

## Romance

The way new couples form over time, so settlements change instead of staying frozen as generated. Any NPC can take part; the romantic quirk makes it far more likely and faster. Who an NPC can be attracted to: the opposite gender by default, or the same with the **gay** quirk (10%) or any with the **bisexual** quirk (10%). These show on the profile like other quirks, don't count toward the three-quirk limit, and aren't inherited. Players don't set a gender, so anyone can fall for a player. Blood relatives never date.

Stages, each needing high opinions on both sides (for the player, only the NPC's side counts; the player decides for themself):
1. **Dating** at 70 or more. One asks; the other accepts if their opinion is high enough and they're attracted, and a refusal leaves a "turned me down" memory. NPCs check about once a minute, romantics three times as often.
2. **Engaged** after 7 days of dating with both at 80 or more.
3. **Married** after 5 days engaged, still at 80 or more. A wedding: everyone nearby gets a "went to a wedding" memory and some cheer.

With the gift and chat pacing ([opinions](personality.md#opinions)), that puts dating about two weeks into a friendship and a wedding a few weeks in.

A partner whose opinion falls under 30 (dating) or 10 (engaged or married) breaks it off, leaving a "broke up with me" memory. A partner's death leaves the other widowed; they remember their late husband or wife.

Partners and family warm opinions as a standing factor: spouse +30, fiancé +25, partner +20, parent or child +20, sibling +15, grandparent or grandchild +12, aunt, uncle, niece or nephew +8, cousin +6, in-laws +5. When someone dies, every relative grieves (most for a spouse, child or parent) and remembers who killed them, wherever they are.

Family is kept in the world's register of people (`npclaude_people`, see [architecture](architecture.md)), so relatives who are far away, unloaded or dead still count.

## Children

Married couples have children, which keeps settlements alive across generations. (Minecraft Comes Alive does something similar; this design is our own.)
- A married couple at 80+ opinion, near each other, has about one child every ten in-game days, up to four, and not until the youngest is five days old. An NPC married to the player has children the same way.
- A child is born beside its NPC parent, with a name from `assets/npclaude/names.txt`, its parents' faction and home, and family links set, so siblings, cousins and so on follow from the graph.
- It starts at half size and grows to full size over 18 in-game years (1008 days in a world with 56-day years; `yearsToGrowUp` in `config/npclaude.cfg` changes it). `/npc grow` shows or sets it. Its head grows from three quarters of an adult's to full size over the same years, for child proportions.
- A child stays near a parent who's around, NPC or player, catching up when they walk off.
- Children inherit trait axes as a blend of both parents plus a random shift, and each parent quirk has a 30% chance to pass on (sometimes they get one of their own). Tastes are their own.
- Looks: skin tone, hair color and eye color from one parent or the other, often a parent's hairstyle, eyes or mouth, and no facial hair. A young man may grow a beard on coming of age.
- Children are invincible ([health modes](health.md)). They don't fight, brawl or date: they run from danger.
- Not yet: playing, and helping with small tasks as they get older.

## Ages and birthdays

Everyone has a birth date. Adults without one (new or from before ages existed) get an age on a curve that peaks around 27 and tails off toward old age (18 to 85, most in their twenties to forties); children's come from their birth. No birthday is celebrated on the day someone is born. The profile shows their age and birthday ("born Summer 5"), following the world's own calendar of seasons. On their birthday an NPC is in a good mood, says so, and any gift counts double.

Planned, not built yet:
- **Dying of old age.** Past a certain age (somewhere in the 70s), a small and rising chance each year of dying peacefully, ideally at home. Family and friends grieve as for any death, without anyone to blame. Possibly a funeral, the way weddings gather people.
- **An item that stops aging.** Given like the guardian amulet (its own locked slot, kept for good), it stops the NPC's age from advancing: no old age, and a child given one stays a child. Open questions: its recipe, whether it can be given to children at all, and whether it shows its effect on the profile.
- **The player's own birthday.** The player picks it (a dialogue or a setting); NPCs who know it wish them a happy birthday, and close friends and family give a gift.

## The player

In conversation: "Would you go out with me?" (once they're at least a friend), and once together, "About us..." to propose, set the wedding, or break up, call it off or divorce. NPCs who want to ask the player out (or propose) say they'd like to talk; the next conversation opens with the question and Yes / No / Can I think about it. "Can I ask you something?" includes "Tell me about your family", and asking what they think of a relative starts with how they're related.

The player can do everything NPCs can, with the player making their own choices on one side: dating, getting engaged, marrying, and having children with an NPC spouse. Dialogue gets options to ask someone out, propose, and break up. NPCs with high enough opinions of the player can also ask the player. The same opinion requirements apply to the NPC side; the player is free to decide.
