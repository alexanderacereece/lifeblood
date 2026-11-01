package npclaude.words;

import com.mojang.nbt.tags.CompoundTag;
import com.mojang.nbt.tags.ListTag;
import com.mojang.nbt.tags.Tag;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.world.World;
import net.minecraft.core.world.saveddata.SavedData;
import npclaude.speech.Word;
import org.jetbrains.annotations.Nullable;

/**
 * Every word players have taught NPCs, saved with the world. A word is personal (only the NPC it was taught to
 * uses it) or general (it spreads through friendly NPC conversations). Words never fade; they can only be deleted,
 * which removes them from everyone.
 */
public class WordBook extends SavedData {
	public static final String DATA_ID = "npclaude_words";

	public static final class TaughtWord {
		public final int id;
		public final WordCategory category;
		public final Word word;
		public final boolean personal;
		public final UUID teacher;
		public final String teacherName;
		/** Who knows it, and who each heard it from (the teacher's name for the first). */
		public final Map<UUID, String> knownBy = new LinkedHashMap<>();

		TaughtWord(int id, WordCategory category, Word word, boolean personal, UUID teacher, String teacherName) {
			this.id = id;
			this.category = category;
			this.word = word;
			this.personal = personal;
			this.teacher = teacher;
			this.teacherName = teacherName;
		}
	}

	private final Map<Integer, TaughtWord> words = new LinkedHashMap<>();
	private int nextId = 1;
	/** The world day each NPC last asked each player for a word, as "npc|player". */
	private final Map<String, Long> lastAsked = new HashMap<>();

	/** Called by BTA through reflection when loading saved data. */
	public WordBook(String id) {
		super(id);
	}

	public static WordBook get(World world) {
		WordBook book = (WordBook) world.getSavedData(WordBook.class, DATA_ID);
		if (book == null) {
			book = new WordBook(DATA_ID);
			world.setSavedData(DATA_ID, book);
		}

		return book;
	}

	public TaughtWord teach(UUID npc, WordCategory category, Word word, boolean personal, UUID teacher, String teacherName) {
		TaughtWord taught = new TaughtWord(this.nextId++, category, word, personal || category.alwaysPersonal(), teacher, teacherName);
		taught.knownBy.put(npc, teacherName);
		this.words.put(taught.id, taught);
		this.setDirty();
		return taught;
	}

	public Collection<TaughtWord> all() {
		return this.words.values();
	}

	@Nullable
	public TaughtWord find(int id) {
		return this.words.get(id);
	}

	public boolean delete(int id) {
		boolean removed = this.words.remove(id) != null;
		if (removed) {
			this.setDirty();
		}

		return removed;
	}

	public List<TaughtWord> knownBy(UUID npc) {
		List<TaughtWord> known = new ArrayList<>();
		for (TaughtWord word : this.words.values()) {
			if (word.knownBy.containsKey(npc)) {
				known.add(word);
			}
		}

		return known;
	}

	/** A random word of this category the NPC knows; nicknames only if taught by this player. */
	@Nullable
	public TaughtWord pick(UUID npc, WordCategory category, @Nullable UUID listener, Random random) {
		List<TaughtWord> options = new ArrayList<>();
		for (TaughtWord word : this.words.values()) {
			if (word.category == category && word.knownBy.containsKey(npc) && (category != WordCategory.NICKNAME || word.teacher.equals(listener))) {
				options.add(word);
			}
		}

		return options.isEmpty() ? null : options.get(random.nextInt(options.size()));
	}

	/** One NPC passes a general word on to another who doesn't know it yet; returns it, or null if there's none. */
	@Nullable
	public TaughtWord share(UUID from, String fromName, UUID to, Random random) {
		List<TaughtWord> options = new ArrayList<>();
		for (TaughtWord word : this.words.values()) {
			if (!word.personal && word.knownBy.containsKey(from) && !word.knownBy.containsKey(to)) {
				options.add(word);
			}
		}

		if (options.isEmpty()) {
			return null;
		}

		TaughtWord word = options.get(random.nextInt(options.size()));
		word.knownBy.put(to, fromName);
		this.setDirty();
		return word;
	}

	public long lastAsked(UUID npc, UUID player) {
		return this.lastAsked.getOrDefault(npc + "|" + player, -1L);
	}

	public void setLastAsked(UUID npc, UUID player, long day) {
		this.lastAsked.put(npc + "|" + player, day);
		this.setDirty();
	}

	@Override
	public void load(CompoundTag tag) {
		this.words.clear();
		this.lastAsked.clear();
		this.nextId = tag.getIntegerOrDefault("NextId", 1);
		for (Tag<?> entry : tag.getList("Words")) {
			if (!(entry instanceof CompoundTag wordTag)) {
				continue;
			}

			WordCategory category = WordCategory.byName(wordTag.getStringOrDefault("Category", ""));
			if (category == null) {
				continue;
			}

			try {
				Word.Kind kind = Word.Kind.valueOf(wordTag.getStringOrDefault("Kind", "ONE"));
				Word word = new Word(wordTag.getString("Singular"), wordTag.getString("Plural"), kind);
				TaughtWord taught = new TaughtWord(wordTag.getInteger("Id"), category, word, wordTag.getBoolean("Personal"),
					UUID.fromString(wordTag.getString("Teacher")), wordTag.getStringOrDefault("TeacherName", "someone"));
				CompoundTag known = wordTag.getCompound("KnownBy");
				for (String npc : known.getValue().keySet()) {
					taught.knownBy.put(UUID.fromString(npc), known.getString(npc));
				}

				this.words.put(taught.id, taught);
				this.nextId = Math.max(this.nextId, taught.id + 1);
			} catch (IllegalArgumentException ignored) {
			}
		}

		CompoundTag asked = tag.getCompound("LastAsked");
		for (String key : asked.getValue().keySet()) {
			this.lastAsked.put(key, asked.getLong(key));
		}
	}

	@Override
	public void save(CompoundTag tag) {
		tag.putInt("NextId", this.nextId);
		ListTag list = new ListTag();
		for (TaughtWord word : this.words.values()) {
			CompoundTag wordTag = new CompoundTag();
			wordTag.putInt("Id", word.id);
			wordTag.putString("Category", word.category.id());
			wordTag.putString("Singular", word.word.singular());
			wordTag.putString("Plural", word.word.plural());
			wordTag.putString("Kind", word.word.kind().name());
			wordTag.putBoolean("Personal", word.personal);
			wordTag.putString("Teacher", word.teacher.toString());
			wordTag.putString("TeacherName", word.teacherName);
			CompoundTag known = new CompoundTag();
			for (Map.Entry<UUID, String> entry : word.knownBy.entrySet()) {
				known.putString(entry.getKey().toString(), entry.getValue());
			}

			wordTag.putCompound("KnownBy", known);
			list.addTag(wordTag);
		}

		tag.putList("Words", list);
		CompoundTag asked = new CompoundTag();
		for (Map.Entry<String, Long> entry : this.lastAsked.entrySet()) {
			asked.putLong(entry.getKey(), entry.getValue());
		}

		tag.putCompound("LastAsked", asked);
	}
}
