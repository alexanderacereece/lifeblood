package lifeblood.homestead.life;

import java.util.function.Predicate;
import net.minecraft.core.entity.EntityItem;
import net.minecraft.core.entity.animal.MobAnimal;
import net.minecraft.core.item.ItemFood;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.item.tool.ItemToolAxe;
import net.minecraft.core.item.tool.ItemToolSword;
import lifeblood.entity.EntityNPC;
import lifeblood.personality.Quirk;
import lifeblood.work.Approach;
import lifeblood.work.ItemPickup;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBd;

/**
 * Hunting: takes a sword (or an axe) from home if there is one, finds an animal near home whose kind has more than a
 * few left around it, so herds last, chases it down and strikes by the player's melee rules, then gathers what it drops
 * and brings it home. Animal lovers never hunt (ChoresBehavior sees to that).
 */
public final class HuntChore implements Chore {
	/** How far from home they hunt, in blocks. */
	private static final int HUNT_RADIUS = 32;
	/** How many others of a kind must be around, within HERD_RADIUS, for one to be hunted, so some are always left. */
	private static final int HERD_LEFT = 2;
	private static final int HERD_RADIUS = 32;
	/** How long a chase goes on before they let it go, in ticks (40 seconds). */
	private static final int CHASE_TICKS = 800;
	/** How long they gather what fell after a kill, in ticks (8 seconds). */
	private static final int GATHER_TICKS = 160;
	private static final double GATHER_RADIUS = 6.0;

	private static final Predicate<ItemStack> WEAPON = stack -> stack.getItem() instanceof ItemToolSword || stack.getItem() instanceof ItemToolAxe;
	/**
	 * What a hunt brings home: raw meat and what else animals drop, and the weapon so others can use it. Food they were
	 * carrying for themselves stays with them.
	 */
	private static final Predicate<ItemStack> HAUL = stack -> WEAPON.test(stack) || stack.getItem() instanceof ItemFood && stack.getItem().getKey().contains("raw")
		|| stack.getItem() == Items.LEATHER || stack.getItem() == Items.FEATHER_CHICKEN || stack.getItem() == Items.EGG_CHICKEN
		|| stack.getItem() == Items.BONE || stack.getItem() == Items.STRING || stack.getItem().getKey().contains("wool");

	private enum Step {
		FETCH_WEAPON, FIND, CHASE, GATHER, STORE
	}

	private final Approach approach = new Approach();
	private Step step = Step.FETCH_WEAPON;
	@Nullable
	private MobAnimal prey;
	private long chaseStart;
	private long gatherStart;

	/** Whether this one would hunt at all: animal lovers won't. */
	public static boolean willHunt(EntityNPC npc) {
		return !npc.personality.has(Quirk.ANIMAL_LOVER);
	}

	@Override
	public boolean tick(EntityNPC npc) {
		switch (this.step) {
			case FETCH_WEAPON -> {
				if (Errands.fetchTool(npc, this.approach, WEAPON) != Errands.Trip.ON_THE_WAY) {
					npc.melee.readyWeapon();
					this.step = Step.FIND;
				}
			}
			case FIND -> {
				this.prey = findPrey(npc);
				this.step = this.prey != null ? Step.CHASE : Step.STORE;
				this.chaseStart = npc.world.getWorldTime();
			}
			case CHASE -> this.chase(npc);
			case GATHER -> {
				ItemPickup.pickUpNearby(npc);
				EntityItem item = ItemPickup.nearest(npc, GATHER_RADIUS);
				if (item != null && !npc.navigator.isMoving()) {
					npc.navigator.moveTo(item);
				}

				if (npc.world.getWorldTime() - this.gatherStart > GATHER_TICKS) {
					this.step = Step.STORE;
				}
			}
			case STORE -> {
				return Errands.storeAway(npc, this.approach, HAUL) != Errands.Trip.ON_THE_WAY;
			}
		}

		return false;
	}

	private void chase(EntityNPC npc) {
		MobAnimal prey = this.prey;
		if (prey == null || !prey.isAlive()) {
			if (prey != null) {
				npc.voice.bark("chore_hunted", null, null, false);
			}

			this.step = Step.GATHER;
			this.gatherStart = npc.world.getWorldTime();
			npc.navigator.stop();
			return;
		}

		if (npc.world.getWorldTime() - this.chaseStart > CHASE_TICKS || prey.distanceTo(npc) > HUNT_RADIUS * 1.5) {
			npc.navigator.stop();
			this.step = Step.STORE;
			return;
		}

		npc.lookAt(prey, 30.0F, 30.0F);
		npc.navigator.moveTo(prey);
		npc.melee.tryAttack(prey);
	}

	/** The nearest animal near home whose kind has more than a few others around it, or null. */
	@Nullable
	private static MobAnimal findPrey(EntityNPC npc) {
		AABBd area = new AABBd(npc.home.getX() - HUNT_RADIUS, npc.home.getY() - 8, npc.home.getZ() - HUNT_RADIUS, npc.home.getX() + HUNT_RADIUS,
			npc.home.getY() + 8, npc.home.getZ() + HUNT_RADIUS);
		MobAnimal best = null;
		double bestDistance = Double.MAX_VALUE;
		for (MobAnimal animal : npc.world.getEntitiesWithinAABB(MobAnimal.class, area)) {
			double distance = animal.distanceToSqr(npc);
			if (animal.isAlive() && distance < bestDistance && herdAround(npc, animal) >= HERD_LEFT) {
				best = animal;
				bestDistance = distance;
			}
		}

		return best;
	}

	/** How many others of the same kind live around an animal. */
	private static int herdAround(EntityNPC npc, MobAnimal animal) {
		AABBd area = new AABBd(animal.x - HERD_RADIUS, animal.y - 8, animal.z - HERD_RADIUS, animal.x + HERD_RADIUS, animal.y + 8, animal.z + HERD_RADIUS);
		int count = 0;
		for (MobAnimal other : npc.world.getEntitiesWithinAABB(MobAnimal.class, area)) {
			if (other != animal && other.getClass() == animal.getClass() && other.isAlive()) {
				count++;
			}
		}

		return count;
	}

	@Override
	public void stop(EntityNPC npc) {
		npc.navigator.stop();
	}

	@Override
	public String doing() {
		return "hunting";
	}

	@Override
	public String describe() {
		return switch (this.step) {
			case FETCH_WEAPON -> "fetching a weapon to hunt";
			case FIND -> "looking for game";
			case CHASE -> "hunting";
			case GATHER -> "gathering the kill";
			case STORE -> "bringing meat home";
		};
	}
}
