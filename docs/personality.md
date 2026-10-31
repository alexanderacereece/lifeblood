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
- Nobody can die (see section 7). A brawler who drops to low health yields, and the fight ends. Timid NPCs yield earlier, proud and competitive ones later.
- Bystanders react: friends of a brawler may join or break it up, extroverts gather to watch, timid NPCs keep away.
- Afterwards, the winner and loser get memories ("beat me in a brawl", "won a fight", "lost a fight"), and the hate often cools a little. Competitive NPCs gain respect for whoever beat them.
- The player can be challenged to brawls too, and can start one by punching an NPC who isn't hostile.

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

Stages:
1. **Attraction.** A one-directional value next to opinion. It grows from time spent together, compatibility and gifts, and only between unattached adults with a good opinion of each other (friendly or better).
2. **Courting.** Once attraction is high enough, the NPC starts flirting: compliments, gifts, seeking the other out in the evenings. If the feeling is mutual, they become a couple. If not, rejection leaves a heartbreak memory.
3. **Engagement.** A couple whose opinion of each other stays high long enough gets engaged (the fiancé link), with a proposal the town can gossip about.
4. **Marriage.** A small wedding, where friends and family gather, after which they share a home.

Things can go wrong: jealousy toward rivals, a partner who loses opinion over time, and breakups that leave heartbreak memories on both sides. Children are a later question, since they need child models and growing up.

Whether the player can court NPCs is a separate decision for later.

---

## 6. Food preferences

Instead of one favorite food, every NPC has a like value from -1.0 to +1.0 for every food in the game.
- Values are derived from the NPC's random seed and the food's id, so foods added by other mods get preferences automatically and nothing needs storing until it changes.
- `[favorite_food]` is the food they like most, and `[hated_food]` the one they like least. Lines can also ask for any liked or disliked food.
- Preferences drive memories and mood: eating or being given food they like is good, food they hate is bad.
- When choosing what to eat, NPCs prefer foods they like (the same rules as the player for actually eating).
- Gluttons feel their preferences twice as strongly.

---

## 7. Health modes

Three modes, replacing the current invulnerable flag:

| Mode | Takes damage | Can die | Use |
|---|---|---|---|
| Mortal | yes | yes | The default |
| Invincible | yes | no | Important characters, like Skyrim's essential NPCs |
| Invulnerable | no | no | Debug only |

An invincible NPC fights, gets hurt and flees like anyone else. Damage that would kill them instead leaves them at 1 health, knocked down for several seconds, after which they get up and recover. Enemies lose interest in a downed NPC.

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

## 11. Implementation order

1. **Health modes:** mortal, invincible, invulnerable. Small, and brawls need it.
2. **Traits, quirks and food preferences:** data, generation with the blacklist, NBT saving, commands to view and set them. Courage replaces bravery.
3. **Memories, mood and opinions:** the memory catalog, standing factors, compatibility, and opinion replacing the personal relation in combat.
4. **Speech engine:** facts, rules, grammar, agreement, anti-repetition, speech style, chat output. Lines live in data files, editable without rebuilding.
5. **First writing pass:** greetings, farewells, combat barks, low health, idle comments, all with full neutral coverage.
6. **Brawls.**
7. **Word book:** NPCs asking, personal and general words, spreading, the book screen.
8. **Family graph**, then gossip and romance.

## Settled

- Pious NPCs follow the Cube God.
- Sleeping NPCs do not count toward skipping the night.
- Brawls exist, between hotheads and between NPCs who hate each other.

## Open questions

- Can the player court NPCs?
- NPC children: models, growing up, and whether they're worth it.
