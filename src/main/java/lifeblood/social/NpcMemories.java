package lifeblood.social;

import com.mojang.nbt.tags.CompoundTag;
import com.mojang.nbt.tags.ListTag;
import com.mojang.nbt.tags.Tag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lifeblood.entity.NpcPart;
import lifeblood.personality.NpcPersonality;
import lifeblood.personality.Quirk;
import lifeblood.personality.TraitAxis;
import org.jetbrains.annotations.Nullable;

/**
 * Everything an NPC remembers, and what those memories add up to: its opinion of each person and its mood.
 * Personality shapes memories as they're made: vengeful NPCs hold bad memories longer, sentimental ones good ones,
 * proud ones feel slights twice as hard, and loyal ones go easy on friends.
 */
public class NpcMemories implements NpcPart {
	private final List<Memory> memories = new ArrayList<>();

	/**
	 * Remembers something. Scale multiplies the memory's effects (e.g. by how much a gifted food is liked);
	 * a memory of a type about the same person beyond its stack limit replaces the oldest.
	 *
	 * @param loyalBond whether the subject is someone a loyal NPC would forgive (a friend or faction member)
	 */
	public void add(MemoryType type, @Nullable UUID subject, String subjectName, float scale, NpcPersonality personality,
		boolean loyalBond, long now) {
		this.prune(now);
		float days = type.days;
		boolean negative = type.opinion < 0 || type.opinion == 0 && type.mood < 0;
		if (negative) {
			// Vengeful (-1) holds grudges twice as long, forgiving (+1) half as long.
			days *= (float) Math.pow(2.0, -personality.get(TraitAxis.MERCY));
			if (loyalBond && personality.has(Quirk.LOYAL)) {
				scale *= 0.5F;
			}
		} else if (personality.has(Quirk.SENTIMENTAL)) {
			days *= 1.5F;
		}

		if (type.pride && personality.has(Quirk.PROUD)) {
			scale *= 2.0F;
		}

		List<Memory> same = this.matching(type, subject);
		if (same.size() >= type.stackLimit) {
			this.memories.remove(same.get(same.size() - 1));
		}

		long expires = now + (long) (days * Memory.TICKS_PER_DAY);
		this.memories.add(new Memory(type, subject, subjectName, now, expires, scale));
	}

	/** Memories of this type about this subject (or of no one), newest first. */
	private List<Memory> matching(MemoryType type, @Nullable UUID subject) {
		List<Memory> result = new ArrayList<>();
		for (Memory memory : this.memories) {
			if (memory.type() == type && (subject == null ? memory.subject() == null : memory.isAbout(subject))) {
				result.add(memory);
			}
		}

		result.sort(Comparator.comparingLong(Memory::created).reversed());
		return result;
	}

	public void prune(long now) {
		this.memories.removeIf(memory -> memory.strength(now) <= 0.0F);
	}

	public void forget(UUID subject) {
		this.memories.removeIf(memory -> memory.isAbout(subject));
	}

	public void clear() {
		this.memories.clear();
	}

	public List<Memory> all() {
		return Collections.unmodifiableList(this.memories);
	}

	public boolean remembers(MemoryType type, UUID subject) {
		for (Memory memory : this.memories) {
			if (memory.type() == type && memory.isAbout(subject)) {
				return true;
			}
		}

		return false;
	}

	/** What memories add to the opinion of this person, by memory type (for explaining it). */
	public Map<MemoryType, Float> opinionParts(UUID subject, long now) {
		Map<MemoryType, List<Memory>> byType = new LinkedHashMap<>();
		for (Memory memory : this.memories) {
			if (memory.isAbout(subject) && memory.type().isAboutSomeone()) {
				byType.computeIfAbsent(memory.type(), type -> new ArrayList<>()).add(memory);
			}
		}

		Map<MemoryType, Float> parts = new LinkedHashMap<>();
		for (Map.Entry<MemoryType, List<Memory>> entry : byType.entrySet()) {
			parts.put(entry.getKey(), stacked(entry.getValue(), now, true));
		}

		return parts;
	}

	public float opinionOf(UUID subject, long now) {
		float total = 0.0F;
		for (float part : this.opinionParts(subject, now).values()) {
			total += part;
		}

		return total;
	}

	/**
	 * The NPC's mood, -100 to +100: the mood effects of everything it remembers. Pessimists feel bad things more
	 * and good things less, optimists the reverse; zen NPCs feel everything half as much.
	 */
	public float mood(NpcPersonality personality, long now) {
		Map<String, List<Memory>> groups = new LinkedHashMap<>();
		for (Memory memory : this.memories) {
			if (memory.type().mood != 0) {
				groups.computeIfAbsent(memory.type().id() + "|" + memory.subject(), key -> new ArrayList<>()).add(memory);
			}
		}

		float outlook = personality.get(TraitAxis.OUTLOOK);
		float mood = 0.0F;
		for (List<Memory> group : groups.values()) {
			float effect = stacked(group, now, false);
			effect *= effect < 0.0F ? 1.0F - 0.5F * outlook : 1.0F + 0.5F * outlook;
			mood += effect;
		}

		if (personality.has(Quirk.ZEN)) {
			mood *= 0.5F;
		}

		return Math.max(-100.0F, Math.min(100.0F, mood));
	}

	/** The mood memories, for explaining the mood. */
	public List<Memory> moodMemories(long now) {
		List<Memory> result = new ArrayList<>();
		for (Memory memory : this.memories) {
			if (memory.type().mood != 0 && memory.strength(now) > 0.0F) {
				result.add(memory);
			}
		}

		return result;
	}

	/** Sums repeats of one memory, newest strongest, each repeat counting a share (the type's falloff) of the one before. */
	private static float stacked(List<Memory> group, long now, boolean opinion) {
		group.sort(Comparator.comparingLong(Memory::created).reversed());
		float total = 0.0F;
		float weight = 1.0F;
		for (Memory memory : group) {
			int base = opinion ? memory.type().opinion : memory.type().mood;
			total += base * memory.scale() * memory.strength(now) * weight;
			weight *= memory.type().falloff;
		}

		return total;
	}

	private ListTag toTag() {
		ListTag list = new ListTag();
		for (Memory memory : this.memories) {
			CompoundTag tag = new CompoundTag();
			tag.putString("Type", memory.type().id());
			if (memory.subject() != null) {
				tag.putString("Subject", memory.subject().toString());
			}

			tag.putString("Name", memory.subjectName());
			tag.putLong("Created", memory.created());
			tag.putLong("Expires", memory.expires());
			tag.putFloat("Scale", memory.scale());
			list.addTag(tag);
		}

		return list;
	}

	private void fromTag(ListTag list) {
		this.memories.clear();
		for (Tag<?> entry : list) {
			if (!(entry instanceof CompoundTag tag)) {
				continue;
			}

			MemoryType type = MemoryType.byName(tag.getString("Type"));
			if (type == null) {
				continue;
			}

			UUID subject = null;
			if (tag.containsKey("Subject")) {
				try {
					subject = UUID.fromString(tag.getString("Subject"));
				} catch (IllegalArgumentException ignored) {
					continue;
				}
			}

			this.memories.add(new Memory(type, subject, tag.getString("Name"), tag.getLong("Created"), tag.getLong("Expires"),
				tag.getFloatOrDefault("Scale", 1.0F)));
		}
	}

	/** Saved in the NPC's compound under "Memories". */
	@Override
	public void save(CompoundTag tag) {
		tag.putList("Memories", this.toTag());
	}

	@Override
	public void load(CompoundTag tag) {
		if (tag.containsKey("Memories")) {
			this.fromTag(tag.getList("Memories"));
		}
	}
}
