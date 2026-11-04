package lifeblood.entity;

import com.mojang.nbt.tags.CompoundTag;
import net.minecraft.core.item.ItemFood;
import net.minecraft.core.item.ItemSoup;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import lifeblood.inventory.NpcInventory;
import lifeblood.personality.Quirk;
import lifeblood.personality.Tastes;
import lifeblood.social.MemoryType;
import lifeblood.speech.Word;

/** Eating by the player's rules: only when hurt enough, picking the food it likes best, one bite at a time. */
public final class NpcEating implements NpcPart {
	/** How often the NPC checks whether it should eat, in ticks. */
	private static final int CHECK_INTERVAL = 10;
	/** How long food is held before it's eaten, so the switch is visible. */
	private static final int HOLD_TICKS = 20;

	private final EntityNPC npc;
	/** Ticks left before the held food is eaten; 0 when not eating. */
	private int eatTicks;
	/** Where the item held before eating went, to switch back to it afterwards; -1 if none. */
	private int returnSlot = -1;

	NpcEating(EntityNPC npc) {
		this.npc = npc;
	}

	@Override
	public void tick() {
		if (this.eatTicks > 0) {
			if (--this.eatTicks == 0) {
				this.finish();
			}
		} else if (this.npc.tickCount % CHECK_INTERVAL == 0) {
			this.start();
		}
	}

	public boolean isEating() {
		return this.eatTicks > 0;
	}

	/** Stops eating without finishing (food stays in hand). */
	void stop() {
		this.eatTicks = 0;
		this.returnSlot = -1;
	}

	/** The player's rule for eating: only while health plus the healing still queued from earlier food is below max. */
	private boolean wantsFood() {
		return this.npc.getHealth() > 0 && this.npc.getHealth() + this.npc.getTotalHealingRemaining() < this.npc.getMaxHealth();
	}

	/** Switches food into the hand; it's eaten a second later, like a player picking the food then using it. */
	private void start() {
		if (!this.wantsFood()) {
			return;
		}

		int slot = this.findFavoriteFood();
		if (slot < 0) {
			return;
		}

		this.npc.inventory.swapWithHand(slot);
		this.returnSlot = slot == NpcInventory.HAND_SLOT ? -1 : slot;
		this.eatTicks = HOLD_TICKS;
	}

	/** The slot holding the carried food this NPC likes most, or -1 if it has none. */
	private int findFavoriteFood() {
		int best = -1;
		float bestLike = Float.NEGATIVE_INFINITY;
		for (int slot = 0; slot < this.npc.inventory.getContainerSize(); slot++) {
			ItemStack stack = this.npc.inventory.getItem(slot);
			if (stack != null && Tastes.isFood(stack.getItem()) && ((ItemFood) stack.getItem()).getHealAmount(stack) > 0) {
				float like = this.npc.personality.tastes.like(stack.getItem());
				if (like > bestLike) {
					best = slot;
					bestLike = like;
				}
			}
		}

		return best;
	}

	/** Eats one of the held food (soup leaves its bowl behind), then switches back to what was held before. */
	private void finish() {
		ItemStack stack = this.npc.getHeldItem();
		if (stack != null && stack.getItem() instanceof ItemFood food && this.wantsFood() && stack.consumeItem(null)) {
			this.npc.eatFood(stack);
			this.reactTo(food);
			float pitch = 1.1F + (this.npc.getRandom().nextFloat() - this.npc.getRandom().nextFloat()) * 0.1F;
			float volume = 0.5F + (this.npc.getRandom().nextFloat() - this.npc.getRandom().nextFloat()) * 0.1F;
			this.npc.world.playSoundAtEntity(null, this.npc, food.getTicksPerHeal(stack) >= 10 ? "random.bite_extended" : "random.bite", volume, pitch);
			if (stack.stackSize <= 0) {
				this.npc.setHeldItem(food instanceof ItemSoup ? Items.BOWL.getDefaultStack() : null);
			}
		}

		if (this.returnSlot >= 0) {
			this.npc.inventory.swapWithHand(this.returnSlot);
			this.returnSlot = -1;
		}
	}

	/** Good food lifts the mood and bad food sours it, twice as much for gluttons; sometimes the NPC says so. */
	private void reactTo(ItemFood food) {
		float like = this.npc.personality.tastes.like(food);
		float strength = Math.abs(like) * (this.npc.personality.has(Quirk.GLUTTON) ? 2.0F : 1.0F);
		if (like >= 0.2F) {
			this.npc.remember(MemoryType.ATE_FOOD_I_LIKE, null, strength);
		} else if (like <= -0.2F) {
			this.npc.remember(MemoryType.ATE_FOOD_I_HATE, null, strength);
		}

		if (this.npc.getRandom().nextFloat() < 0.25F) {
			this.npc.voice.bark("eat", null, context -> context.word("food", Word.ofFood(food)).fact("taste", Tastes.describe(like)), false);
		}
	}

	/** Eating isn't saved: a bite in progress is simply abandoned. */
	@Override
	public void save(CompoundTag tag) {
	}

	@Override
	public void load(CompoundTag tag) {
	}
}
