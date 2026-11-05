package lifeblood.gossip;

import com.mojang.nbt.tags.CompoundTag;
import com.mojang.nbt.tags.ListTag;
import com.mojang.nbt.tags.Tag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.world.World;
import net.minecraft.core.world.saveddata.SavedData;
import lifeblood.world.Calendar;
import org.jetbrains.annotations.Nullable;

/**
 * The news of one world and who has heard each piece, saved with the world (BTA SavedData). Kept here rather than in
 * each NPC so relatives far away or unloaded still hear of a death in the family. Holds the newest pieces only.
 */
public class GossipRegister extends SavedData {
	public static final String DATA_ID = "lifeblood_gossip";
	/** How many pieces of news the world keeps; the oldest are forgotten first. */
	static final int MAX_NEWS = 200;
	/** News stays news for a real month: a twelfth of an in-game year. */
	private static final int FRESH_PARTS_OF_A_YEAR = 12;

	/** How someone heard a piece of news: from whom (null if they were there, or family), and whether it grew in the telling. */
	public record Heard(@Nullable UUID from, String fromName, boolean exaggerated) {
		public boolean firsthand() {
			return this.from == null;
		}
	}

	private final Map<Integer, News> news = new LinkedHashMap<>();
	private final Map<Integer, Map<UUID, Heard>> heard = new HashMap<>();
	private int nextId = 1;

	/** Called by BTA through reflection when loading saved data. */
	public GossipRegister(String id) {
		super(id);
	}

	public static GossipRegister get(World world) {
		GossipRegister register = (GossipRegister) world.getSavedData(GossipRegister.class, DATA_ID);
		if (register == null) {
			register = new GossipRegister(DATA_ID);
			world.setSavedData(DATA_ID, register);
			register.setDirty();
		}

		return register;
	}

	/** How long news stays fresh in this world, in ticks. */
	public static long freshTicks(World world) {
		return Calendar.ticksPerYear(world) / FRESH_PARTS_OF_A_YEAR;
	}

	/**
	 * Records something that happened. The same thing again while it's still fresh (the same two people, the same
	 * kind) is the same news, brought up to date, so a long fight doesn't become fifty stories.
	 */
	public News report(NewsKind kind, UUID subject, String subjectName, @Nullable UUID other, String otherName, String babyName, long now,
		long freshTicks) {
		for (News existing : this.news.values()) {
			if (existing.kind() == kind && existing.subject().equals(subject) && Objects.equals(existing.other(), other)
				&& now - existing.when() < freshTicks) {
				News updated = new News(existing.id(), kind, subject, subjectName, other, otherName, babyName, now);
				this.news.put(existing.id(), updated);
				this.setDirty();
				return updated;
			}
		}

		News added = new News(this.nextId++, kind, subject, subjectName, other, otherName, babyName, now);
		this.news.put(added.id(), added);
		while (this.news.size() > MAX_NEWS) {
			int oldest = this.news.keySet().iterator().next();
			this.news.remove(oldest);
			this.heard.remove(oldest);
		}

		this.setDirty();
		return added;
	}

	/** Someone hears a piece of news; false if they already knew it. */
	public boolean hear(News piece, UUID person, @Nullable UUID from, String fromName, boolean exaggerated) {
		Map<UUID, Heard> knowers = this.heard.computeIfAbsent(piece.id(), id -> new HashMap<>());
		if (knowers.containsKey(person)) {
			return false;
		}

		knowers.put(person, new Heard(from, fromName, exaggerated));
		this.setDirty();
		return true;
	}

	/** How this person heard a piece of news, or null if they haven't. */
	@Nullable
	public Heard heard(News piece, UUID person) {
		return this.heard.getOrDefault(piece.id(), Map.of()).get(person);
	}

	/** Every piece of news, oldest first. */
	public List<News> all() {
		return new ArrayList<>(this.news.values());
	}

	@Override
	public void load(CompoundTag tag) {
		this.news.clear();
		this.heard.clear();
		this.nextId = Math.max(1, tag.getInteger("NextId"));
		for (Tag<?> entry : tag.getList("News")) {
			if (!(entry instanceof CompoundTag newsTag)) {
				continue;
			}

			NewsKind kind = NewsKind.byName(newsTag.getString("Kind"));
			if (kind == null) {
				continue;
			}

			try {
				int id = newsTag.getInteger("Id");
				String other = newsTag.getStringOrDefault("Other", "");
				this.news.put(id, new News(id, kind, UUID.fromString(newsTag.getString("Subject")), newsTag.getString("SubjectName"),
					other.isEmpty() ? null : UUID.fromString(other), newsTag.getString("OtherName"), newsTag.getString("BabyName"),
					newsTag.getLong("When")));
				Map<UUID, Heard> knowers = new HashMap<>();
				for (Tag<?> heardEntry : newsTag.getList("Heard")) {
					if (heardEntry instanceof CompoundTag heardTag) {
						String from = heardTag.getStringOrDefault("From", "");
						knowers.put(UUID.fromString(heardTag.getString("Who")), new Heard(from.isEmpty() ? null : UUID.fromString(from),
							heardTag.getString("FromName"), heardTag.getBoolean("Exaggerated")));
					}
				}

				this.heard.put(id, knowers);
			} catch (IllegalArgumentException e) {
				// A damaged entry is dropped rather than breaking the rest.
			}
		}
	}

	@Override
	public void save(CompoundTag tag) {
		tag.putInt("NextId", this.nextId);
		ListTag list = new ListTag();
		for (News piece : this.news.values()) {
			CompoundTag newsTag = new CompoundTag();
			newsTag.putInt("Id", piece.id());
			newsTag.putString("Kind", piece.kind().id());
			newsTag.putString("Subject", piece.subject().toString());
			newsTag.putString("SubjectName", piece.subjectName());
			newsTag.putString("Other", piece.other() != null ? piece.other().toString() : "");
			newsTag.putString("OtherName", piece.otherName());
			newsTag.putString("BabyName", piece.babyName());
			newsTag.putLong("When", piece.when());
			ListTag knowers = new ListTag();
			for (Map.Entry<UUID, Heard> entry : this.heard.getOrDefault(piece.id(), Map.of()).entrySet()) {
				CompoundTag heardTag = new CompoundTag();
				heardTag.putString("Who", entry.getKey().toString());
				heardTag.putString("From", entry.getValue().from() != null ? entry.getValue().from().toString() : "");
				heardTag.putString("FromName", entry.getValue().fromName());
				heardTag.putBoolean("Exaggerated", entry.getValue().exaggerated());
				knowers.addTag(heardTag);
			}

			newsTag.putList("Heard", knowers);
			list.addTag(newsTag);
		}

		tag.putList("News", list);
	}
}
