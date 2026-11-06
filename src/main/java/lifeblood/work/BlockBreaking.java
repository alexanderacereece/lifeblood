package lifeblood.work;

import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.entity.TileEntity;
import net.minecraft.core.enums.EnumBlockSoundEffectType;
import net.minecraft.core.enums.EnumDropCause;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.util.helper.Side;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.entity.EntityNPC;
import lifeblood.inventory.NpcInventory;
import lifeblood.ownership.BlockOwners;
import org.jetbrains.annotations.Nullable;

/**
 * Breaking one block at a time by the player's rules (BlockLogic.getStrength and PlayerController): progress each tick
 * is the held tool's strength against the block over its hardness (a fifth as fast underwater or off the ground, and
 * very slow without the right tool), the arm swings as it digs, the block drops what it would for a player with that
 * tool, and the tool wears down. Only within the player's reach.
 */
public final class BlockBreaking {
	/** How often the arm swings and the dig sound plays while digging, in ticks (as the player's). */
	private static final int SWING_INTERVAL = 4;
	/** The most ticks of digging one call counts, so a long pause (unloading, a fight) doesn't finish a block at once. */
	private static final int MAX_ELAPSED = 20;

	private final EntityNPC npc;
	@Nullable
	private TilePos target;
	private Block<?> targetBlock = Blocks.AIR;
	private float progress;
	private int ticks;
	/** The world time of the last dig, so progress counts every game tick even when the NPC thinks less often. */
	private long lastDig;

	public BlockBreaking(EntityNPC npc) {
		this.npc = npc;
	}

	/** What happened this tick. */
	public enum Result {
		/** Still digging. */
		DIGGING,
		/** The block is broken; its drops are on the ground. */
		BROKEN,
		/** It can't be broken from here: out of reach, unbreakable, or already gone. */
		FAILED
	}

	/** Digs at a block for one tick; starts over if it's a different block from last tick. */
	public Result dig(int x, int y, int z) {
		World world = this.npc.world;
		TilePos pos = new TilePos(x, y, z);
		Block<?> block = world.getBlockType(pos);
		if (block == Blocks.AIR || block.getHardness() < 0.0F || !Reach.canReach(this.npc, x, y, z)) {
			this.stop();
			return Result.FAILED;
		}

		if (this.target == null || !this.target.equals(pos) || this.targetBlock != block) {
			this.target = pos;
			this.targetBlock = block;
			this.progress = 0.0F;
			this.ticks = 0;
			this.lastDig = world.getWorldTime() - 1;
		}

		// An NPC far from players thinks every few ticks; the digging between counts, as it would for a player.
		long elapsed = Math.max(1, Math.min(MAX_ELAPSED, world.getWorldTime() - this.lastDig));
		this.lastDig = world.getWorldTime();

		Reach.lookAt(this.npc, x, y, z);
		if (this.ticks++ % SWING_INTERVAL == 0) {
			this.npc.swingArm();
			world.playBlockSoundEffect(null, x + 0.5, y + 0.5, z + 0.5, block, EnumBlockSoundEffectType.DIG);
		}

		this.progress += strength(block) * elapsed;
		if (this.progress < 1.0F) {
			return Result.DIGGING;
		}

		this.breakBlock(world, pos, block);
		this.stop();
		return Result.BROKEN;
	}

	/** Forgets the block being dug, as a player letting go of the button. */
	public void stop() {
		this.target = null;
		this.targetBlock = Blocks.AIR;
		this.progress = 0.0F;
	}

	/** How far one tick of digging gets, out of 1, by the player's formula. */
	private float strength(Block<?> block) {
		float hardness = block.getHardness();
		if (hardness <= 0.0F) {
			return 1.0F;
		}

		ItemStack held = this.npc.getHeldItem();
		if (!this.canHarvest(block)) {
			return 1.0F / hardness / 100.0F;
		}

		float strength = held != null ? held.getStrVsBlock(block) : 1.0F;
		if (this.npc.isUnderAcidOrWater()) {
			strength /= 5.0F;
		}

		if (!this.npc.onGround) {
			strength /= 5.0F;
		}

		return strength / hardness / 30.0F;
	}

	/** Whether the held tool (or a bare hand) gets the block's proper drops, as for a player. */
	private boolean canHarvest(Block<?> block) {
		if (block.getMaterial().isAlwaysDestroyable()) {
			return true;
		}

		ItemStack held = this.npc.getHeldItem();
		return held != null && held.canHarvestBlock(this.npc, block);
	}

	/** The block comes away as it would for a player: the break effect, its drops, then the tool's wear. */
	private void breakBlock(World world, TilePos pos, Block<?> block) {
		int data = world.getBlockData(pos);
		TileEntity tileEntity = world.getTileEntity(pos);
		boolean proper = this.canHarvest(block);
		world.playBlockEvent(pos, 2001, block.id());
		if (!world.setBlockTypeNotify(pos, Blocks.AIR)) {
			return;
		}

		BlockOwners.setOwner(world, pos.x, pos.y, pos.z, null);
		block.dropWithCause(world, proper ? EnumDropCause.PROPER_TOOL : EnumDropCause.IMPROPER_TOOL, pos, data, tileEntity, null);
		ItemStack held = this.npc.getHeldItem();
		if (held != null) {
			// Each tool wears as its own rules say, as for a player.
			held.getItem().onBlockDestroyed(held, world, this.npc, block, pos, Side.TOP);

			if (held.stackSize <= 0) {
				this.npc.inventory.setItem(NpcInventory.HAND_SLOT, null);
			}
		}
	}
}
