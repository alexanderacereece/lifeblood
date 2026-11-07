package lifeblood.travel;

import com.mojang.nbt.tags.CompoundTag;

/**
 * One trip away from home: what kind, where to, how far along, and what it's turned up. Saved with the NPC while they're
 * loaded, and with their record in TravelRegister while they're away from every player.
 */
public final class Outing {
	/** How far along the trip is. */
	public enum Stage {
		/** Getting what they'll need from home. */
		PACKING,
		/** On the way there. */
		OUTBOUND,
		/** There, doing what they came for. */
		THERE,
		/** On the way home. */
		HOMEWARD,
		/** Home again: putting things away, telling about it. */
		HOME
	}

	/** The kind of trip, by its registered id ("journey", "adventure", "visit"). */
	public final String kind;
	/** Where they're going: a spot on the map; y is the ground there, or -1 if not known yet. */
	public int destX;
	public int destY;
	public int destZ;
	/** Where home is, to come back to. */
	public int homeX;
	public int homeY;
	public int homeZ;
	public Stage stage = Stage.PACKING;
	/** When the trip began and when they got there (world time); -1 until then. */
	public long started;
	public long arrived = -1L;
	/** How long they mean to spend there, in ticks. */
	public long stay;
	/** Whether they're a stranger passing through, with no home in these parts to come back to. */
	public boolean passingThrough;
	/** What happened, for what they say afterwards: found treasure, got hurt, got lost. */
	public boolean foundTreasure;
	public boolean gotHurt;
	/** The kind's own progress (what it's found, where the cave mouth was). */
	public CompoundTag data = new CompoundTag();

	public Outing(String kind) {
		this.kind = kind;
	}

	/** Where they're headed right now: the destination, or home on the way back. */
	public int targetX() {
		return this.stage == Stage.HOMEWARD || this.stage == Stage.HOME ? this.homeX : this.destX;
	}

	public int targetZ() {
		return this.stage == Stage.HOMEWARD || this.stage == Stage.HOME ? this.homeZ : this.destZ;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Kind", this.kind);
		tag.putInt("DestX", this.destX);
		tag.putInt("DestY", this.destY);
		tag.putInt("DestZ", this.destZ);
		tag.putInt("HomeX", this.homeX);
		tag.putInt("HomeY", this.homeY);
		tag.putInt("HomeZ", this.homeZ);
		tag.putString("Stage", this.stage.name());
		tag.putLong("Started", this.started);
		tag.putLong("Arrived", this.arrived);
		tag.putLong("Stay", this.stay);
		tag.putBoolean("PassingThrough", this.passingThrough);
		tag.putBoolean("FoundTreasure", this.foundTreasure);
		tag.putBoolean("GotHurt", this.gotHurt);
		tag.putCompound("Data", this.data);
		return tag;
	}

	public static Outing load(CompoundTag tag) {
		Outing outing = new Outing(tag.getStringOrDefault("Kind", "journey"));
		outing.destX = tag.getInteger("DestX");
		outing.destY = tag.getInteger("DestY");
		outing.destZ = tag.getInteger("DestZ");
		outing.homeX = tag.getInteger("HomeX");
		outing.homeY = tag.getInteger("HomeY");
		outing.homeZ = tag.getInteger("HomeZ");
		try {
			outing.stage = Stage.valueOf(tag.getStringOrDefault("Stage", Stage.PACKING.name()));
		} catch (IllegalArgumentException e) {
			outing.stage = Stage.PACKING;
		}

		outing.started = tag.getLong("Started");
		outing.arrived = tag.getLongOrDefault("Arrived", -1L);
		outing.stay = tag.getLong("Stay");
		outing.passingThrough = tag.getBoolean("PassingThrough");
		outing.foundTreasure = tag.getBoolean("FoundTreasure");
		outing.gotHurt = tag.getBoolean("GotHurt");
		outing.data = tag.containsKey("Data") ? tag.getCompound("Data") : new CompoundTag();
		return outing;
	}
}
