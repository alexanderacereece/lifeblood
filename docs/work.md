# Work and items (planned)

Not built yet. Jobs, and the real items they move around. Numbers are starting points for playtesting.

The goal is full detail: a fisherman fishes all day by the player's rules, carries the catch to his shop's chest, and townsfolk come and buy it; a blacksmith smelts real ore in a real furnace and crafts the guards' swords from the game's own recipes. Every item is a real item in a real container. Where nobody is watching, a ledger stands in, and turns back into real items when the area loads ([unloaded](unloaded.md)).

## Player parity

The same rules as everywhere else ([NPCs use the player's exact mechanics](README.md#settled)):
- breaking blocks at the player's digging speed for the held tool, wearing the tool down, with the block's real drops;
- placing blocks within the player's reach, against an existing face, with the player's placement delay;
- the game's own recipe list for crafting (modded recipes come free), real furnaces at real smelting speed;
- fishing with the player's timing and catch rules (BTA's bobber only works for a player, so NPCs get their own copy of it that follows the same rules);
- swinging the arm, with the right sounds.

## The pieces

Built in this order; each is useful on its own.

1. **Block actions.** Break and place by the player's rules, using a tool from the inventory. Shared by building, farming, mining, forestry and repairs.
2. **Job steps.** Small reusable steps: go to, break, place, use a block, take from a container, put into one, craft, smelt, fish, wait. A job strings them together; the brain runs one step at a time, and eating, fleeing and chatting still interrupt as now.
3. **Jobs.** A job is a [schedule](architecture.md) of activities, a workplace and the steps those activities use, plus the job's outfit, tastes and speech lines. Who gets which job is a [life choice](life-choices.md).
4. **Storage.** Which chests and furnaces belong to which shop, home or armory, kept in an index so nobody searches for them. Owned containers tie into [ownership](ownership.md): taking from one that isn't yours is theft.
5. **Demand.** What people need: food (eating already works), tools that wore out, gear for guards, materials for builders. Shortfalls become requests to the player (planned on the roadmap).

## Example chains

- **Fisher:** fishes at the shore all day, carries the catch to the shop chest, cooks some at a furnace if they have one.
- **Farmer:** tills, plants, harvests and replants; brings crops to the shop or the household.
- **Forester:** chops trees in a tree farm and always replants saplings; logs go to the builders' stockpile.
- **Hunter:** hunts, but never the last animals in an area, so the herds last. Animal lovers won't take this job.
- **Miner:** digs a designated mine, honestly (see below). Ore goes to the smith.
- **Sifter:** runs loose blocks through a trommel for what it yields. A slow, renewable job.
- **Smith:** smelts ore, crafts tools, weapons and armor from real recipes, stocks the armory and fills orders.
- **Guard:** equips from the armory; without iron, guards go without.
- **Cook, merchant, innkeeper:** turn raw goods into meals and sell them.

Before currency exists, goods go to household chests and are shared; with currency, they're bought and sold (currency is planned on the roadmap).

## Resources

Renewable work (fishing, farming, forestry, hunting) stays fully 1:1, as long as foresters replant and hunters leave enough animals. Mining stays honest too:
- **While loaded, mining is real.** A town's mine is a designated tunnel or quarry, dug honestly, so it doesn't swiss-cheese the countryside.
- **Depletion is a story driver.** When a mine runs dry the town opens a new one farther out, buys ore, or fights over land rich in it. Mining towns boom and bust.
- **Demand falls off.** Once a town has a baseline of tools and gear, it needs far less ore; most mining after that replaces what wears out or is lost.
- **Scarcity raises prices.** Iron grows dear as mines run dry; smiths repair what they can (it saves little ore, but some), and guards keep old gear longer.
- **While unloaded**, a mine draws on an estimated deposit, measured by sampling the real ore around it when it opens; tunnels catch up when the area loads ([unloaded](unloaded.md)).

## Performance

Most work is walking and waiting on timers, which costs little. The costly parts are searching for blocks and containers, so towns keep an index of their containers, workplaces and work sites, and block searches share a budget the way pathfinding does.

## Open questions

- What BTA's trommel yields from which blocks, and whether that makes sifting worthwhile.
- How NPCs carry large loads: one inventory's worth per trip, or carts later.
- Whether NPCs can hire the player for a job, through requests.
