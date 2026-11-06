package lifeblood.homestead.life;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.block.BlockLogicLog;
import net.minecraft.core.item.block.ItemBlock;
import lifeblood.ai.NpcBehavior;
import lifeblood.entity.EntityNPC;
import lifeblood.personality.Quirk;
import lifeblood.personality.TraitAxis;
import org.jetbrains.annotations.Nullable;

/**
 * Chore time: picks a chore by what the household needs and what suits the person, does it start to finish, rests a
 * little, and picks the next. Nobody has a fixed job yet. Children under 10 play near home instead; older ones do the
 * lighter chores.
 */
public final class ChoresBehavior implements NpcBehavior {
	public static final String ID = "chores";
	/** The age children start doing chores, in years. */
	public static final int CHORE_AGE = 10;
	/** How many logs the household likes to keep in its chests; fewer makes gathering wood likelier. */
	private static final int LOGS_WANTED = 32;
	/** The rest between chores, in ticks, before work ethic: 5 to 20 seconds. */
	private static final int MIN_REST = 100;
	private static final int MAX_REST = 400;
	/** How long someone who found no trees near home leaves wood be, in ticks (an in-game day). */
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

			this.chore = pick(npc, npc.world.getWorldTime() < this.noWoodUntil);
		}

		if (this.chore.tick(npc)) {
			if (this.chore instanceof GatherWoodChore wood && wood.foundNoTrees()) {
				this.noWoodUntil = npc.world.getWorldTime() + NO_TREES_WAIT;
			}

			this.chore = null;
			// The hardworking get back to it sooner; the lazy take their time.
			float ethic = npc.personality.get(TraitAxis.WORK_ETHIC);
			int rest = MIN_REST + npc.getRandom().nextInt(MAX_REST - MIN_REST);
			this.restUntil = npc.world.getWorldTime() + Math.round(rest * (1.0F - 0.6F * ethic));
		}
	}

	/** The next chore, weighed by the household's needs and the person's inclinations; no wood if there are no trees near. */
	private static Chore pick(EntityNPC npc, boolean noTrees) {
		if (npc.life.getAgeYears() < CHORE_AGE) {
			return new WalkChore(2, 8);
		}

		float ethic = npc.personality.get(TraitAxis.WORK_ETHIC);
		boolean curious = npc.personality.has(Quirk.CURIOUS);
		List<Option> options = new ArrayList<>();
		int logs = Household.count(npc, stack -> stack.getItem() instanceof ItemBlock<?> block && block.getBlock().getLogic() instanceof BlockLogicLog);
		float woodNeed = noTrees ? 0.0F : logs < LOGS_WANTED ? 2.0F : 0.4F;
		options.add(new Option(woodNeed * (1.0F + 0.5F * ethic), GatherWoodChore::new));
		options.add(new Option((curious ? 1.2F : 0.6F) * (1.0F - 0.5F * ethic), who -> WalkChore.stroll(curious)));
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
	}

	@Override
	public String describe() {
		return this.chore != null ? this.chore.describe() : "resting between chores";
	}
}
