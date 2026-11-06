# Daily life (being built)

**Built so far:** the day's schedule, sleeping in their own beds, meals from the chests, gathering wood, farming, hunting, walks, and children playing near home. **Next:** fishing, cooking meat at the furnace, older children's chores, and speech lines about their work.

How NPCs who live somewhere spend their days without anyone telling them what to do, starting with homesteaders: sleeping in their own beds, eating from the household's chests, and doing the chores a household needs. Adventurers and travelers come after ([travelers](travelers.md)). Numbers are starting points for playtesting.

Everything here follows the player's rules ([work](work.md#player-parity)): blocks break at the player's digging speed for the tool in hand and drop what they'd drop for a player, tools wear down, blocks are placed within reach, and food comes from real chests.

## The day

A homesteader's day (`homestead` schedule), in in-game hours:

| From | Activity |
|---|---|
| 6 | Up and about: a bite to eat if hungry, then chores |
| 12 | Midday meal at home, from the household's chests |
| 13 | Chores, or a walk |
| 18 | Home: evening around the house, chatting |
| 21 | Bed: walks to their own bed and sleeps until morning |

Early birds live an hour ahead of the clock (up at 5, in bed at 20) and night owls an hour behind (up at 7, in bed at 22).

Fights, brawls, being downed and conversations still interrupt anything, as now.

- **Sleeping:** each person has their own bed (`NpcHome.getBed`), assigned when the homestead generates; they start the world standing beside it. At bedtime they walk to it and lie in it, head on the pillow, and get up beside it in the morning. A child whose parent is asleep stops following them and goes to its own bed. Sleeping NPCs don't count toward skipping the night.
- **Eating:** players in Better than Adventure eat to heal, not to stave off hunger, and NPCs follow the same rule ([health](health.md)). So at the midday meal, anyone carrying fewer than 3 food items takes some from the household's chests, and eats it when hurt. Food runs out if nobody brings any in, which is what makes chores matter.

## Chores, by need and inclination

Nobody has a fixed job yet (jobs come later as [life choices](life-choices.md)). Each time someone is free for chores, they pick one, weighing:
- **What the household is short of:** under 12 food items (wheat counts) per person in the chests makes farming, fishing and hunting likelier; under 32 logs makes gathering wood likely, and past 64 they hardly bother.
- **What suits them:** the hardworking do more and rest less; the lazy take long walks; animal lovers never hunt; the restless walk farther.
- **What they have:** a hoe to farm, an axe for wood (bare hands work, slowly, as for a player), a fishing rod to fish, a weapon to hunt. Tools come from the household's chests and go back when they're done.

The chores, built in this order:
1. **Gathering wood** (built): takes the household's axe if there is one (bare hands work, slowly), finds a natural tree within 48 blocks of home (nobody placed it, it stands on soil, it has leaves), chops its trunk from the bottom up as far as they can reach, gathers what falls for 15 seconds, plants a sapling if they have one, and brings the logs, saplings, apples, sticks and the axe home. Finding no tree puts wood off for an in-game day.
2. **Farming** (built): takes the hoe and seeds from home, harvests ripe wheat, sows bare farmland, and tills up to 4 new plots a trip until the field has 16. The field is the farmland the household tilled (by ownership), within 20 blocks of home and kept 2 blocks clear of the house. A new field starts beside water if there's any within the game's 4-block reach for wetting farmland, else near home; where it can't grow (a slope, a tree), a new patch starts the same way. Tall grass and flowers are knocked off before tilling. Wheat, spare seeds and the hoe go home. Not yet: baking the wheat into bread.
3. **Hunting** (built): takes a sword or axe from home if there is one (fists otherwise), picks the nearest animal within 32 blocks of home that has at least 2 others of its kind around it, so some are always left, chases it for up to 40 seconds striking by the player's melee rules, gathers what drops, and brings home the raw meat, leather, feathers and the like (and the weapon). The brave hunt more, the timid less, animal lovers never; animal lovers who see it are upset, as with any killing. Not yet: cooking the meat at the furnace.
4. **Fishing:** at the nearest water, with the player's catch timing and rules.
5. **Walks** (built): a stroll 12 to 28 blocks from home and a while looking around; the curious go up to twice as far, the lazy walk more and the hardworking less.

Everything gathered goes into the household's chests.

## Children

From about 10, children help: they gather what falls, harvest and fish with a parent. Younger ones play near home and stay near a parent, as now (built: under 10, chores time is play within a few blocks of home).

## What it's built from

- **Block actions** (`work/`, built): breaking at the player's digging speed for the held tool (`BlockBreaking`: the player's formula, a fifth as fast underwater or in the air, drops for that tool, tool wear), using an item on a block within the player's 4.5-block reach (`BlockUse`: placing, sowing, tilling with the hoe's own rules), walking until a block is in reach (`Approach`), picking up what drops (`ItemPickup`), and taking from and putting into containers (`Containers`). NPCs far from players think less often, so digging and chore timers count game time, not thinking ticks. Shared with building, mining and jobs later.
- **The household's storage:** the homestead's chests, found from its prefab when it's generated and kept in its record, so nobody searches for them.
- **Chores** are behaviors run by a chores activity, which picks the next one when one finishes.

## Testing

`/lb debug speed <1-20>` runs a dedicated server that many game ticks per real one, so a few minutes covers days of homestead life; `/lb homestead list` shows what each house's chests hold.

## Open questions

- How far a homestead's field may spread, and whether they fence it.
- Whether homesteaders trade with each other once currency exists.
