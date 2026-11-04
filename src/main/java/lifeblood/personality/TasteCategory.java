package lifeblood.personality;

import java.util.Locale;

/**
 * What kind of thing an item is, as far as people's tastes go. Traits and quirks pull whole categories up or down
 * (greedy NPCs love valuables, gluttons love food), and speech files test the main one as {kind=junk}.
 */
public enum TasteCategory {
	FOOD,
	/** Gems, gold and the like. */
	VALUABLE,
	/** Dirt, cobblestone, gravel: handing someone this is rude. */
	JUNK,
	/** Slime, bones, raw meat. */
	GROSS,
	/** Ingots, wood, cloth: useful, not exciting. */
	MATERIAL,
	TOOL,
	WEAPON,
	ARMOR,
	FLOWER,
	/** Saplings, seeds, mushrooms. */
	PLANT,
	/** Compasses, clocks, maps, butterflies in jars. */
	CURIO,
	MUSIC,
	/** Paintings, statues, flags. */
	DECOR,
	/** Gunpowder, explosives. */
	DANGEROUS,
	/** Beds and seats. */
	COMFORT,
	/** Snowballs, carved pumpkins, ice skates. */
	FUN,
	/** Any other block. */
	BLOCK,
	/** Any other item. */
	ITEM;

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}
}
