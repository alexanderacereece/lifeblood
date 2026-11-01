package npclaude.speech;

import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.Item;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.pos.TilePos;
import net.minecraft.core.world.weather.Weather;
import npclaude.combat.FactionRegistry;
import npclaude.combat.FactionRelation;
import npclaude.combat.NpcFactions;
import npclaude.entity.EntityNPC;
import npclaude.family.FamilyRegistry;
import npclaude.family.Kinship;
import npclaude.words.WordBook;
import npclaude.words.WordCategory;
import npclaude.personality.Surroundings;
import npclaude.personality.Tastes;
import npclaude.social.Opinions;
import org.jetbrains.annotations.Nullable;

/**
 * Gathers the facts of the moment for a line: who's talking to whom and how they feel about them, the time and
 * weather, the speaker's mood and health, faction, tastes and voice. Speech files test these as conditions
 * ({time=night}) and fill them in ([listener], [favorite_food.a]).
 *
 * Facts: name, listener, listener_type (player/npc), opinion (enemy ... close_friend), relation (ally/neutral/hostile), leader (yes if the listener is
 * who they follow), time (morning/day/evening/night), weather (clear/rain/snow/storm/fog/blood_rain), wet (yes if
 * it's coming down on them), outside, season (spring/summer/fall/winter), biome (desert, tundra...), with
 * weather_like, season_like and biome_like (love/like/meh/dislike/hate), mood (awful/bad/okay/good/great),
 * health (fine/hurt/low), faction and enemy_faction (with has_faction/has_enemy = yes/no), recovering, address,
 * catchphrase, age (child/adult), birthday (yes on their birthday), relationship (single/dating/engaged/married/widowed), listener_kin (what the listener is to them,
 * or none), surprising_thing (yes if their favorite thing is one most people wouldn't care for).
 * Words: partner, favorite_food, hated_food, favorite_thing, hated_thing, weather_word, biome_word, season_word,
 * favorite_weather, hated_weather, favorite_biome, hated_biome, favorite_season, hated_season, and whatever the
 * event adds (food, item...).
 */
public final class SpeechFacts {
	private SpeechFacts() {
	}

	public static SpeechContext build(EntityNPC npc, @Nullable Entity listener) {
		long now = npc.world.getWorldTime();
		float mood = npc.memories.mood(npc.personality, now);
		SpeechContext context = new SpeechContext(npc, listener, mood);
		context.fact("name", npc.nickname);
		if (listener != null) {
			context.fact("listener", Opinions.nameOf(listener));
			context.fact("listener_type", listener instanceof Player ? "player" : "npc");
			if (listener instanceof Player || listener instanceof EntityNPC) {
				context.fact("opinion", Opinions.Label.of(Opinions.of(npc, listener)).id());
				context.fact("relation", NpcFactions.regard(npc, listener).name().toLowerCase(Locale.ROOT));
			}

			context.fact("leader", listener == npc.getLeader() ? "yes" : "no");
		}

		addFamily(npc, listener, context);
		addTaughtWords(npc, listener, context);
		context.fact("age", npc.isChild() ? "child" : "adult");
		context.fact("birthday", npc.isBirthday() ? "yes" : "no");
		context.fact("time", timeOfDay(npc));
		addSurroundings(npc, context);
		context.fact("mood", moodLabel(mood));
		float health = npc.getHealth() / (float) npc.getMaxHealth();
		context.fact("health", health < 0.3F ? "low" : health < 0.7F ? "hurt" : "fine");
		context.fact("recovering", npc.isRecovering() ? "yes" : "no");

		String faction = npc.getFaction();
		context.fact("has_faction", faction != null ? "yes" : "no");
		if (faction != null) {
			context.word("faction", Word.name(faction));
			String enemy = enemyFaction(npc, faction);
			if (enemy != null) {
				context.word("enemy_faction", Word.name(enemy));
			}

			context.fact("has_enemy", enemy != null ? "yes" : "no");
		} else {
			context.fact("has_enemy", "no");
		}

		Tastes tastes = npc.personality.tastes;
		Item favorite = tastes.favoriteFood();
		Item hated = tastes.hatedFood();
		if (favorite != null) {
			context.word("favorite_food", Word.ofFood(favorite));
		}

		if (hated != null) {
			context.word("hated_food", Word.ofFood(hated));
		}

		Item favoriteThing = tastes.favoriteThing();
		Item hatedThing = tastes.hatedThing();
		if (favoriteThing != null) {
			context.word("favorite_thing", Word.ofThing(favoriteThing));
			context.fact("surprising_thing", tastes.personal(favoriteThing) >= 0.6F ? "yes" : "no");
		}

		if (hatedThing != null) {
			context.word("hated_thing", Word.ofThing(hatedThing));
		}

		for (Surroundings.Kind kind : Surroundings.Kind.values()) {
			context.word("favorite_" + kind.id(), surroundingsWord(tastes.favorite(kind, true)));
			context.word("hated_" + kind.id(), surroundingsWord(tastes.favorite(kind, false)));
		}

		SpeechStyle style = npc.voice.style;
		if (!style.getAddress().isEmpty()) {
			context.fact("address", style.getAddress());
		}

		if (!style.getCatchphrase().isEmpty()) {
			context.fact("catchphrase", style.getCatchphrase());
		}

		return context;
	}

	/**
	 * relationship (single/dating/engaged/married/widowed) with the partner's name as [partner], and listener_kin:
	 * what the listener is to the speaker (spouse, partner, fiance, parent, child, sibling, cousin...) or "none".
	 */
	private static void addFamily(EntityNPC npc, @Nullable Entity listener, SpeechContext context) {
		FamilyRegistry family = FamilyRegistry.get(npc.world);
		FamilyRegistry.Partnership partnership = family.partnershipOf(npc.getUuid());
		FamilyRegistry.Person self = family.find(npc.getUuid());
		if (partnership != null) {
			context.fact("relationship", partnership.stage.name().toLowerCase(Locale.ROOT));
			context.word("partner", Word.name(family.nameOf(partnership.other(npc.getUuid()))));
		} else {
			context.fact("relationship", self != null && self.lateSpouse != null ? "widowed" : "single");
		}

		UUID listenerId = listener != null ? Opinions.idOf(listener) : null;
		Kinship kin = listenerId != null ? Kinship.of(family, npc.getUuid(), listenerId) : null;
		context.fact("listener_kin", kin != null ? kin.id() : "none");
	}

	/**
	 * Words players taught it, one of each category it knows, as [taught_food], [taught_greeting] and so on. A
	 * nickname the listener taught it replaces their name, about half the time.
	 */
	private static void addTaughtWords(EntityNPC npc, @Nullable Entity listener, SpeechContext context) {
		WordBook book = WordBook.get(npc.world);
		UUID listenerId = listener != null ? Opinions.idOf(listener) : null;
		for (WordCategory category : WordCategory.values()) {
			WordBook.TaughtWord word = book.pick(npc.getUuid(), category, listenerId, npc.getRandom());
			if (word == null) {
				continue;
			}

			if (category == WordCategory.NICKNAME) {
				if (npc.getRandom().nextBoolean()) {
					context.fact("listener", word.word.singular());
				}
			} else {
				context.word("taught_" + category.id(), word.word);
			}
		}
	}

	/** What the weather, biome and season are, and how the NPC feels about each. */
	private static void addSurroundings(EntityNPC npc, SpeechContext context) {
		Tastes tastes = npc.personality.tastes;
		double headY = npc.y + npc.getHeadHeight();
		String weather = Surroundings.weatherAt(npc.world, npc.x, headY, npc.z);
		boolean outside = npc.world.canBlockSeeSky(new TilePos(MathHelper.floor(npc.x), MathHelper.floor(headY), MathHelper.floor(npc.z)));
		context.fact("weather", weather);
		context.fact("weather_like", EntityNPC.tasteOf(tastes.like(Surroundings.Kind.WEATHER, weather)));
		context.word("weather_word", surroundingsWord(weather));
		context.fact("outside", outside ? "yes" : "no");
		context.fact("wet", isRaining(npc) && !weather.equals("clear") ? "yes" : "no");
		String biome = Surroundings.placeAt(npc.world, npc.x, npc.y, npc.z);
		if (biome != null) {
			context.fact("biome", biome);
			context.fact("biome_like", EntityNPC.tasteOf(tastes.like(Surroundings.Kind.BIOME, biome)));
			context.word("biome_word", surroundingsWord(biome));
		}

		String season = Surroundings.seasonOf(npc.world);
		if (season != null) {
			context.fact("season", season);
			context.fact("season_like", EntityNPC.tasteOf(tastes.like(Surroundings.Kind.SEASON, season)));
			context.word("season_word", surroundingsWord(season));
		}
	}

	/** "storm" reads as "storms" when talked about in general, "plains" is plural, "rain" can't be counted. */
	private static Word surroundingsWord(String key) {
		String name = Surroundings.displayName(key);
		if (Surroundings.isPluralName(key)) {
			return new Word(name, name, Word.Kind.PLURAL);
		}

		return key.equals("storm") ? Word.one(name) : Word.mass(name);
	}

	/** A faction this NPC's faction is hostile toward (an explicit war first, then anyone if it's hostile by default). */
	@Nullable
	private static String enemyFaction(EntityNPC npc, String faction) {
		FactionRegistry registry = FactionRegistry.get(npc.world);
		for (String other : registry.all()) {
			if (!other.equals(faction) && registry.regard(faction, other) == FactionRelation.HOSTILE) {
				return other;
			}
		}

		return null;
	}

	/** From the sun's position, so it works however long the world's days are. */
	private static String timeOfDay(EntityNPC npc) {
		float angle = npc.world.getCelestialAngle(1.0F);
		if (angle >= 0.70F && angle < 0.85F) {
			return "morning";
		} else if (angle >= 0.85F || angle < 0.20F) {
			return "day";
		} else if (angle < 0.30F) {
			return "evening";
		}

		return "night";
	}

	public static boolean isRaining(EntityNPC npc) {
		Weather weather = npc.world.getCurrentWeather();
		return weather != null && weather.isDamp()
			&& npc.world.isBlockBeingRainedOn(new TilePos(MathHelper.floor(npc.x), MathHelper.floor(npc.y + npc.getHeadHeight()), MathHelper.floor(npc.z)));
	}

	public static String moodLabel(float mood) {
		if (mood <= -40.0F) {
			return "awful";
		} else if (mood <= -10.0F) {
			return "bad";
		} else if (mood < 15.0F) {
			return "okay";
		}

		return mood < 40.0F ? "good" : "great";
	}
}
