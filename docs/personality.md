# NPC personality and opinions

An NPC's character is built from layers, each simple on its own:

| Layer | What it is | Changes over time? |
|---|---|---|
| Trait axes | 8 continuous scales, the core of who they are | No |
| Quirks | 0 to 3 discrete traits that add topics and opinions | No |
| Mood | A temporary shift from recent events | Yes, constantly |
| Memories | Things that happened, each with an opinion effect | Yes, they fade |
| Family | Parent, spouse and fiancé links; other kin derived | Rarely |
| Tastes | A like value for every item, weather, biome and season | No |
| Speech style | Surface voice: what they call people, filler, catchphrase, habits | Its words change with age |
| Word book | Words the player taught, spreading between NPCs | Yes |

These feed two systems: **opinions** (how NPCs regard each other and players) and **speech** (what they say and how).

## Trait axes

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
| Ambition (planned) | Content | Ambitious |

Ambition is planned with [life choices](life-choices.md): ambitious NPCs want a better job, a bigger house, an office, and will leave for an opportunity; content ones are happy as they are.

### Intensity tiers

Lines are written against tiers, never exact values, so a moderate NPC never pulls from both extremes:

| Tier | Range | Lines available |
|---|---|---|
| Neutral | always | Untagged lines. Every event must have these. |
| Lean | 0.30 to 0.65 toward a pole | Neutral plus "lean" lines of that pole |
| Strong | 0.65 or more toward a pole | Neutral, lean and "strong" lines of that pole |

Tiers use the **effective** value (base plus mood, see [mood](#mood)).

### Generation

Planned: players choose or roll their own axes, quirks and tastes when they start ([player creation](family.md#player-creation-planned)), so compatibility works between NPCs and players exactly as between NPCs.

Each axis is rolled from a bell curve centered on 0 (standard deviation 0.55, clamped to ±1). About two in five values land in the neutral band, a third lean, and roughly one in four is strong. A typical NPC has one to three strong axes, which is what makes them recognizable.

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
- Behavior: fight or flight. Courage sets how much danger the NPC will face before it runs ([combat](../src/main/java/lifeblood/combat/CombatBehavior.java)).
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
- Behavior (planned, with [work](work.md)): how long they work each day, how many breaks they take, how quickly they get to tasks.
- Voice: complaining about work versus talking about it with pride.
- Opinions: hardworking NPCs dislike lazy ones, scaled by both values. Lazy NPCs don't care either way.

## Quirks

Discrete traits. Most come in opposing pairs, where most NPCs sit in the unmarked middle. Quirks already shape opinions, tastes, fighting and what NPCs say; the behaviors below that need systems not built yet (gossip, lying, daily schedules, work) arrive with those systems.

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
- Early bird: wakes and starts work early, goes to bed early, grumpy if kept up. (Built for homesteaders: their day runs an hour early.)
- Night owl: up late, sleeps in, more talkative at night. (Built for homesteaders: their day runs an hour late.)

**Homebody / Wanderer** (planned, with [life choices](life-choices.md))
- Homebody: strongly tied to home, rarely travels, homesick when away, slow to move even when unhappy.
- Wanderer: restless, travels and comes back with stories, likelier to homestead or move on.

### Singles

- **Romantic:** falls for people faster and more often, courts more actively, comments on couples, hit harder by rejection and heartbreak. Anyone can take part in romance ([family](family.md)); this quirk makes it much more likely.
- **Loyal:** much slower to sour on friends, family and faction. Defends them in fights even when timid. Being betrayed hits them extra hard.
- **Glutton:** talks about food constantly, eats more often, food preferences matter twice as much to them ([tastes](#tastes)).
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

## Mood

A single value from -100 to +100 that reflects recent events, like RimWorld's mood.

- Memories can carry a **mood effect** separate from their opinion effect. "My brother died" is a big mood hit for weeks; "ate a good meal" is a small boost for a few hours.
- Outlook scales mood swings: pessimists feel bad events more and good ones less, optimists the reverse. Zen dampens everything.
- Mood shifts the **effective** value of some axes, which changes which line tiers are available:
  - Outlook: shifted by up to ±0.6 at extreme mood. A cheerful NPC in mourning sounds gloomy, then recovers.
  - Manners: low mood pulls it down by up to 0.3. Bad days make people short with others.
  - Work ethic: low mood pulls it down by up to 0.3.
- The current mood is a dialogue fact too, so lines can react directly ("Sorry, I'm not myself today").

## Opinions

Every NPC has an opinion of other NPCs and players, from -100 to +100. It is the sum of **memories** and **standing factors**, and it is one-directional: A's opinion of B is separate from B's opinion of A.

### Memories

Each memory type has:
- an opinion effect toward the person involved, and an optional mood effect;
- a duration in in-game days, after which it fades out linearly over its last quarter;
- a stack limit, where each repeat counts for half of the previous one.

Mercy multiplies the duration of negative memories (×0.5 to ×2.0). Sentimental multiplies positive ones by 1.5. Proud doubles negative memories about their own pride (insults, defeats). Loyal halves negative memories about friends, family and faction.

The catalog (the exact numbers live in `social/MemoryType`; durations are in-game days, and gifts and chats are scaled as described under [pacing](#pacing)):

| Memory | Opinion | Mood | Duration | Stacks |
|---|---|---|---|---|
| Saved my life | +30 | +10 | 60 days | 3 |
| Gave me a guardian amulet | +25 | +8 | 60 days | 1 |
| Fought beside me | +8 | | 10 days | 5 |
| Gave me a gift | up to +8, by how much they like it | up to +3 | 14 days | 14 |
| Gave me food I like | up to +12, by how much they like it | up to +5 | 14 days | 14 |
| Gave me food I hate | -3 | -2 | 3 days | 3 |
| Gave me junk | -3 | -2 | 3 days | 3 |
| Had a nice chat | +2 | +2 | 3 days | 10 |
| Taught me a word | +2 | | 3 days | 5 |
| Cleared the air | +8 | +2 | 5 days | 2 |
| Beat me fair and square (competitive) | +8 | | 10 days | 2 |
| Turned me down | -5 | -8 | 5 days | 2 |
| Killed an animal (animal lovers) | -4 | | 2 days | 5 |
| Beat me in a brawl | -5 | -5 | 10 days | 3 |
| Picked a fight with me | -6 | -2 | 7 days | 3 |
| Insulted me | -8 | -3 | 4 days | 5 |
| Hit me | -12 | -5 | 3 days | 5 |
| Fled and left me | -15 | | 15 days | 3 |
| Broke up with me | -20 | -20 | 20 days | 2 |
| Lied to me (found out) | -20 | | 20 days | 3 |
| Stole from me | -25 | -5 | 30 days | 3 |
| Attacked my home | -30 | -10 | 40 days | 3 |
| Killed a friend | -40 | -15 | 80 days | 3 |
| Killed family | -70 | -30 | 200 days | 3 |
| Killed my spouse or child | -100 | -40 | 400 days | 3 |
| Heard they hurt someone | -12, at a quarter: -3 | | 3 days | 5 |
| Heard they killed someone | -40, at a quarter: -10 | | 30 days | 3 |

Not all of these happen yet: lying, stealing and attacking homes wait for crime, ownership and settlements. The two "heard" memories come from [gossip](speech.md#gossip), always at a quarter of their weight (half again if the story was exaggerated); hearing that a friend or relative was killed gives the witness's memory (killed a friend, family, spouse or child) at a quarter.

Mood-only memories (no opinion effect): ate food I like or hate, slept in a bed or outside, enjoyed or was miserable in the weather, likes or hates it here, saw an animal die, saw a death, a friend or family member died, their birthday, had a child, started dating, got engaged, went to a wedding, got married, won or lost a fight, nearly died.

**Detecting "saved my life"** needs careful balancing. Starting rule: the NPC was in combat below 30% health, someone else killed or drove off the attacker (or healed them), and the NPC survived the next 10 seconds. Only whoever did the most damage to the attackers gets the memory. To be tuned in playtesting so ordinary teamwork doesn't count.

### Pacing

Only the first gift to an NPC each in-game day moves their opinion; later ones that day are kept and used but leave no memory. Chats are limited to five per real-world minute (the guardian amulet doesn't count as a gift here). Both count for more because of it, and repeats add up steadily instead of halving:
- a gift is worth up to about +11 depending on how much they like it, lasting two weeks, up to 14 at once;
- a chat is worth +2, lasting three days, up to 10 at once.

Checkpoints: a daily liked gift plus some chatting makes most NPCs friends (50) in about three days, and close friends (80+) in about two weeks, which is where romance should become possible. `/lb debug limits off` lifts both limits for playtesting.

### Standing factors

Computed live, never stored:

| Factor | Effect |
|---|---|
| Same faction | +10 |
| Factions allied | +5 |
| Factions hostile | -15 |
| Family | +5 to +30 by how close: spouse +30, fiancé +25, partner +20, parent or child +20, sibling +15, and so on ([family](family.md#romance)) |
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

`/lb social stance` sets a debug override of how one NPC treats one player, which beats both opinion and faction.

### Brawls

Non-lethal fistfights between people who aren't hostile enough to kill each other: usually members of the same faction, or neutral factions. A brawl can start when:
- a hothead's irritation boils over (an insult, being hit, a lie found out);
- two NPCs hate each other (both at -40 or less) and run into each other, even if neither is a hothead. Rarer, and more likely the lower their opinions.

Rules:
- Fists only. Weapons are put away.
- Nobody can die (see [health modes](health.md)), and a brawler yields well before that, so an accident doesn't leave anyone at death's door. The fight ends when one brawler drops to their yield point:
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

### Gossip

News travels by word of mouth in NPC chats, and hearing that someone attacked or killed a person colors the hearer's opinion of them at a quarter of the weight of seeing it; liars embellish. This is how a reputation spreads through a town without reaching strangers 1000 blocks away. Details in [speech](speech.md#gossip). Not yet: gullible or paranoid listeners believing more or less, and outright invented stories.

### Storage

Memories are stored per NPC, only for people they actually have memories about. Standing factors are computed when needed, so 30 NPCs don't create 900 stored relations.

## Tastes

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

## Archetype labels (planned)

Not built yet. A label for flavor and the inspect screen, taken from an NPC's most distinctive traits. Strong axes count by their strength; quirks count as 0.7.

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
