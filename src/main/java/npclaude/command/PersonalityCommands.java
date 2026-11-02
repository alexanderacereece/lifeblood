package npclaude.command;

import static npclaude.command.CommandSupport.*;

import com.mojang.brigadier.arguments.ArgumentTypeInteger;
import com.mojang.brigadier.arguments.ArgumentTypeString;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.net.command.CommandSource;
import npclaude.dialogue.SocialLimits;
import npclaude.entity.EntityNPC;
import npclaude.personality.NpcPersonality;
import npclaude.personality.Quirk;
import npclaude.personality.Surroundings;
import npclaude.personality.Tastes;
import npclaude.personality.TraitAxis;
import npclaude.personality.TraitTier;
import npclaude.social.Memory;
import npclaude.social.MemoryType;
import npclaude.social.Opinions;
import npclaude.speech.SpeechFacts;
import npclaude.speech.SpeechLibrary;

/** /npc subcommands: personality, tastes, opinions, mood, memories and speech. */
final class PersonalityCommands {
	static void addTo(ArgumentBuilderLiteral<CommandSource> root) {
		root
			.then(literal("personality")
				.then(npcArgument()
					.executes(PersonalityCommands::showPersonality)
					.then(literal("randomize").executes(c -> {
						EntityNPC npc = findNpc(c);
						npc.personality.randomize(npc.getRandom());
						reply(c, npc.nickname + " is someone new.");
						return showPersonality(c);
					}))
					.then(argument("axis", ArgumentTypeString.word()).suggests(AXIS_NAMES)
						.then(argument("value", ArgumentTypeInteger.integer(-100, 100)).executes(c -> {
							EntityNPC npc = findNpc(c);
							String name = ArgumentTypeString.getString(c, "axis");
							TraitAxis axis = TraitAxis.byName(name);
							if (axis == null) {
								throw error("Unknown trait '" + name + "'.");
							}

							int value = c.getArgument("value", Integer.class);
							npc.personality.set(axis, value / 100.0F);
							return reply(c, npc.nickname + "'s " + axis.displayName() + " is now " + describeAxis(npc.personality, axis) + ".");
						})))))
			.then(literal("quirk")
				.then(npcArgument()
					.then(literal("add")
						.then(argument("quirk", ArgumentTypeString.word()).suggests(QUIRK_NAMES).executes(c -> {
							EntityNPC npc = findNpc(c);
							Quirk quirk = requireQuirk(c);
							if (npc.personality.has(quirk)) {
								throw error(npc.nickname + " is already " + quirk.displayName() + ".");
							}

							if (!npc.personality.addQuirk(quirk)) {
								throw error(npc.personality.getQuirks().size() >= NpcPersonality.MAX_QUIRKS
									? npc.nickname + " already has " + NpcPersonality.MAX_QUIRKS + " quirks."
									: quirk.displayName() + " clashes with " + quirk.getOpposite().displayName() + ".");
							}

							return reply(c, npc.nickname + " is now " + quirk.displayName() + ".");
						})))
					.then(literal("remove")
						.then(argument("quirk", ArgumentTypeString.word()).suggests(QUIRK_NAMES).executes(c -> {
							EntityNPC npc = findNpc(c);
							Quirk quirk = requireQuirk(c);
							if (!npc.personality.removeQuirk(quirk)) {
								throw error(npc.nickname + " isn't " + quirk.displayName() + ".");
							}

							return reply(c, npc.nickname + " is no longer " + quirk.displayName() + ".");
						})))))
			.then(literal("likes")
				.then(npcArgument()
					.executes(PersonalityCommands::showLikes)
					.then(literal("reveal").executes(c -> {
						EntityNPC npc = findNpc(c);
						npc.voice.revealAll();
						return reply(c, npc.nickname + "'s loves and hates now show on their profile.");
					}))))
			.then(literal("like")
				.then(npcArgument()
					.executes(c -> {
						EntityNPC npc = findNpc(c);
						Item item = requireHeldItem(c);
						return reply(c, npc.nickname + " " + describeLike(npc.personality.tastes.like(item)) + " " + itemName(item)
							+ " (" + Math.round(npc.personality.tastes.like(item) * 100) + ").");
					})
					.then(argument("like", ArgumentTypeInteger.integer(-100, 100)).executes(c -> {
						EntityNPC npc = findNpc(c);
						Item item = requireHeldItem(c);
						npc.personality.tastes.set(item, c.getArgument("like", Integer.class) / 100.0F);
						return reply(c, npc.nickname + " now " + describeLike(npc.personality.tastes.like(item)) + " " + itemName(item) + ".");
					}))
					.then(surroundingsLike(Surroundings.Kind.WEATHER))
					.then(surroundingsLike(Surroundings.Kind.BIOME))
					.then(surroundingsLike(Surroundings.Kind.SEASON))))
			.then(literal("opinion")
				.then(npcArgument()
					.executes(c -> showOpinion(c, requirePlayer(c.getSource())))
					.then(argument("target", ArgumentTypeString.string()).suggests(TARGET_NAMES)
						.executes(c -> showOpinion(c, findTarget(c))))))
			.then(literal("mood")
				.then(npcArgument().executes(PersonalityCommands::showMood)))
			.then(literal("remember")
				.then(npcArgument()
					.then(argument("memory", ArgumentTypeString.word()).suggests(MEMORY_NAMES)
						.executes(c -> addMemory(c, requirePlayer(c.getSource())))
						.then(argument("target", ArgumentTypeString.string()).suggests(TARGET_NAMES)
							.executes(c -> addMemory(c, findTarget(c)))))))
			.then(literal("forget")
				.then(npcArgument()
					.executes(c -> {
						EntityNPC npc = findNpc(c);
						npc.memories.clear();
						return reply(c, npc.nickname + " has forgotten everything.");
					})
					.then(argument("target", ArgumentTypeString.string()).suggests(TARGET_NAMES).executes(c -> {
						EntityNPC npc = findNpc(c);
						Entity target = findTarget(c);
						UUID id = Opinions.idOf(target);
						if (id != null) {
							npc.memories.forget(id);
						}

						return reply(c, npc.nickname + " has forgotten everything about " + nameOf(target) + ".");
					}))))
			.then(literal("say")
				.then(npcArgument()
					.then(argument("symbol", ArgumentTypeString.word()).executes(c -> {
						EntityNPC npc = findNpc(c);
						String symbol = ArgumentTypeString.getString(c, "symbol");
						String line = npc.voice.line(symbol, c.getSource().getSender(), null);
						if (line == null) {
							throw error(npc.nickname + " has nothing to say for '" + symbol + "' right now.");
						}

						npc.voice.sayAloud(line);
						return 1;
					}))))
			.then(literal("limits")
				.executes(c -> reply(c, "Gift and chat limits are " + (SocialLimits.isEnabled() ? "on" : "off") + "."))
				.then(literal("on").executes(c -> {
					SocialLimits.setEnabled(true);
					return reply(c, "Gift and chat limits are on: one gift per NPC a day, five chats a minute.");
				}))
				.then(literal("off").executes(c -> {
					SocialLimits.setEnabled(false);
					return reply(c, "Gift and chat limits are off (until you turn them back on or restart).");
				})))
			.then(literal("speech")
				.then(literal("reload").executes(c -> reply(c, "Loaded " + SpeechLibrary.reload() + " speech lines."))));
	}

	private static int showPersonality(CommandContext<CommandSource> c) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		NpcPersonality personality = npc.personality;
		reply(c, npc.nickname + "'s personality:");
		for (TraitAxis axis : TraitAxis.values()) {
			reply(c, String.format(Locale.ROOT, "- %s (%s / %s): %s", axis.displayName(), axis.negative, axis.positive,
				describeAxis(personality, axis)));
		}

		List<String> quirks = new ArrayList<>();
		for (Quirk quirk : personality.getQuirks()) {
			quirks.add(quirk.displayName());
		}

		reply(c, "- quirks: " + (quirks.isEmpty() ? "none" : String.join(", ", quirks)));
		Item favorite = personality.tastes.favoriteFood();
		Item hated = personality.tastes.hatedFood();
		if (favorite != null && hated != null) {
			reply(c, "- favorite food: " + itemName(favorite) + "; hates " + itemName(hated));
		}

		Item favoriteThing = personality.tastes.favoriteThing();
		Item hatedThing = personality.tastes.hatedThing();
		reply(c, "- loves " + (favoriteThing != null ? itemName(favoriteThing) : "nothing in particular") + "; hates "
			+ (hatedThing != null ? itemName(hatedThing) : "nothing in particular"));

		return 1;
	}

	/** E.g. "72, strongly brave", "-40, a little timid" or "5, neither". */
	private static String describeAxis(NpcPersonality personality, TraitAxis axis) {
		float value = personality.get(axis);
		String pole = axis.poleOf(value);
		String amount = pole == null ? "neither" : (personality.tier(axis) == TraitTier.STRONG ? "strongly " : "a little ") + pole;
		return Math.round(value * 100) + ", " + amount;
	}

	private static int showLikes(CommandContext<CommandSource> c) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		Tastes tastes = npc.personality.tastes;
		List<Item> foods = tastes.rankedFoods();
		List<Item> things = tastes.rankedThings();
		reply(c, npc.nickname + "'s tastes:");
		reply(c, "- foods: loves " + summarize(npc, foods.subList(0, Math.min(3, foods.size()))) + "; hates "
			+ summarize(npc, foods.subList(Math.max(0, foods.size() - 2), foods.size())));
		reply(c, "- things: loves " + summarize(npc, things.subList(0, Math.min(4, things.size()))) + "; hates "
			+ summarize(npc, things.subList(Math.max(0, things.size() - 3), things.size())));
		Item favorite = tastes.favoriteThing();
		Item hated = tastes.hatedThing();
		reply(c, "- most their own: " + (favorite != null ? summarize(npc, List.of(favorite)) : "nothing") + " / "
			+ (hated != null ? summarize(npc, List.of(hated)) : "nothing"));
		for (Surroundings.Kind kind : Surroundings.Kind.values()) {
			List<String> parts = new ArrayList<>();
			for (String key : Surroundings.table(kind).keySet()) {
				parts.add(key + " " + Math.round(tastes.like(kind, key) * 100));
			}

			reply(c, "- " + kind.id() + ": " + String.join(", ", parts));
		}

		Player player = c.getSource().getSender();
		ItemStack held = player != null ? player.inventory.getCurrentItem() : null;
		if (held != null) {
			reply(c, npc.nickname + " " + describeLike(tastes.like(held.getItem())) + " " + itemName(held.getItem()) + ".");
		}

		return 1;
	}

	private static ArgumentBuilderLiteral<CommandSource> surroundingsLike(Surroundings.Kind kind) {
		SuggestionProvider<CommandSource> keys = (c, builder) -> {
			for (String key : Surroundings.table(kind).keySet()) {
				builder.suggest(key);
			}

			return builder.buildFuture();
		};
		return literal(kind.id())
			.then(argument("key", ArgumentTypeString.word()).suggests(keys)
				.executes(c -> {
					EntityNPC npc = findNpc(c);
					String key = ArgumentTypeString.getString(c, "key");
					float like = npc.personality.tastes.like(kind, key);
					return reply(c, npc.nickname + " " + describeLike(like) + " " + Surroundings.displayName(key) + " (" + Math.round(like * 100) + ").");
				})
				.then(argument("like", ArgumentTypeInteger.integer(-100, 100)).executes(c -> {
					EntityNPC npc = findNpc(c);
					String key = ArgumentTypeString.getString(c, "key");
					npc.personality.tastes.set(kind, key, c.getArgument("like", Integer.class) / 100.0F);
					return reply(c, npc.nickname + " now " + describeLike(npc.personality.tastes.like(kind, key)) + " " + Surroundings.displayName(key) + ".");
				})));
	}

	private static String summarize(EntityNPC npc, List<Item> items) {
		List<String> parts = new ArrayList<>();
		for (Item item : items) {
			parts.add(itemName(item) + " (" + Math.round(npc.personality.tastes.like(item) * 100) + ")");
		}

		return String.join(", ", parts);
	}

	private static int showOpinion(CommandContext<CommandSource> c, Entity target) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		if (target == npc) {
			throw error(npc.nickname + " doesn't keep an opinion of itself.");
		}

		Opinions.Opinion opinion = Opinions.explain(npc, target);
		reply(c, String.format(Locale.ROOT, "%s's opinion of %s: %d (%s)", npc.nickname, nameOf(target), Math.round(opinion.value()),
			opinion.label().displayName()));
		for (Map.Entry<String, Float> part : opinion.parts().entrySet()) {
			reply(c, String.format(Locale.ROOT, "- %s: %+d", part.getKey(), Math.round(part.getValue())));
		}

		return 1;
	}

	private static int showMood(CommandContext<CommandSource> c) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		long now = c.getSource().getWorld().getWorldTime();
		float mood = npc.memories.mood(npc.personality, now);
		reply(c, String.format(Locale.ROOT, "%s's mood: %d (%s)", npc.nickname, Math.round(mood), SpeechFacts.moodLabel(mood)));
		for (Memory memory : npc.memories.moodMemories(now)) {
			String about = memory.subjectName().isEmpty() ? "" : " (" + memory.subjectName() + ")";
			float effect = memory.type().mood * memory.scale() * memory.strength(now);
			long hoursLeft = (memory.expires() - now) / 1000L;
			reply(c, String.format(Locale.ROOT, "- %s%s: %+d, %d more hours", memory.type().description, about, Math.round(effect), hoursLeft));
		}

		return 1;
	}

	private static int addMemory(CommandContext<CommandSource> c, Entity target) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		String name = ArgumentTypeString.getString(c, "memory");
		MemoryType type = MemoryType.byName(name);
		if (type == null) {
			throw error("Unknown memory '" + name + "'.");
		}

		if (type.isAboutSomeone() && Opinions.idOf(target) == null) {
			throw error("That memory needs a person to be about.");
		}

		npc.remember(type, type.isAboutSomeone() ? target : null, 1.0F);
		return reply(c, npc.nickname + " remembers: " + type.description + (type.isAboutSomeone() ? " (" + nameOf(target) + ")" : "") + ".");
	}

	private PersonalityCommands() {
	}
}
