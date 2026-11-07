# Family, romance and children

Only direct links are stored: **parent**, **spouse** and **fiancé**. Everything else is derived from the graph:
- siblings share a parent;
- grandparents are parents' parents;
- aunts and uncles are parents' siblings;
- cousins are children of aunts and uncles;
- in-laws come through a spouse.

Generated homesteads start with whole families already linked.

## Family names

Everyone has a given name and a family name ("Ada Thorne"), and family names pass down the traditional way:
- A child takes its father's family name. Without an NPC father (a player parent, or two mothers or two fathers), it takes the name of the NPC it's born to. Players have no family name.
- At a wedding between a man and a woman, the bride takes her husband's family name 80% of the time, and keeps her own otherwise. Same-sex couples keep their own names, and so does an NPC marrying a player.
- A married name stays through widowhood and divorce. The name someone was born with is remembered.
- NPCs whose family isn't from this world (anyone summoned or spawned rather than born) get a family name from the `[family]` section of `assets/lifeblood/names.txt`.

People go by their given name: it's the name tag and what others call them. The full name shows on the profile and in commands (`/lb npc summon Ada Thorne` sets both), and the register of people keeps it for the dead and the far away. NPCs mention it now and then: introducing themselves, being proud of the family, saying what they were called before they married, and trying out their new name at the wedding.

## Romance

The way new couples form over time, so settlements change instead of staying frozen as generated. Any NPC can take part; the romantic quirk makes it far more likely and faster. Who an NPC can be attracted to: the opposite gender by default, or the same with the **gay** quirk (10%) or any with the **bisexual** quirk (10%). These show on the profile like other quirks, don't count toward the three-quirk limit, and aren't inherited. A child's isn't decided until age 7, old enough for a first crush; until then it has none. Players don't set a gender, so anyone can fall for a player. Blood relatives never date.

Stages, each needing high opinions on both sides (for the player, only the NPC's side counts; the player decides for themself):
1. **Dating** at 70 or more. One asks; the other accepts if their opinion is high enough and they're attracted, and a refusal leaves a "turned me down" memory. NPCs check about once a minute, and romantics are three times as likely to ask.
2. **Engaged** after a year and a half of dating (84 in-game days) with both at 80 or more.
3. **Married** after a year engaged (56 in-game days), still at 80 or more. A wedding: everyone nearby gets a "went to a wedding" memory and some cheer.

With the gift and chat pacing ([opinions](personality.md#opinions)), dating can start about two weeks into a friendship; the engagement and wedding then take as long as they would in life, two and a half years in all.

A partner whose opinion falls under 30 (dating) or 10 (engaged or married) breaks it off, leaving a "broke up with me" memory. A partner's death leaves the other widowed; they remember their late husband or wife.

Partners and family warm opinions as a standing factor: spouse +30, fiancé +25, partner +20, parent or child +20, sibling +15, grandparent or grandchild +12, aunt, uncle, niece or nephew +8, cousin +6, in-laws +5. When someone dies, every relative grieves (most for a spouse, child or parent) and remembers who killed them, wherever they are.

Family is kept in the world's register of people (`lifeblood_people`, see [architecture](architecture.md)), so relatives who are far away, unloaded or dead still count.

## Children

Married couples have children, which keeps settlements alive across generations. (Minecraft Comes Alive does something similar; this design is our own.)
- A married couple at 80+ opinion, near each other, has about one child every two in-game years, up to four, and not until the youngest is a year and a half old. An NPC married to the player has children the same way. Like everything time-based, these are real-life lengths with an in-game year (56 days) as a year.
- A child is born beside its NPC parent, with a given name from `assets/lifeblood/names.txt`, its father's family name ([family names](#family-names)), its parents' faction and home, and family links set, so siblings, cousins and so on follow from the graph.
- It starts at half size and grows to full size over 18 in-game years (1008 days in a world with 56-day years; `yearsToGrowUp` in `config/lifeblood.cfg` changes it). `/lb npc grow` shows or sets it. Its head grows from three quarters of an adult's to full size over the same years, for child proportions.
- A child under 10 stays near a parent who's around, NPC or player, catching up when they walk off. From 10 they go about on their own, and homesteaders' children help with chores ([daily life](daily-life.md#children)).
- Children inherit trait axes as a blend of both parents plus a random shift, and each parent quirk has a 30% chance to pass on (sometimes they get one of their own). Tastes are their own.
- Looks: skin tone, hair color and eye color from one parent or the other, often a parent's hairstyle, eyes or mouth, and no facial hair. A young man may grow a beard on coming of age.
- Children are invincible ([health modes](health.md)). They don't fight, brawl or date: they run from danger.
- They sound their age: babies babble, then little kids, young teens and older teens each talk their own way, to their family most of all ([speech](speech.md#sounding-their-age)).
- Homesteaders' children under 10 play within a few blocks of home while the grown-ups do chores; from 10 they help with the lighter chores ([daily life](daily-life.md#children)). Children never go on trips.

### Parenting (planned)

Who a child becomes should come from both parents, NPC or player, by nature and by upbringing:
- **Nature:** at birth, axes, quirks and looks blend both parents, as now. With [player creation](#player-creation-planned), a player parent passes on their own chosen traits and looks the same way an NPC does.
- **Upbringing:** while the child grows, the parents around it keep nudging who it becomes. Time spent with each parent, how they treat it (kind or harsh, present or absent), what they do together, and big events (a parent's death, a divorce, a move) shift its axes and can give or take quirks before it comes of age. A player parent shapes their child through the same things: talking, gifts, taking them along, being there or not.
- Every part of it works the same whichever parent is the player, so a child raised by an NPC and a player is shaped by both.

## Ages and birthdays

Everyone has a birth date. Adults without one (new or from before ages existed) get an age on a curve that peaks around 27 and tails off toward old age (18 to 85, most in their twenties to forties); children's come from their birth. No birthday is celebrated on the day someone is born. The profile shows their age and birthday ("born Summer 5"), following the world's own calendar of seasons. On their birthday an NPC is in a good mood, says so, and any gift counts double. Hair grays with age, starting around 40 ([appearance](appearance.md#graying)).

Planned, not built yet:
- **Dying of old age.** Past a certain age (somewhere in the 70s), a small and rising chance each year of dying peacefully, ideally at home. Family and friends grieve as for any death, without anyone to blame. Possibly a funeral, the way weddings gather people.
- **An item that stops aging.** Given like the guardian amulet (its own locked slot, kept for good), it stops the NPC's age from advancing: no old age, and a child given one stays a child. Open questions: its recipe, whether it can be given to children at all, and whether it shows its effect on the profile.
- **The player's own birthday.** The player picks it (a dialogue or a setting); NPCs who know it wish them a happy birthday, and close friends and family give a gift.

## The player

In conversation: "Would you go out with me?" (once they're at least a friend), and once together, "About us..." to propose, set the wedding, or break up, call it off or divorce. NPCs who want to ask the player out (or propose) say they'd like to talk; the next conversation opens with the question and Yes / No / Can I think about it. "Can I ask you something?" includes "Tell me about your family", and asking what they think of a relative starts with how they're related.

### Player creation (planned)

When starting out, the player makes themselves the way an NPC is made, so everything that works between NPCs works with them too:
- **Personality:** they choose their own trait axes, quirks and tastes (or roll them). NPCs then judge them by compatibility exactly as they judge each other: a crude, hotheaded player clashes with proper, zen NPCs; a pious one gets on with the devout.
- **Looks:** they can replace their Minecraft skin with one built from skin parts and palettes, like an NPC's, so their "genetics" work like an NPC's and their children inherit their skin tone, hair, eyes and features.
- It's optional: a player who skips it keeps their own skin and a neutral personality.

The player can do everything NPCs can, with the player making their own choices on one side: dating, getting engaged, marrying, and having children with an NPC spouse. Dialogue gets options to ask someone out, propose, and break up. NPCs with high enough opinions of the player can also ask the player. The same opinion requirements apply to the NPC side; the player is free to decide.
