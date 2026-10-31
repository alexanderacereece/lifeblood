package npclaude.entity;

import com.mojang.nbt.tags.CompoundTag;

/**
 * Character traits that shape how an NPC behaves. Each trait runs from 0 to 1.
 * New traits go here, each with a default, and are saved alongside the others.
 */
public class NpcPersonality {
	public static final float DEFAULT_BRAVERY = 0.5F;

	/** 0 = flees from any fair fight, 1 = stands its ground against about 3 to 1. */
	private float bravery = DEFAULT_BRAVERY;

	public float getBravery() {
		return this.bravery;
	}

	public void setBravery(float bravery) {
		this.bravery = clamp(bravery);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putFloat("Bravery", this.bravery);
		return tag;
	}

	public void load(CompoundTag tag) {
		this.setBravery(tag.getFloatOrDefault("Bravery", DEFAULT_BRAVERY));
	}

	private static float clamp(float value) {
		return Math.max(0.0F, Math.min(1.0F, value));
	}
}
