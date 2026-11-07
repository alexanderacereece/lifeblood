package lifeblood.travel.kinds;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.enums.HumanArmorShape;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.item.tool.ItemToolAxe;
import net.minecraft.core.item.tool.ItemToolPickaxe;
import net.minecraft.core.item.tool.ItemToolSword;
import lifeblood.ai.NpcBehavior;
import lifeblood.combat.NpcArchery;
import lifeblood.entity.EntityNPC;
import lifeblood.homestead.life.Household;
import lifeblood.personality.Quirk;
import lifeblood.personality.TraitAxis;
import lifeblood.travel.Outing;
import lifeblood.travel.OutingKind;
import lifeblood.travel.Supplies;

/**
 * An adventure: out to somewhere a little way off, down into the caves there with torches and a pickaxe, and home with
 * what they found, if they come home. The brave, curious and greedy go; only those with a pickaxe and torches to take.
 * Out of sight it's worked out at once: coal and iron, mob drops, now and then a dungeon's treasure, the odd wound, and
 * a small chance of not coming back (more for the reckless and the badly armed).
 */
public final class AdventureKind implements OutingKind {
	public static final String ID = "adventure";
	/** How far off they go looking for caves, in blocks. */
	private static final int MIN_DISTANCE = 60;
	private static final int MAX_DISTANCE = 200;
	/** How long they mean to spend underground, in ticks: a quarter to half an in-game day. */
	private static final int MIN_STAY = 6000;
	private static final int MAX_STAY = 12000;
	/** The chance of not coming back from an adventure out of sight, before courage and arms. */
	private static final float DEATH_CHANCE = 0.015F;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public float weight(EntityNPC npc) {
		boolean equipped = (Supplies.count(npc, AdventureKind::isPickaxe) > 0 || Household.count(npc, AdventureKind::isPickaxe) > 0)
			&& (Supplies.count(npc, AdventureKind::isTorch) > 0 || Household.count(npc, AdventureKind::isTorch) > 0);
		if (!equipped) {
			return 0.0F;
		}

		float weight = 0.6F * (1.0F + 0.8F * npc.personality.get(TraitAxis.COURAGE));
		if (npc.personality.has(Quirk.CURIOUS)) {
			weight *= 1.5F;
		}

		if (npc.personality.has(Quirk.GREEDY)) {
			weight *= 1.5F;
		}

		return Math.max(0.0F, weight);
	}

	@Override
	public Outing plan(EntityNPC npc, Random random) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE);
		Outing outing = new Outing(ID);
		outing.destX = (int) Math.floor(npc.home.getX() + Math.cos(angle) * distance);
		outing.destZ = (int) Math.floor(npc.home.getZ() + Math.sin(angle) * distance);
		outing.destY = -1;
		outing.stay = MIN_STAY + random.nextInt(MAX_STAY - MIN_STAY);
		return outing;
	}

	@Override
	public boolean packs(ItemStack stack) {
		return isPickaxe(stack) || isTorch(stack) || stack.getItem() instanceof ItemToolSword || stack.getItem() instanceof ItemToolAxe
			|| NpcArchery.isBow(stack) || stack.getItem() == Items.AMMO_ARROW || stack.getItem() == Items.COAL;
	}

	@Override
	public boolean sheltersAtNight() {
		return false;
	}

	@Override
	public NpcBehavior atDestination(Outing outing) {
		return new CaveExplorer(outing);
	}

	@Override
	public Outcome simulate(Outing outing, EntityNPC npc, Random random) {
		List<ItemStack> found = new ArrayList<>();
		found.add(new ItemStack(Items.COAL, 3 + random.nextInt(10)));
		if (random.nextFloat() < 0.7F) {
			found.add(new ItemStack(Items.ORE_RAW_IRON, 1 + random.nextInt(5)));
		}

		if (random.nextFloat() < 0.5F) {
			found.add(new ItemStack(Items.BONE, 1 + random.nextInt(4)));
		}

		if (random.nextFloat() < 0.4F) {
			found.add(new ItemStack(Items.STRING, 1 + random.nextInt(3)));
		}

		if (random.nextFloat() < 0.3F) {
			found.add(new ItemStack(Items.AMMO_ARROW, 2 + random.nextInt(6)));
		}

		// Now and then a dungeon, and what's in its chest.
		if (random.nextFloat() < 0.12F) {
			outing.foundTreasure = true;
			found.add(new ItemStack(Items.INGOT_IRON, 1 + random.nextInt(4)));
			if (random.nextFloat() < 0.4F) {
				found.add(new ItemStack(Items.INGOT_GOLD, 1 + random.nextInt(3)));
			}

			if (random.nextFloat() < 0.3F) {
				found.add(new ItemStack(Items.DUST_REDSTONE, 2 + random.nextInt(6)));
			}
		}

		// Danger: the brave take more risks; a weapon and armor make it likelier they come back.
		boolean armed = Supplies.count(npc, stack -> stack.getItem() instanceof ItemToolSword || NpcArchery.isBow(stack)) > 0;
		boolean armored = npc.getItemInArmorSlot(HumanArmorShape.CHEST) != null;
		float death = DEATH_CHANCE * (1.0F + 0.5F * npc.personality.get(TraitAxis.COURAGE)) * (armed ? 1.0F : 2.0F) * (armored ? 0.5F : 1.0F);
		float hurt = random.nextFloat() < 0.4F ? 2 + random.nextInt(9) : 0.0F;
		// Going down with nothing to eat makes it worse.
		boolean died = random.nextFloat() < death * (Supplies.foodCarried(npc) == 0 ? 1.5F : 1.0F);
		return new Outcome(found, hurt, died);
	}

	static boolean isPickaxe(ItemStack stack) {
		return stack.getItem() instanceof ItemToolPickaxe;
	}

	static boolean isTorch(ItemStack stack) {
		return stack.itemID == Blocks.TORCH_COAL.id();
	}
}
