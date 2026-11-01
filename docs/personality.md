# NPC personality, opinions and speech: design

Status: design, not yet implemented. Numbers are starting points for playtesting, not final.

An NPC's character is built from layers, each simple on its own:

| Layer | What it is | Changes over time? |
|---|---|---|
| Trait axes | 8 continuous scales, the core of who they are | No |
| Quirks | 0 to 3 discrete traits that add topics and opinions | No |
| Mood | A temporary shift from recent events | Yes, constantly |
| Memories | Things that happened, each with an opinion effect | Yes, they fade |
| Family | Parent, spouse and fiancé links; other kin derived | Rarely |
| Food preferences | A like value for every food | No |
| Speech style | Surface voice: greetings, filler, tics, catchphrase | No |
| Word book | Words the player taught, spreading between NPCs | Yes |

These feed two systems: **opinions** (how NPCs regard each other and players) and **speech** (what they say and how).

---

## 1. Trait axes

Each axis runs from -1.0 to +1.0. By convention the conventionally "positive" pole is +, but neither side is better: an introverted or serious NPC is not a worse person, and the writing treats both sides with equal affection.

| Axis | - pole | + pole |
|---|---|---|
| Outlook | Pessimist | Optimist |
| Manners | Rude | Polite |
| Speech | Crude | Proper |
| Courage | Timid | Brave |
| Sociability | Introvert | Extrovert |
| Humor | Serious | Lighthearted |
| Mercy | Vengeful | Forgiving |
| Work ethic | Lazy | Hardworking |

### Intensity tiers

Lines are written against tiers, never exact values, so a moderate NPC never pulls from both extremes:

| Tier | Range | Lines available |
|---|---|---|
| Neutral | always | Untagged lines. Every event must have these. |
| Lean | 0.30 to 0.65 toward a pole | Neutral plus "lean" lines of that pole |
| Strong | 0.65 or more toward a pole | Neutral, lean and "strong" lines of that pole |

Tiers use the **effective** value (base plus mood, see section 3).

### Generation

Each axis is rolled from a bell curve centered on 0 (standard deviation about 0.4, clamped to ±1). About half of all values land in the neutral band, a third lean, and roughly one in six is strong. A typical NPC has one or two strong axes, which is what makes them recognizable.

### What each axis does

**Outlook (pessimist ↔ optimist)**
- Behavior: how strongly bad and good events move their mood. Pessimists feel bad events more; optimists bounce back faster.
- Voice: how they describe events, the weather, the harvest, the future.
- Opinions: mild friction between strong pessimists and strong optimists, both ways.

**Manners (rude ↔ polite)**
- Behavior: whether they greet, thank, apologize, or ignore and insult.
- Voice: how they address people; insults versus courtesies.
- Opinions: polite people get a small bonus from almost everyone. Rude people get a small penalty from almost everyone, smaller from other rude people. Rude people are extra irritated by polite and generous ones.

**Speech (crude ↔ proper)**
- Behavior: none to speak of. This axis is purely register.
- Voice: slang, swearing and dropped letters versus full sentences and refined words. Sets the formality of the speech style.
- Opinions: only the extremes clash. A strongly crude and a strongly proper NPC grate on each other, both ways. Everything else is neutral.

**Courage (timid ↔ brave)**
- Behavior: fight or flight, as already built. Replaces the current bravery value (0 to 1): courage = bravery × 2 - 1.
- Voice: how they talk about danger, enemies and their own fights.
- Opinions: none directly. Memories cover it ("fled and left me", "stood by me").

**Sociability (introvert ↔ extrovert)**
- Behavior: whether they seek company. Extroverts gather in the evenings, start conversations and join groups; introverts keep to their homes and work, and get tired of long conversations.
- Voice: whether they start talk and how they end it.
- Opinions: a small similarity bonus.

**Humor (serious ↔ lighthearted)**
- Behavior: whether they joke, tease, and take bad news in stride.
- Voice: jokes and teasing versus earnest, direct talk.
- Opinions: a small similarity bonus.

**Mercy (vengeful ↔ forgiving)**
- Behavior: how long bad memories last. Duration multiplier on negative memories, from ×2.0 (vengeful) to ×0.5 (forgiving).
- Voice: whether they bring up old wrongs or let them go.
- Opinions: only through memory duration.

**Work ethic (lazy ↔ hardworking)**
- Behavior: how long they work each day, how many breaks they take, how quickly they get to tasks.
- Voice: complaining about work versus talking about it with pride.
- Opinions: hardworking NPCs dislike lazy ones, scaled by both values. Lazy NPCs don't care either way.

---

## 2. Quirks

Discrete traits. Most come in opposing pairs, where most NPCs sit in the unmarked middle.

### How many

| Quirks | Chance |
|---|---|
| 0 | 5% |
| 1 | 55% |
| 2 | 32% |
| 3 | 8% |
| 4 | never |

### Pairs

**Chatterbox / Taciturn**
- Chatterbox: barks and comments about twice as often, longer lines, starts conversations, rambles. Gets along with nosy people.
- Taciturn: barks about a third as often, short answers. Mildly annoyed by chatterboxes and nosy people.

**Hothead / Zen**
- Hothead: irritation escalates fast, from snapping to insults to a fight. A lower threshold before turning hostile. Friction with other hotheads, both ways. Likes zen people, who calm them down.
- Zen: hard to provoke, a higher threshold before turning hostile, a calming voice. Bad events move their mood less.

**Gullible / Paranoid**
- Gullible: believes gossip at full strength, falls for lies, warm toward strangers at first.
- Paranoid: starts a little cooler toward strangers, discounts gossip, wary of nosy people and liars. This is a small shift only, never active dislike from paranoia alone.

**Greedy / Generous**
- Greedy: wants payment and haggles, talks about money and goods, hoards. Thinks generous people are fools.
- Generous: gives gifts and shares food, likes giving. Likes everyone a little more. Rude people are irritated by them.

**Honest / Liar**
- Honest: always tells the truth, gossip they spread is accurate. Dislikes liars once they find out.
- Liar: sometimes gives false information, exaggerates, invents gossip. Being caught creates a memory for whoever caught them.

**Nosy / Detached**
- Nosy: asks about your business, spreads gossip, comments on others. Gets along with chatterboxes and gossipers; annoys taciturn, detached and paranoid people.
- Detached: uninterested in others' lives, doesn't gossip, gossip reaches them weakly. Memories about other people affect them less.

**Proud / Humble**
- Proud: boasts, wants credit, hurt deeply by insults and defeats (stronger negative memories about themselves). Friction with other proud people.
- Humble: downplays themselves, deflects praise. Liked a little by most people.

**Early bird / Night owl**
- Early bird: wakes and starts work early, goes to bed early, grumpy if kept up.
- Night owl: up late, sleeps in, more talkative at night.

### Singles

- **Romantic:** falls for people faster and more often, courts more actively, comments on couples, hit harder by rejection and heartbreak. Anyone can take part in romance (section 5); this quirk makes it much more likely.
- **Loyal:** much slower to sour on friends, family and faction. Defends them in fights even when timid. Being betrayed hits them extra hard.
- **Glutton:** talks about food constantly, eats more often, food preferences matter twice as much to them (section 6).
- **Pious:** devout follower of the Cube God, a lighthearted in-world faith ("Thank the Cube God!", "Cube God help us"). Swears by it, keeps a routine. Dislikes crude people and liars.
- **Curious:** asks questions, wanders to look at things, interested in your travels and items. Gets along with chatterboxes.
- **Jealous:** resents friends spending time with others, envies others' wealth and partners. Opinion penalties toward rivals for the same person.
- **Sentimental:** good memories last longer (×1.5), remembers anniversaries and old favors, brings up the past.
- **Competitive:** compares themselves to others in work and fights. Friction with other competitive people, but respects whoever beats them.
- **Animal lover:** upset when someone kills an animal in front of them, and thinks less of whoever did it. Likes forests and meadows, dislikes leather, meat and other animal products.

### Blacklist

Generation never combines:
- the two sides of any pair (Chatterbox and Taciturn, Greedy and Generous, and so on).

No other combination is known to be impossible yet. True impossibilities get added here as playtesting finds them.

Everything else is left to chance until playtesting says otherwise.

---

## 3. Mood

A single value from -100 to +100 that reflects recent events, like RimWorld's mood.

- Memories can carry a **mood effect** separate from their opinion effect. "My brother died" is a big mood hit for weeks; "ate a good meal" is a small boost for a few hours.
- Outlook scales mood swings: pessimists feel bad events more and good ones less, optimists the reverse. Zen dampens everything.
- Mood shifts the **effective** value of some axes, which changes which line tiers are available:
  - Outlook: shifted by up to ±0.6 at extreme mood. A cheerful NPC in mourning sounds gloomy, then recovers.
  - Manners: low mood pulls it down by up to 0.3. Bad days make people short with others.
  - Work ethic: low mood pulls it down by up to 0.3.
- The current mood is a dialogue fact too, so lines can react directly ("Sorry, I'm not myself today").

---

## 4. Opinions

Every NPC has an opinion of other NPCs and players, from -100 to +100. It is the sum of **memories** and **standing factors**, and it is one-directional: A's opinion of B is separate from B's opinion of A.

### Memories

Each memory type has:
- an opinion effect toward the person involved, and an optional mood effect;
- a duration in in-game days, after which it fades out linearly over its last quarter;
- a stack limit, where each repeat counts for half of the previous one.

Mercy multiplies the duration of negative memories (×0.5 to ×2.0). Sentimental multiplies positive ones by 1.5. Proud doubles negative memories about their own pride (insults, defeats). Loyal halves negative memories about friends, family and faction.

Starting catalog:

| Memory | Opinion | Mood | Duration | Stacks |
|---|---|---|---|---|
| Saved my life | +30 | +10 | 60 days | 3 |
| Fought beside me | +8 | | 10 days | 5 |
| Gave me a gift | +5 | +3 | 5 days | 5 |
| Gave me food I like | +4 to +10, by preference | +2 to +5 | 8 days | 3 |
| Gave me food I hate | -3 | -2 | 3 days | 3 |
| Beat me in a brawl | -5 (+3 if competitive) | -5 | 10 days | 3 |
| Had a nice chat | +3 | +2 | 2 days | 5 |
| Taught me a word | +2 | | 3 days | 5 |
| Insulted me | -8 | -3 | 4 days | 5 |
| Hit me | -12 | -5 | 3 days | 5 |
| Fled and left me | -15 | | 15 days | 3 |
| Lied to me (found out) | -20 | | 20 days | 3 |
| Stole from me | -25 | -5 | 30 days | 3 |
| Attacked my home | -30 | -10 | 40 days | 3 |
| Killed a friend | -40 | -15 | 80 days | 3 |
| Killed family | -70 | -30 | 200 days | 3 |
| Killed my spouse or child | -100 | -40 | 400 days | 3 |

Mood-only memories (no opinion effect): ate food I like or hate (scaled by preference), slept in a bed, slept outside, got rained on, saw a death, family member died, got married, won a fight, lost a fight.

**Detecting "saved my life"** needs careful balancing. Starting rule: the NPC was in combat below 30% health, someone else killed or drove off the attacker (or healed them), and the NPC survived the next 10 seconds. Only whoever did the most damage to the attackers gets the memory. To be tuned in playtesting so ordinary teamwork doesn't count.

### Pacing

Gifts are limited to one per NPC per in-game day, and chats to three per real-world minute (the guardian amulet doesn't count as a gift here). Both count for more because of it, and repeats add up steadily instead of halving:
- a gift is worth up to about +11 depending on how much they like it, lasting two weeks, up to 14 at once;
- a chat is worth +2, lasting three days, up to 10 at once.

Checkpoints: a daily liked gift plus some chatting makes most NPCs friends (50) in about three days, and close friends (80+) in about two weeks, which is where romance should become possible. `/npc limits off` lifts both limits for playtesting.

### Standing factors

Computed live, never stored:

| Factor | Effect |
|---|---|
| Same faction | +10 |
| Factions allied | +5 |
| Factions hostile | -15 |
| Spouse | +40 |
| Fiancé | +35 |
| Parent or child | +30 |
| Sibling | +20 |
| Grandparent, grandchild, aunt, uncle, niece, nephew, cousin | +10 |
| Personality compatibility | -15 to +15, from the table below |

### Personality compatibility

How A's traits make them regard B. One-directional unless marked "both ways".

| A has | B has | A's opinion of B |
|---|---|---|
| Anyone | Polite (strong) | +5 |
| Anyone | Rude (strong) | -5 (only -2 if A is rude too) |
| Rude (lean or strong) | Polite or Generous | extra -4 |
| Hardworking | Lazy | up to -10, scaled by both values |
| Crude (strong) | Proper (strong) | -5, both ways |
| Pessimist (strong) | Optimist (strong) | -3, both ways |
| Similar Sociability | | up to +4 |
| Similar Humor | | up to +4 |
| Pious | Crude (strong) or Liar | -6 |
| Greedy | Generous | -4 |
| Generous | Anyone | +3 |
| Nosy | Chatterbox or Nosy | +4 |
| Taciturn, Detached or Paranoid | Nosy | -4 |
| Taciturn | Chatterbox | -3 |
| Hothead | Hothead | -6, both ways |
| Hothead | Zen | +4 |
| Paranoid | Anyone | -3 starting cooler, never more |
| Gullible | Anyone | +3 starting warmer |
| Paranoid | Liar (known) | -6 |
| Honest | Liar (known) | -8 |
| Anyone | Humble | +2 |
| Proud | Proud | -4, both ways |
| Competitive | Competitive | -3, both ways |
| Curious | Chatterbox | +3 |

"Known" means the liar has been caught at least once by A or by someone who told A.

The total is clamped to ±15, so personality only sets a starting temperature. History decides the rest.

### What opinions do

Labels, used in dialogue and the inspect screen:

| Opinion | Label |
|---|---|
| -60 or less | Enemy |
| -20 to -60 | Disliked |
| -20 to +20 | Neutral |
| +20 to +50 | Friendly |
| +50 to +80 | Friend |
| +80 or more | Close friend |

Combat: opinion overrides the faction only at the extremes.
- At +60 or more, the NPC treats the person as an ally (defends them, never attacks).
- At -60 or less, the NPC treats them as hostile. Hotheads turn hostile at -45, zen NPCs at -80.
- In between, the faction decides, as it does now.

The existing `/npc relation` command stays as a debug override, implemented as a strong permanent memory.

### Brawls

Non-lethal fistfights between people who aren't hostile enough to kill each other: usually members of the same faction, or neutral factions. A brawl can start when:
- a hothead's irritation boils over (an insult, being hit, a lie found out);
- two NPCs hate each other (both at -40 or less) and run into each other, even if neither is a hothead. Rarer, and more likely the lower their opinions.

Rules:
- Fists only. Weapons are put away.
- Nobody can die (see section 7), and a brawler yields well before that, so an accident doesn't leave anyone at death's door. The fight ends when one brawler drops to their yield point:
  - 10 health normally (half of the maximum 20);
  - 15 for timid NPCs;
  - 5 for proud or competitive NPCs.
  If an NPC is both timid and proud, the two cancel out to 10. As a final safeguard, brawl hits can never take anyone below 1 health.
- Bystanders react: friends of a brawler may join or break it up, extroverts gather to watch, timid NPCs keep away.
- Afterwards, the winner and loser get memories ("beat me in a brawl", "won a fight", "lost a fight"), and the hate often cools a little. Competitive NPCs gain respect for whoever beat them.
- The player can be challenged to brawls too, and can start one by punching an NPC who isn't hostile. Someone who likes you gets one warning ("Hey! Watch it!") before a second punch starts the brawl.
- Reaching for a weapon (or a bow) mid-brawl turns it into a real fight. Walking more than 16 blocks away forfeits it. Hitting someone in the 30 seconds after a brawl with them ended is a cheap shot, and starts a real fight too.
- Whoever is challenged remembers who picked the fight. Afterwards both sides usually "clear the air", which mostly outweighs the brawl's bad memories.
- Not yet: bystanders joining in or breaking it up (for now they just comment).

### Gossip (later)

When two friendly NPCs talk, they may share a memory about a third person, at reduced strength (about 30%, full for gullible listeners, weaker for paranoid and detached ones). Liars may share invented ones. This is how a reputation spreads through a town without reaching strangers 1000 blocks away.

### Storage

Memories are stored per NPC, only for people they actually have memories about. Standing factors are computed when needed, so 30 NPCs don't create 900 stored relations.

---

## 5. Family

Only direct links are stored: **parent**, **spouse** and **fiancé**. Everything else is derived from the graph:
- siblings share a parent;
- grandparents are parents' parents;
- aunts and uncles are parents' siblings;
- cousins are children of aunts and uncles;
- in-laws come through a spouse.

Generated homesteads start with whole families already linked.

### Romance

The way new couples form over time, so settlements change instead of staying frozen as generated. Any NPC can take part; the romantic quirk makes it far more likely and faster. Who an NPC can be attracted to is rolled per NPC.

Stages, each needing high opinions on both sides:
1. **Dating.** Two unattached adults with high opinions of each other (both Friend, +50 or more) may start dating. One asks; the other accepts if their opinion is high enough, and a refusal leaves a heartbreak memory. Romantic NPCs ask sooner and more often. Couples spend evenings together and give each other gifts.
2. **Engaged.** After a period of dating (starting point: 7 days) with opinions staying high (+70 or more), one proposes. The town can gossip about it.
3. **Married.** After a period engaged (starting point: 5 days), a small wedding where friends and family gather. Afterwards they share a home.

Things can go wrong: jealousy toward rivals, opinion dropping over time, and breakups (or calling off the engagement) when a partner's opinion falls too low. Breakups leave heartbreak memories on both sides.

### Children

Married couples with a home may have children, which keeps settlements alive across generations. (Minecraft Comes Alive does something similar; this design is our own.)
- A child starts small and grows into an adult over a number of in-game days (starting point: 20). Children need a smaller model, likely the player model scaled down with a larger head.
- Children inherit trait axes as a blend of both parents plus a random shift, and each parent quirk has a chance (about 30%) to pass on. Food preferences are partly inherited too.
- Family links are set automatically, so siblings, cousins and so on follow from the graph.
- Children are invincible by default (section 7). They don't fight; they flee and hide.
- Children don't work real jobs, but follow parents around, play, and help with small tasks as they get older.

### The player

The player can do everything NPCs can, with the player making their own choices on one side: dating, getting engaged, marrying, and having children with an NPC spouse. Dialogue gets options to ask someone out, propose, and break up. NPCs with high enough opinions of the player can also ask the player. The same opinion requirements apply to the NPC side; the player is free to decide.

---

## 6. Tastes

Every NPC has a like value from -1.0 to +1.0 for every item in the game, every kind of weather, every biome and every season.

Each like is built from three parts:
- **A shared default.** Everyone loves a diamond, nobody wants a handful of dirt (giving dirt is rude). Items are sorted into categories (food, valuable, junk, gross, material, tool, weapon, armor, flower, plant, curio, music, decor, dangerous, comfort, fun) by name, so modded items land somewhere sensible.
- **Personality pulls.** Traits and quirks pull whole categories:
  - greedy loves valuables, humble and generous care less for them
  - gluttons like all food more and feel their tastes more strongly
  - hardworking likes tools and materials, lazy likes beds
  - brave likes weapons and storms, timid likes armor and hates storms and explosives
  - curious likes curios, romantic likes flowers and spring, sentimental likes flowers, music and fall
  - crude doesn't mind gross things or junk, proper minds them more; proud is insulted by junk
  - lighthearted likes music, fun things and snow; introverts like rain and fog; paranoid hates fog
  - night owls like winter, early birds like summer
- **Personal noise.** Derived from the NPC's seed and the thing's id, so nothing needs storing until it's changed. Food gets a lot of noise, so favorite foods vary widely. Now and then (about 6% of items) the noise flips the default on its head: the odd NPC really doesn't mind dirt, or isn't impressed by diamonds.

What it drives:
- **Gifts.** Liked gifts raise opinion more the more they're liked. Disliked gifts lower it ("gave me junk"). Junk or gross gifts the NPC strongly dislikes count as an insult (doubled for proud NPCs). The NPC says something about the kind of thing and how it feels, and remarks when its taste is unusual.
- **Eating.** NPCs eat the food they like most; good and bad food affect mood (twice as much for gluttons).
- **Weather.** Being out in weather they like or hate affects mood (rain lovers enjoy a downpour). When the weather turns, they may remark on it, likelier the stronger they feel.
- **Places.** Living in a biome they love or hate colors their mood. Walking into a new biome (usually while following someone) may get a remark.
- **Seasons.** Remarks when the season changes, and smalltalk about it.
- **Places** include caves (creepy to some, cozy to others) and the ocean (some love the smell of it), besides biomes.
- **Secret until heard.** The profile screen shows a love or hate as "?" until the player has heard the NPC talk about it, or seen its reaction to being given it.
- **Speech words:** `[favorite_food]`, `[hated_food]`, `[favorite_thing]` and `[hated_thing]` (the things most their own: what they love or hate more than most people do, so a curious NPC's favorite may be a compass rather than a diamond), `[favorite_weather]`, `[favorite_biome]`, `[favorite_season]` and their hated versions.

---

## 7. Health modes

Three modes, replacing the current invulnerable flag:

| Mode | Takes damage | Can die | Use |
|---|---|---|---|
| Mortal | yes | yes | The default |
| Invincible | yes | no | Important characters, like Skyrim's essential NPCs |
| Invulnerable | no | no | Debug only |

An invincible NPC fights, gets hurt and flees like anyone else. Damage that would kill them instead leaves them at 1 health, knocked down for several seconds, after which they get up and recover. Enemies lose interest in a downed NPC.

### Making someone invincible

The player chooses who they want to keep safe: a spouse, children, friends, companions, allies. This is done with a **guardian amulet**, a craftable item that makes any NPC invincible while it's in their inventory.
- **Gifted like any item.** You can give it to any NPC you could normally give gifts to, and they respond to it with their own lines (touched, flustered, suspicious of the gesture, and so on).
- **Theirs to keep.** Once given, they keep it no matter what happens between you later. They can't die while carrying it, so it never drops.
- **Gifts in general are consumed.** A gift doesn't go into the NPC's inventory, so it can't be taken back out of a follower's pack.
- **A real cost.** The recipe should be expensive enough (gold and a diamond, for example) that you protect the people who matter, not everyone.
- **Its own slot.** The amulet goes into a dedicated, locked slot in the NPC's inventory, so it takes up no pack space and can't be taken back. It isn't shown on the model: modular appearance (section 11) gives enough visual variety.
- **Recipe:** string on top, a gold block in the middle, gold ingots around it.

Children are the exception: they're invincible by default, with no amulet needed.

Brawls use the same rule for everyone involved: nobody can be reduced below 1 health by a brawl, and dropping low makes them yield.

---

## 8. Speech

### Facts and rules

When an NPC wants to speak, it builds a list of **facts** about the moment: the event, its own traits and tiers, quirks, mood, the listener and its opinion of them, faction relations, time of day, weather, its job, its memories about the listener, and so on.

Each line has **criteria** on those facts. The line whose criteria match the most facts wins (ties broken at random, weighted toward lines not used recently). Generic lines are the fallback; specific lines override them automatically. A hand-crafted character is just lines with the criterion `name = X`.

### Grammar

Lines contain **symbols** that expand into one of several alternatives, which can contain more symbols. Alternatives can carry the same criteria as lines (trait tiers, quirks, facts).

```
greet        -> [hello] [comment]
hello        -> Oh, hey [player]!          (manners: polite, lean)
             |  You.                       (manners: rude, strong)
             |  Hello.                     (neutral)
comment      -> [harvest_talk]             (if: own farm failed)
             |  Haven't had [food.a] in days.   (quirk: glutton)
harvest_talk -> Wheat didn't take. Next year, though!     (outlook: optimist, lean)
             |  Wheat's dead. Figures.                    (outlook: pessimist, strong)
             |  Wheat didn't take this year.              (neutral)
```

Fact symbols come from the NPC's data: `[player]`, `[name]`, `[job]`, `[home]`, `[faction]`, `[enemy_faction]`, `[favorite_food]`, `[spouse]`, `[time_of_day]`, and so on.

**Agreement.** Word symbols know their form (one item, plural or mass noun) and expand correctly:
- `[food.a]`: "a pork chop", "some blackberries", "some bread"
- `[food.the]`: "the pork chop", "the blackberries"
- `[food.it]`: "it" or "them"
- `[food.is]`: "is" or "are"
- `.cap` capitalizes: `[food.it.cap] [food.is] the best.`

**Repetition.** Each NPC remembers its recent lines and avoids them; recently used alternatives are weighted down.

### Speech style

A surface layer rolled per NPC, independent of personality, so two NPCs with the same traits still sound different. It supplies its own values for shared symbols and applies small transforms:
- symbols: `[hello]`, `[goodbye]`, `[yes]`, `[no]`, `[exclaim]`, `[filler]`, `[address]` (what they call people: "friend", "mate", "sir");
- a catchphrase, inserted occasionally;
- transforms: dropped g's ("goin'"), trailing "...", "y'know" at the end, no capitals.

Formality comes from the Speech axis; the rest is random. Because style supplies fragments rather than whole lines, it adds to the writing instead of multiplying it.

### Chat

Speech appears in chat as `<Name> line` within a hearing radius (about 24 blocks), for barks, direct messages to the player and conversations between NPCs. Each NPC has a bark cooldown (chatterbox shorter, taciturn longer), and there is a global rate limit so a 30-NPC battle doesn't flood chat.

---

## 9. Word book

NPCs occasionally ask the player for a new word, in conversation or as a chat message when you're nearby ("What would you call something you'd eat for breakfast?"). Friendlier, curious and chatty NPCs ask more often. The player can answer or decline.

After learning a word, the NPC asks whether it's **just for them** or **something anyone could say**:
- **Just for them:** a personal word, like their own catchphrase or their nickname for you. It never spreads.
- **Anyone:** a general word. It spreads through conversations between NPCs who are at least friendly, so it travels through a town but not to strangers far away.

Each word has a **category** that says exactly what it is, which keeps sentences coherent:

| Category | Forms asked for | Example use |
|---|---|---|
| Food | one item / plural / mass, plus the other form if needed | "Haven't had [food.a] in days." |
| Animal | singular and plural | "Saw [animal.a] by the river." |
| Thing | singular and plural | "Lost my [thing] again." |
| Place | name | "Ever been to [place]?" |
| Exclamation | text | "[exclaim]! Did you see that?" |
| Greeting | text | Used as their hello |
| Farewell | text | Used as their goodbye |
| Nickname for you | text | Used instead of your name |
| Describing word | text | "That's [describe]." |
| Activity | as a doing-word phrase ("rock skipping") | "Been [activity] lately?" |

- **No fading.** Words stay until the player deletes them.
- **No editing.** A word can only be deleted, never changed.
- **The word book screen** lists every taught word: category, personal or general, who you taught it to, and how many NPCs know it. Deleting a word removes it from everyone.
- **Per NPC**, a dialogue option ("What words do you know?") lists that NPC's words and where they picked each one up.

---

## 10. Archetype labels

A label for flavor and the inspect screen, taken from an NPC's most distinctive traits. Strong axes count by their strength; quirks count as 0.7.

- If the top two traits have a pair name, use it.
- Otherwise use the name of the single top trait, if it is strong.
- If nothing stands out, there is no label.

Single traits:

| Trait | Label | Trait | Label |
|---|---|---|---|
| Pessimist | the Cynic | Optimist | the Optimist |
| Rude | the Boor | Polite | the Gracious One |
| Crude | the Roughneck | Proper | the Refined One |
| Timid | the Worrier | Brave | the Daredevil |
| Introvert | the Loner | Extrovert | the Socialite |
| Serious | the Stoic | Lighthearted | the Jokester |
| Vengeful | the Grudge-holder | Forgiving | the Peacemaker |
| Lazy | the Slacker | Hardworking | the Workhorse |
| Chatterbox | the Chatterbox | Taciturn | the Silent One |
| Hothead | the Hothead | Zen | the Calm One |
| Gullible | the Innocent | Paranoid | the Skeptic |
| Greedy | the Miser | Generous | the Giver |
| Honest | the Straight Shooter | Liar | the Fibber |
| Nosy | the Busybody | Detached | the Drifter |
| Proud | the Braggart | Humble | the Modest One |
| Early bird | the Early Riser | Night owl | the Night Owl |
| Romantic | the Romantic | Loyal | the Faithful |
| Glutton | the Glutton | Pious | the Devout |
| Curious | the Wanderer | Jealous | the Envious |
| Sentimental | the Sentimentalist | Competitive | the Rival |

Pairs:

| Traits | Label |
|---|---|
| Pessimist + Rude | the Grump |
| Optimist + Extrovert | the Ray of Sunshine |
| Brave + Hothead | the Brawler |
| Timid + Introvert | the Wallflower |
| Lighthearted + Crude | the Rascal |
| Lazy + Lighthearted | the Layabout |
| Serious + Hardworking | the Taskmaster |
| Proper + Polite | the Aristocrat |
| Nosy + Chatterbox | the Gossip |
| Greedy + Liar | the Swindler |
| Romantic + Chatterbox | the Charmer |
| Curious + Brave | the Adventurer |
| Vengeful + Hothead | the Firebrand |
| Loyal + Brave | the Guardian |
| Humble + Hardworking | the Salt of the Earth |

---

## 11. Appearance

Generated NPCs are assembled from layers instead of using one fixed skin. Hand-made skins stay supported, for unique characters and anyone who wants them.

### Layers

Each layer is a 64x64 skin-format PNG. Fixed layers are drawn in full color and used as they are. Recolorable layers are recolored by palette swap (a gradient map), not by multiplying a tint, so dark colors keep their shading:

| Pixel in a recolorable layer | Becomes |
|---|---|
| `#202020` | palette shade 1: deepest shadow, inner lines |
| `#505050` | palette shade 2: shadow |
| `#808080` | palette shade 3: mid shade |
| `#B0B0B0` | palette shade 4: base color (most of the area) |
| `#E0E0E0` | palette shade 5: highlight |
| anything else | itself, unchanged |

- Every color option (a hair color, a dye, a skin tone, an eye color) is a palette of five colors, darkest to lightest, defined in a small text file. Using the same five grays in every part means a palette looks the same on hair, shirts and pants.
- Anything that isn't one of the five grays passes through untouched: pure white eye whites and black pupils on an eye layer (only the iris recolors), a brown belt on a recolorable shirt, a colored gem. So a part never needs splitting into two files just to keep a detail fixed.
- Pixel art only: no soft brushes or anti-aliasing, so every pixel is either one of the five grays or meant to stay as drawn.

| Layer | Recolorable? | Palette | Examples |
|---|---|---|---|
| Base | yes | skin tone | the body, in wide and slim arm versions |
| Eyes | yes (iris only) | eye color | several eye shapes; whites and pupils stay as drawn |
| Mouth | no | | mouths and expressions |
| Facial hair | yes | hair color | beards, moustaches |
| Hair | yes | hair color | styles, using the outer layer for volume |
| Tops | either | dye color, if recolorable | shirts, tunics, dresses, aprons |
| Bottoms | either | dye color, if recolorable | trousers, skirts, shoes |
| Accessories | no | | scarves, glasses, hats |

Tops and bottoms are recolorable or fixed per item: plain clothes come in any dye color, while patterned or detailed ones keep their drawn colors.

Layers are stacked in order and combined into a single texture per NPC, built once and cached by its exact combination. Two NPCs with identical looks share a texture. Rebuilding a 64x64 texture is cheap, so changing clothes costs almost nothing.

Variety comes from multiplying a modest amount of art: a dozen hairstyles in many colors, a range of skin tones, and clothing in dye colors already gives millions of combinations.

### Clothing that changes

An NPC's look is split into **body** (skin, eyes, face, hair: fixed for life, except hair could grow or be cut later) and **outfits** (sets of clothing layers). NPCs change outfits with their job and schedule: work clothes for the farmer, an apron for the cook, nightclothes for sleeping, something nicer for a wedding. Armor still draws on top of everything, as it does now.

### Inheritance

Children inherit body traits: skin tone blended from both parents, hair and eye color from one or a blend, and a chance at a parent's hairstyle or face features. Families visibly look related.

### The art

Layers live in a resource folder with a small data file describing each one (its slot, what tints it, which arm width), so adding a hairstyle means adding a PNG and a line of data. I can generate simple placeholder layers to get the system working; better art can replace them at any time.

## 12. Implementation order

1. **Health modes:** mortal, invincible, invulnerable. Small, and brawls need it.
2. **Traits, quirks and food preferences:** data, generation with the blacklist, NBT saving, commands to view and set them. Courage replaces bravery.
3. **Memories, mood and opinions:** the memory catalog, standing factors, compatibility, and opinion replacing the personal relation in combat.
4. **Speech engine:** facts, rules, grammar, agreement, anti-repetition, speech style, chat output. Lines live in data files, editable without rebuilding.
5. **First writing pass:** greetings, farewells, combat barks, low health, idle comments, all with full neutral coverage.
6. **Brawls.**
7. **Word book:** NPCs asking, personal and general words, spreading, the book screen.
8. **Modular appearance:** layers, tinting, outfits, the amulet showing.
9. **Family graph**, then gossip, then romance (NPCs and the player), then children.

## Settled

- Pious NPCs follow the Cube God.
- Sleeping NPCs do not count toward skipping the night.
- Brawls exist, between hotheads and between NPCs who hate each other.
- The player can date, marry and have children with NPCs, under the same rules as NPCs.
- Children exist, grow up and are invincible by default.

## Open questions

- The guardian amulet's recipe.
- Who draws the appearance layers beyond placeholders.
- How long a child takes to grow up, and how children are drawn.
