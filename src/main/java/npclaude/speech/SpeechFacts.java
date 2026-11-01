package npclaude.speech;

import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.Item;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.pos.TilePos;
import net.minecraft.core.world.weather.Weather;
import npclaude.combat.FactionRegistry;
import npclaude.combat.FactionRelation;
import npclaude.entity.EntityNPC;
import npclaude.social.Opinions;
import org.jetbrains.annotations.Nullable;

/**
 * Gathers the facts of the moment for a line: who's talking to whom and how they feel about them, the time and
 * weather, the speaker's mood and health, faction, tastes and voice. Speech files test these as conditions
 * ({time=night}) and fill them in ([listener], [favorite_food.a]).
 *
 * Facts: name, listener, listener_type (player/npc), opinion (enemy ... close_friend), leader (yes if the listener is
 * who they follow), time (morning/day/evening/night), weather (rain/clear), mood (awful/bad/okay/good/great),
 * health (fine/hurt/low), faction and enemy_faction (with has_faction/has_enemy = yes/no), recovering, address,
 * catchphrase. Words: favorite_food, hated_food, and whatever the event adds (food, item...).
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
			}

			context.fact("leader", listener == npc.getLeader() ? "yes" : "no");
		}

		context.fact("time", timeOfDay(npc));
		context.fact("weather", isRaining(npc) ? "rain" : "clear");
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

		Item favorite = npc.personality.food.favorite();
		Item hated = npc.personality.food.hated();
		if (favorite != null) {
			context.word("favorite_food", Word.ofFood(favorite));
		}

		if (hated != null) {
			context.word("hated_food", Word.ofFood(hated));
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
