package lifeblood.travel.kinds;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.item.tool.ItemToolAxe;
import net.minecraft.core.item.tool.ItemToolSword;
import lifeblood.ai.NpcBehavior;
import lifeblood.combat.NpcArchery;
import lifeblood.entity.EntityNPC;
import lifeblood.personality.Quirk;
import lifeblood.travel.Outing;
import lifeblood.travel.OutingKind;

/**
 * A journey: a long walk to somewhere far, a look around, and the walk back, for the pleasure of seeing it. Wanderers
 * go farthest and most often, the curious next. They take a weapon for the road.
 */
public final class JourneyKind implements OutingKind {
	public static final String ID = "journey";
	/** How far a journey goes, in blocks; wanderers twice as far. */
	private static final int MIN_DISTANCE = 150;
	private static final int MAX_DISTANCE = 400;
	/** How long they look around once there, in ticks: an in-game hour or three. */
	private static final int MIN_STAY = 1000;
	private static final int MAX_STAY = 3000;
	private static final int LOOK_AROUND = 12;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public float weight(EntityNPC npc) {
		float weight = 1.0F;
		if (npc.personality.has(Quirk.WANDERER)) {
			weight *= 2.0F;
		}

		if (npc.personality.has(Quirk.CURIOUS)) {
			weight *= 1.5F;
		}

		return weight;
	}

	@Override
	public Outing plan(EntityNPC npc, Random random) {
		boolean far = npc.personality.has(Quirk.WANDERER);
		double angle = random.nextDouble() * Math.PI * 2.0;
		int distance = (far ? 2 : 1) * (MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE));
		Outing outing = new Outing(ID);
		outing.destX = (int) Math.floor(npc.home.getX() + Math.cos(angle) * distance);
		outing.destZ = (int) Math.floor(npc.home.getZ() + Math.sin(angle) * distance);
		outing.destY = -1;
		outing.stay = MIN_STAY + random.nextInt(MAX_STAY - MIN_STAY);
		return outing;
	}

	@Override
	public boolean packs(ItemStack stack) {
		return stack.getItem() instanceof ItemToolSword || stack.getItem() instanceof ItemToolAxe || NpcArchery.isBow(stack)
			|| stack.getItem() == Items.AMMO_ARROW;
	}

	@Override
	public NpcBehavior atDestination(Outing outing) {
		return new StrollAround(outing.destX, outing.destZ, LOOK_AROUND, "looking around far from home");
	}

	@Override
	public Outcome simulate(Outing outing, EntityNPC npc, Random random) {
		// A journey is mostly safe: a few things picked up on the way, and now and then a scrape.
		List<ItemStack> found = new ArrayList<>();
		if (random.nextFloat() < 0.5F) {
			found.add(new ItemStack(Items.FEATHER_CHICKEN, 1 + random.nextInt(3)));
		}

		if (random.nextFloat() < 0.3F) {
			found.add(new ItemStack(Items.BONE, 1 + random.nextInt(2)));
		}

		float hurt = random.nextFloat() < 0.15F ? 2 + random.nextInt(5) : 0.0F;
		return new Outcome(found, hurt, false);
	}
}
