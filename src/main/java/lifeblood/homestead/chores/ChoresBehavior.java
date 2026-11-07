package lifeblood.homestead.chores;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.block.BlockLogicLog;
import net.minecraft.core.item.ItemFood;
import net.minecraft.core.item.Items;
import net.minecraft.core.item.block.ItemBlock;
import lifeblood.ai.NpcBehavior;
import lifeblood.entity.EntityNPC;
import lifeblood.homestead.life.Household;
import lifeblood.family.StayNearParentBehavior;
import lifeblood.personality.Quirk;
import lifeblood.personality.TraitAxis;
import org.jetbrains.annotations.Nullable;

/**
 * Chore time: picks a chore by what the household needs and what suits the person, does it start to finish, rests a
 * little, and picks the next. Nobody has a fixed job yet. Children under 10 play near home instead; from 10 they do
 * the lighter chores (farming, fishing, cooking, walks), not felling trees or hunting.
 */
public final class ChoresBehavior implements NpcBehavior {
	public static final String ID = "chores";
	/** The age children start doing chores, in years: when they stop keeping close to a parent. */
	public static final int CHORE_AGE = StayNearParentBehavior.INDEPENDENT_AGE;
	/** How many logs the household likes to keep in its chests; fewer makes gathering wood likelier. */
	private static final int LOGS_WANTED = 32;
	/** How much food (wheat included) the household likes to keep, in items per person; less makes food chores likelier. */
	private static final int FOOD_WANTED = 12;
	/** The rest between chores, in ticks, before work ethic: 5 to 20 seconds. */
	private static final int MIN_REST = 100;
	private static final int MAX_REST = 400;
	/** How long someone who found no trees (or gravel) near home leaves it be, in ticks (an in-game day). */
	private static final long NO_TREES_WAIT = 24000L;

	/** One kind of chore: how much someone would want to do it right now, and making it. */
	private record Option(float weight, Function<EntityNPC, Chore> make) {
	}

	@Nullable
	private Chore chore;
	/** When the rest between chores is over (world time). */
	private long restUntil;
	/** Until when they don't bother looking for trees, having found none near home (world time). */
	private long noWoodUntil;
	/** Until when they don't look for gravel, having found none near home (world time). */
	private long noGravelUntil;

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public boolean canStart(EntityNPC npc) {
		return true;
	}

	@Override
	public void tick(EntityNPC npc) {
		if (this.chore == null) {
			if (npc.world.getWorldTime() < this.restUntil) {
				return;
			}

			this.chore = pick(npc, npc.world.getWorldTime() < this.noWoodUntil, npc.world.getWorldTime() < this.noGravelUntil);
		}

		npc.state.setDoing(this.chore.doing());
		if (this.chore.tick(npc)) {
			if (this.chore instanceof GatherWoodChore wood && wood.foundNoTrees()) {
				this.noWoodUntil = npc.world.getWorldTime() + NO_TREES_WAIT;
			}

			if (this.chore instanceof GravelChore gravel && gravel.foundNoGravel()) {
				this.noGravelUntil = npc.world.getWorldTime() + NO_TREES_WAIT;
			}

			this.chore = null;
			npc.state.setDoing("");
			// The hardworking get back to it sooner; the lazy take their time.
			float ethic = npc.personality.get(TraitAxis.WORK_ETHIC);
			int rest = MIN_REST + npc.getRandom().nextInt(MAX_REST - MIN_REST);
			this.restUntil = npc.world.getWorldTime() + Math.round(rest * (1.0F - 0.6F * ethic));
		}
	}

	/** The next chore, weighed by the household's needs and the person's inclinations; no wood if there are no trees near. */
	private static Chore pick(EntityNPC npc, boolean noTrees, boolean noGravel) {
		if (npc.life.getAgeYears() < CHORE_AGE) {
			return new WalkChore(2, 8);
		}

		float ethic = npc.personality.get(TraitAxis.WORK_ETHIC);
		boolean curious = npc.personality.has(Quirk.CURIOUS);
		// Children old enough to help do the lighter chores: no felling trees, no hunting.
		boolean grown = !npc.life.isChild();
		List<Option> options = new ArrayList<>();
		int logs = Household.count(npc, stack -> stack.getItem() instanceof ItemBlock<?> block && block.getBlock().getLogic() instanceof BlockLogicLog);
		float woodNeed = noTrees ? 0.0F : logs < LOGS_WANTED ? 2.0F : logs < LOGS_WANTED * 2 ? 0.3F : 0.05F;
		options.add(new Option(grown ? woodNeed * (1.0F + 0.5F * ethic) : 0.0F, GatherWoodChore::new));
		options.add(new Option((curious ? 1.2F : 0.6F) * (1.0F - 0.5F * ethic), who -> WalkChore.stroll(curious)));
		int food = Household.count(npc, stack -> stack.getItem() instanceof ItemFood || stack.getItem() == Items.WHEAT);
		float foodNeed = food < FOOD_WANTED * Math.max(1, Household.size(npc)) ? 2.0F : 0.6F;
		options.add(new Option(foodNeed * (1.0F + 0.5F * ethic), FarmChore::new));
		// Hunting is for the brave more than the timid, and never for animal lovers.
		float courage = npc.personality.get(TraitAxis.COURAGE);
		options.add(new Option(grown && HuntChore.willHunt(npc) ? foodNeed * 0.6F * (1.0F + 0.5F * courage) : 0.0F, who -> new HuntChore()));
		// Raw food in the chests, or something done in the furnace, wants seeing to before it's wasted.
		options.add(new Option(CookChore.hasWork(npc) ? 1.5F : 0.0F, who -> new CookChore()));
		// Fishing suits the patient as much as the hardworking: it doesn't lean on work ethic.
		options.add(new Option(FishChore.canFish(npc) ? foodNeed * 0.7F : 0.0F, FishChore::new));
		// A household that hunts with a bow keeps itself in arrows: made from flint, which comes from gravel.
		boolean wantsArrows = grown && FletchChore.wantsArrows(npc);
		options.add(new Option(wantsArrows && FletchChore.canFletch(npc) ? 1.2F : 0.0F, who -> new FletchChore()));
		boolean noFlint = Household.count(npc, FletchChore.FLINT) == 0;
		options.add(new Option(wantsArrows && noFlint && !noGravel ? 0.8F * (1.0F + 0.5F * ethic) : 0.0F, GravelChore::new));
		float total = 0.0F;
		for (Option option : options) {
			total += Math.max(0.0F, option.weight());
		}

		float roll = npc.getRandom().nextFloat() * total;
		for (Option option : options) {
			roll -= Math.max(0.0F, option.weight());
			if (roll <= 0.0F) {
				return option.make().apply(npc);
			}
		}

		return options.get(options.size() - 1).make().apply(npc);
	}

	@Override
	public void stop(EntityNPC npc) {
		if (this.chore != null) {
			this.chore.stop(npc);
			this.chore = null;
		}

		npc.state.setDoing("");
	}

	@Override
	public String describe() {
		return this.chore != null ? this.chore.describe() : "resting between chores";
	}
}
