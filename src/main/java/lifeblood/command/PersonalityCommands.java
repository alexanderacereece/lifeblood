package lifeblood.command;

import static lifeblood.command.CommandSupport.*;
import static lifeblood.command.CommandTree.branch;

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
import lifeblood.entity.EntityNPC;
import lifeblood.faction.FactionRegistry;
import lifeblood.faction.FactionRelation;
import lifeblood.faction.NpcFactions;
import lifeblood.personality.NpcPersonality;
import lifeblood.personality.Quirk;
import lifeblood.personality.Surroundings;
import lifeblood.personality.Tastes;
import lifeblood.personality.TraitAxis;
import lifeblood.personality.TraitTier;
import lifeblood.social.Memory;
import lifeblood.social.MemoryType;
import lifeblood.social.Opinions;
import lifeblood.speech.SpeechFacts;

/**
 * Who NPCs are and how they feel:
 * - /lb personality: trait axes and quirks;
 * - /lb tastes: loves and hates (foods, things, weather, places, seasons);
 * - /lb social: opinions, mood, memories, and how an NPC treats someone.
 */
final class PersonalityCommands {
	private PersonalityCommands() {
	}

	static void addTo(ArgumentBuilderLiteral<CommandSource> root) {
		root.then(branch("personality", "Trait axes and quirks.")
			.then(branch("personality show", "An NPC's trait axes, quirks and strongest tastes.")
				.then(npcArgument("npc").executes(PersonalityCommands::showPersonality)))
			.then(branch("personality randomize", "Rolls NPCs a whole new personality.")
				.then(npcsArgument("npcs").executes(c -> {
					List<EntityNPC> npcs = getNpcs(c, "npcs");
					npcs.forEach(npc -> npc.personality.randomize(npc.getRandom()));
					return replyEach(c, npcs, name -> name + " is someone new.", "%d NPCs are someone new.");
				})))
			.then(branch("personality set", "Sets a trait axis from -100 (the first pole) to 100 (the second), e.g. courage 80.")
				.then(npcsArgument("npcs")
					.then(argument("axis", ArgumentTypeString.word()).suggests(AXIS_NAMES)
						.then(argument("value", ArgumentTypeInteger.integer(-100, 100)).executes(c -> {
							String name = ArgumentTypeString.getString(c, "axis");
							TraitAxis axis = TraitAxis.byName(name);
							if (axis == null) {
								throw error("Unknown trait '" + name + "'.");
							}

							int value = c.getArgument("value", Integer.class);
							List<EntityNPC> npcs = getNpcs(c, "npcs");
							for (EntityNPC npc : npcs) {
								npc.personality.set(axis, value / 100.0F);
							}

							return replyEach(c, npcs, npc -> npc + "'s " + axis.displayName() + " is now " + value + ".",
								"%d NPCs' " + axis.displayName() + " is now " + value + ".");
						})))))
			.then(branch("personality quirk", "Gives or takes away quirks.")
				.then(branch("personality quirk add", "Gives NPCs a quirk, if it doesn't clash with one they have.")
					.then(npcsArgument("npcs")
						.then(argument("quirk", ArgumentTypeString.word()).suggests(QUIRK_NAMES).executes(c -> {
							Quirk quirk = requireQuirk(c);
							int changed = 0;
							for (EntityNPC npc : getNpcs(c, "npcs")) {
								if (npc.personality.has(quirk)) {
									reply(c, npc.nickname + " is already " + quirk.displayName() + ".");
								} else if (!npc.personality.addQuirk(quirk)) {
									reply(c, npc.personality.getQuirks().size() >= NpcPersonality.MAX_QUIRKS
										? npc.nickname + " already has " + NpcPersonality.MAX_QUIRKS + " quirks."
										: quirk.displayName() + " clashes with " + npc.nickname + "'s " + quirk.getOpposite().displayName() + ".");
								} else {
									reply(c, npc.nickname + " is now " + quirk.displayName() + ".");
									changed++;
								}
							}

							return changed;
						}))))
				.then(branch("personality quirk remove", "Takes a quirk away from NPCs.")
					.then(npcsArgument("npcs")
						.then(argument("quirk", ArgumentTypeString.word()).suggests(QUIRK_NAMES).executes(c -> {
							Quirk quirk = requireQuirk(c);
							int changed = 0;
							for (EntityNPC npc : getNpcs(c, "npcs")) {
								if (npc.personality.removeQuirk(quirk)) {
									reply(c, npc.nickname + " is no longer " + quirk.displayName() + ".");
									changed++;
								} else {
									reply(c, npc.nickname + " isn't " + quirk.displayName() + ".");
								}
							}

							return changed;
						}))))));

		root.then(branch("tastes", "What NPCs love and hate.")
			.then(branch("tastes show", "An NPC's loves and hates in full, and what it thinks of what you're holding.")
				.then(npcArgument("npc").executes(PersonalityCommands::showTastes)))
			.then(branch("tastes reveal", "Shows NPCs' loves and hates on their profiles without having to hear about them.")
				.then(npcsArgument("npcs").executes(c -> {
					List<EntityNPC> npcs = getNpcs(c, "npcs");
					npcs.forEach(npc -> npc.voice.revealAll());
					return replyEach(c, npcs, name -> name + "'s loves and hates now show on their profile.", "%d NPCs' loves and hates now show.");
				})))
			.then(branch("tastes item", "Shows or sets, -100 to 100, what an NPC thinks of the item you're holding.")
				.then(npcArgument("npc")
					.executes(c -> {
						EntityNPC npc = getNpc(c, "npc");
						Item item = requireHeldItem(c);
						float like = npc.personality.tastes.like(item);
						return reply(c, npc.nickname + " " + describeLike(like) + " " + itemName(item) + " (" + Math.round(like * 100) + ").");
					})
					.then(argument("value", ArgumentTypeInteger.integer(-100, 100)).executes(c -> {
						EntityNPC npc = getNpc(c, "npc");
						Item item = requireHeldItem(c);
						npc.personality.tastes.set(item, c.getArgument("value", Integer.class) / 100.0F);
						return reply(c, npc.nickname + " now " + describeLike(npc.personality.tastes.like(item)) + " " + itemName(item) + ".");
					}))))
			.then(surroundingsTaste(Surroundings.Kind.WEATHER))
			.then(surroundingsTaste(Surroundings.Kind.BIOME))
			.then(surroundingsTaste(Surroundings.Kind.SEASON)));

		root.then(branch("social", "Opinions, mood, memories, and how NPCs treat people.")
			.then(branch("social opinion", "What an NPC thinks of someone (you, if no one is given), and why.")
				.then(npcArgument("npc")
					.executes(c -> showOpinion(c, requirePlayer(c.getSource())))
					.then(personArgument("about").executes(c -> showOpinion(c, getPerson(c, "about"))))))
			.then(branch("social mood", "An NPC's mood and the memories behind it.")
				.then(npcArgument("npc").executes(PersonalityCommands::showMood)))
			.then(branch("social remember", "Gives NPCs a memory, about someone (you, if no one is given) if it's that kind.")
				.then(npcsArgument("npcs")
					.then(argument("memory", ArgumentTypeString.word()).suggests(MEMORY_NAMES)
						.executes(c -> addMemory(c, requirePlayer(c.getSource())))
						.then(personArgument("about").executes(c -> addMemory(c, getPerson(c, "about")))))))
			.then(branch("social forget", "Wipes NPCs' memories, or only those about someone.")
				.then(npcsArgument("npcs")
					.executes(c -> {
						List<EntityNPC> npcs = getNpcs(c, "npcs");
						npcs.forEach(npc -> npc.memories.clear());
						return replyEach(c, npcs, name -> name + " has forgotten everything.", "%d NPCs have forgotten everything.");
					})
					.then(personArgument("about").executes(c -> {
						Entity about = getPerson(c, "about");
						UUID id = Opinions.idOf(about);
						List<EntityNPC> npcs = getNpcs(c, "npcs");
						if (id != null) {
							npcs.forEach(npc -> npc.memories.forget(id));
						}

						return replyEach(c, npcs, name -> name + " has forgotten everything about " + nameOf(about) + ".",
							"%d NPCs have forgotten everything about " + nameOf(about) + ".");
					}))))
			.then(branch("social stance", "Shows or forces how NPCs treat a player: ally, neutral, hostile, or default to undo.")
				.then(npcsArgument("npcs")
					.then(personArgument("player")
						.executes(PersonalityCommands::showStance)
						.then(argument("stance", ArgumentTypeString.word()).suggests(RELATION_OR_DEFAULT_NAMES).executes(c -> {
							if (!(getPerson(c, "player") instanceof Player player)) {
								throw error("Stances are toward players; between NPCs it's their opinions and factions.");
							}

							FactionRelation relation = requireRelationOrDefault(c, "stance");
							List<EntityNPC> npcs = getNpcs(c, "npcs");
							npcs.forEach(npc -> npc.relations.set(player, relation));
							String label = relation == null ? "back to treating " + player.username + " by faction and opinion"
								: describe(relation) + " toward " + player.username;
							return replyEach(c, npcs, npc -> npc + " is now " + label + ".", "%d NPCs are now " + label + ".");
						}))))));
	}

	private static int showPersonality(CommandContext<CommandSource> c) throws CommandSyntaxException {
		EntityNPC npc = getNpc(c, "npc");
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

	private static int showTastes(CommandContext<CommandSource> c) throws CommandSyntaxException {
		EntityNPC npc = getNpc(c, "npc");
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

	/** /lb tastes weather|biome|season &lt;npc&gt; &lt;key&gt; [value]. */
	private static ArgumentBuilderLiteral<CommandSource> surroundingsTaste(Surroundings.Kind kind) {
		SuggestionProvider<CommandSource> keys = (c, builder) -> {
			for (String key : Surroundings.table(kind).keySet()) {
				builder.suggest(key);
			}

			return builder.buildFuture();
		};
		return branch("tastes " + kind.id(), "Shows or sets, -100 to 100, what an NPC thinks of a " + kind.id() + ".")
			.then(npcArgument("npc")
				.then(argument("key", ArgumentTypeString.word()).suggests(keys)
					.executes(c -> {
						EntityNPC npc = getNpc(c, "npc");
						String key = ArgumentTypeString.getString(c, "key");
						float like = npc.personality.tastes.like(kind, key);
						return reply(c, npc.nickname + " " + describeLike(like) + " " + Surroundings.displayName(key) + " (" + Math.round(like * 100) + ").");
					})
					.then(argument("value", ArgumentTypeInteger.integer(-100, 100)).executes(c -> {
						EntityNPC npc = getNpc(c, "npc");
						String key = ArgumentTypeString.getString(c, "key");
						npc.personality.tastes.set(kind, key, c.getArgument("value", Integer.class) / 100.0F);
						return reply(c, npc.nickname + " now " + describeLike(npc.personality.tastes.like(kind, key)) + " " + Surroundings.displayName(key) + ".");
					}))));
	}

	private static String summarize(EntityNPC npc, List<Item> items) {
		List<String> parts = new ArrayList<>();
		for (Item item : items) {
			parts.add(itemName(item) + " (" + Math.round(npc.personality.tastes.like(item) * 100) + ")");
		}

		return String.join(", ", parts);
	}

	private static int showOpinion(CommandContext<CommandSource> c, Entity about) throws CommandSyntaxException {
		EntityNPC npc = getNpc(c, "npc");
		if (about == npc) {
			throw error(npc.nickname + " doesn't keep an opinion of itself.");
		}

		Opinions.Opinion opinion = Opinions.explain(npc, about);
		reply(c, String.format(Locale.ROOT, "%s's opinion of %s: %d (%s)", npc.nickname, nameOf(about), Math.round(opinion.value()),
			opinion.label().displayName()));
		for (Map.Entry<String, Float> part : opinion.parts().entrySet()) {
			reply(c, String.format(Locale.ROOT, "- %s: %+d", part.getKey(), Math.round(part.getValue())));
		}

		return 1;
	}

	private static int showMood(CommandContext<CommandSource> c) throws CommandSyntaxException {
		EntityNPC npc = getNpc(c, "npc");
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

	private static int addMemory(CommandContext<CommandSource> c, Entity about) throws CommandSyntaxException {
		String name = ArgumentTypeString.getString(c, "memory");
		MemoryType type = MemoryType.byName(name);
		if (type == null) {
			throw error("Unknown memory '" + name + "'.");
		}

		if (type.isAboutSomeone() && Opinions.idOf(about) == null) {
			throw error("That memory needs a person to be about.");
		}

		List<EntityNPC> npcs = getNpcs(c, "npcs");
		npcs.forEach(npc -> npc.remember(type, type.isAboutSomeone() ? about : null, 1.0F));
		String what = type.description + (type.isAboutSomeone() ? " (" + nameOf(about) + ")" : "");
		return replyEach(c, npcs, npc -> npc + " remembers: " + what + ".", "%d NPCs remember: " + what + ".");
	}

	private static int showStance(CommandContext<CommandSource> c) throws CommandSyntaxException {
		if (!(getPerson(c, "player") instanceof Player player)) {
			throw error("Stances are toward players; between NPCs it's their opinions and factions.");
		}

		List<EntityNPC> npcs = getNpcs(c, "npcs");
		for (EntityNPC npc : npcs) {
			showStance(c, npc, player);
		}

		return npcs.size();
	}

	private static void showStance(CommandContext<CommandSource> c, EntityNPC npc, Player player) {

		FactionRelation personal = npc.relations.get(player);
		FactionRelation overall = NpcFactions.regard(npc, player);
		float opinion = Opinions.of(npc, player);
		String member = npc.getFaction() != null ? "one of the " + npc.getFaction() : "someone unaffiliated";
		String reason;
		if (personal != null) {
			reason = "set by command";
		} else if (opinion >= Opinions.ALLY_THRESHOLD || opinion <= Opinions.hostileThreshold(npc)) {
			reason = "because its opinion of " + player.username + " is " + Math.round(opinion);
		} else {
			reason = "as " + member + ", toward someone " + factionLabel(FactionRegistry.get(c.getSource().getWorld()).getPlayerFaction(player.uuid));
		}

		reply(c, npc.nickname + " is " + describe(overall) + " toward " + player.username + " (" + reason + ").");
		if (overall == FactionRelation.HOSTILE && !player.getGamemode().hasHostileMobs()) {
			reply(c, player.username + " is in a game mode monsters ignore, so " + npc.nickname + " won't attack them either.");
		}
	}
}
