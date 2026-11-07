# Life choices (planned)

How NPCs decide the big things for themselves: what work to do, whether to travel, move away, homestead or run for office. Not built yet, except two early pieces: the homebody and wanderer quirks, and homesteaders deciding each morning whether to set out on a trip ([travelers](travelers.md#who-goes-and-when)), which works the way choices here will. Numbers are starting points for playtesting.

The idea in one line: **the NPC decides what it wants, and the world decides what's available and what it costs.** Agency is choosing among real options by inclination; balance comes from shaping the options, never from overriding the NPC.

## Personality additions

Kept small on purpose, to avoid bloat.

- **Ambition** (content to ambitious), a ninth trait axis. Ambitious NPCs want more: a better job, a bigger house, an office, mastery of their trade, and they'll leave for an opportunity. Content NPCs are happy with what they have and rarely change course. Old saves are fine: loading only overwrites values present in the save, so existing NPCs get a fresh roll for the new axis.
- **Homebody / Wanderer** (built), a new quirk pair (see [quirks](personality.md#pairs)). Most NPCs sit in the unmarked middle. Today they set how often someone travels and how far; the rest comes with life choices.
  - Homebody: strongly tied to home, rarely travels, homesick when away, slow to move even when unhappy.
  - Wanderer: restless, travels, comes back with stories, likelier to homestead or move on. Home pulls on them less.

## When choices are made

Not every tick. A life-choices part (an `NpcPart`) looks at its options:
- about once an in-game day, at a random hour so NPCs don't all decide at once;
- at turning points: coming of age, a birthday, marrying, a death in the family, a breakup, a big fight, losing a job, a new opening in town.

## How a choice is scored

Every kind of choice is a registered option, the same way [dialogue options](architecture.md) are registered, so jobs, travel, moving, homesteading and elections are all plug-ins. Each option answers:
- **Is it available?** Only real openings count: a job the town needs, an election that's coming up, land to settle.
- **How much does this NPC want it?** Personality fit (axes and quirks) times circumstances (mood, opinions, family, money, age), plus some randomness.
- **What does it cost?** Leaving friends and family, giving up a job, the risk of a journey.

Then:
- **Pick at random, weighted by score, not always the best fit.** A lazy guard sometimes happens, and an NPC who goes against type is a good story.
- **Commit.** Once chosen, changing course has a switching cost that fades over weeks, so nobody changes careers every few days.
- **Remember why.** Each decision leaves a memory with its reason, so the NPC can explain it ("I left after what my brother did") and it shows on the profile. Every choice comes with speech lines: thinking about it, announcing it, looking back on it.

## Balance

- **Pressure builds over time.** A job nobody takes grows more appealing (as if the pay rises). A crowded town makes moving or homesteading more tempting. This evens things out without hard caps.
- **Big, permanent moves need a reason.** Leaving town takes a push (restlessness with no ties, a grudge, heartbreak, crowding) and something to go to. Spouses and children are strong anchors; a family moves together or not at all.
- **Safety nets, not the main control.** A per-settlement limit on how many can leave in a year, so a town can't empty overnight by bad luck.
- **Settings** in `config/lifeblood.cfg` for the main dials, for tuning.

## The choices

| Choice | Leans toward it | Notes |
|---|---|---|
| A job | depends on the job (see [work](work.md)) | Children lean toward a parent's trade and can apprentice. |
| Changing jobs | ambitious, unhappy at work | Losing a job forces a choice. |
| A journey | wanderer, curious, brave | Goes somewhere and comes back with stories and memories ([travelers](travelers.md), built for homesteaders). |
| Moving to another settlement | wanderer, ambitious, a grudge or heartbreak at home | Needs an opening or a relative there. |
| Homesteading | wanderer, brave, introvert, ambitious | Picks land and builds a home ([building](building.md)). |
| Running for office | ambitious, proud, competitive, extrovert | Wins by opinions (democracy) or appointment, per government. |
| Joining a guild or faction | depends on the guild | Pious to religious orders, greedy to traders. |
| Retiring | old, content | Hands work to an apprentice or a child. |

Romance and children already work this way in spirit ([family](family.md)) and stay as they are.

## Unloaded NPCs

Most choices play out where nobody is watching. They run on the NPC's entry in the world's register of people and are made real when the area loads ([unloaded](unloaded.md)).

## Open questions

- How often a typical NPC should make a big life change: once a year? Less?
- Whether the player can talk an NPC into or out of a choice, and how much that depends on opinion.
