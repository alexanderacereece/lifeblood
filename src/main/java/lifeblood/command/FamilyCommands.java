package lifeblood.command;

import static lifeblood.command.CommandSupport.*;

import com.mojang.brigadier.arguments.ArgumentTypeInteger;
import com.mojang.brigadier.arguments.ArgumentTypeString;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.net.command.CommandSource;
import lifeblood.entity.EntityNPC;
import lifeblood.family.Children;
import lifeblood.family.Kinship;
import lifeblood.family.Romance;
import lifeblood.people.PeopleRegistry;
import lifeblood.words.WordBook;

/** /npc subcommands: families, couples, children growing up, and the words players have taught. */
final class FamilyCommands {
	static void addTo(ArgumentBuilderLiteral<CommandSource> root) {
		root
			.then(literal("family")
				.then(npcArgument()
					.executes(FamilyCommands::showFamily)
					.then(literal("parent")
						.then(argument("target", ArgumentTypeString.string()).suggests(TARGET_NAMES).executes(c -> linkParent(c, true))))
					.then(literal("child")
						.then(argument("target", ArgumentTypeString.string()).suggests(TARGET_NAMES).executes(c -> linkParent(c, false))))
					.then(literal("unlink")
						.then(argument("target", ArgumentTypeString.string()).suggests(TARGET_NAMES).executes(c -> {
							EntityNPC npc = findNpc(c);
							Entity target = findTarget(c);
							UUID id = requireId(target);
							PeopleRegistry family = PeopleRegistry.get(npc.world);
							family.removeParent(npc.getUuid(), id);
							family.removeParent(id, npc.getUuid());
							return reply(c, npc.nickname + " and " + nameOf(target) + " are no longer parent and child.");
						})))))
			.then(literal("couple")
				.then(npcArgument()
					.then(argument("target", ArgumentTypeString.string()).suggests(TARGET_NAMES)
						.then(argument("stage", ArgumentTypeString.word()).suggests((c, builder) -> {
							for (String stage : new String[] {"dating", "engaged", "married", "none"}) {
								builder.suggest(stage);
							}

							return builder.buildFuture();
						}).executes(FamilyCommands::setCouple)))))
			.then(literal("words")
				.executes(c -> {
					WordBook book = WordBook.get(c.getSource().getWorld());
					if (book.all().isEmpty()) {
						return reply(c, "Nobody has been taught any words yet.");
					}

					reply(c, "Taught words (/npc words delete <id> removes one from everyone):");
					for (WordBook.TaughtWord word : book.all()) {
						reply(c, String.format(Locale.ROOT, "- #%d %s (%s, %s), taught by %s, known by %d", word.id, word.word.singular(),
							word.category.id(), word.personal ? "personal" : "general", word.teacherName, word.knownBy.size()));
					}

					return 1;
				})
				.then(literal("delete")
					.then(argument("id", ArgumentTypeInteger.integer(1, Integer.MAX_VALUE)).executes(c -> {
						WordBook book = WordBook.get(c.getSource().getWorld());
						int id = c.getArgument("id", Integer.class);
						WordBook.TaughtWord word = book.find(id);
						if (word == null || !book.delete(id)) {
							throw error("There's no word #" + id + ".");
						}

						return reply(c, "Deleted '" + word.word.singular() + "'. Nobody knows it anymore.");
					}))))
			.then(literal("baby")
				.then(npcArgument().executes(c -> {
					EntityNPC npc = findNpc(c);
					UUID partnerId = PeopleRegistry.get(npc.world).partnerOf(npc.getUuid());
					Mob partner = partnerId != null ? Romance.findPerson(npc.world, partnerId) : null;
					if (partner == null) {
						throw error(npc.nickname + " needs a partner who's here (see /npc couple).");
					}

					EntityNPC child = Children.born(npc, partner);
					if (child == null) {
						throw error("The child couldn't be created.");
					}

					return reply(c, npc.nickname + " and " + nameOf(partner) + " have a new baby: " + child.nickname + ".");
				})))
			.then(literal("grow")
				.then(npcArgument()
					.executes(c -> {
						EntityNPC npc = findNpc(c);
						return reply(c, npc.nickname + " is " + Math.round(npc.life.getGrowth() * 100) + "% grown" + (npc.life.isChild() ? "." : " (an adult)."));
					})
					.then(argument("percent", ArgumentTypeInteger.integer(0, 100)).executes(c -> {
						EntityNPC npc = findNpc(c);
						boolean wasChild = npc.life.isChild();
						npc.life.setGrowth(c.getArgument("percent", Integer.class) / 100.0F);
						if (wasChild && !npc.life.isChild()) {
							Children.cameOfAge(npc);
						}

						return reply(c, npc.nickname + " is now " + Math.round(npc.life.getGrowth() * 100) + "% grown.");
					}))));
	}

	private static int showFamily(CommandContext<CommandSource> c) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		Romance.register(npc);
		PeopleRegistry family = PeopleRegistry.get(npc.world);
		PeopleRegistry.Partnership partnership = family.partnershipOf(npc.getUuid());
		if (partnership != null) {
			reply(c, String.format(Locale.ROOT, "%s is %s %s (%.1f days).", npc.nickname, switch (partnership.stage) {
				case DATING -> "dating";
				case ENGAGED -> "engaged to";
				case MARRIED -> "married to";
			}, family.nameOf(partnership.other(npc.getUuid())), Romance.daysTogether(partnership, npc.world.getWorldTime())));
		}

		boolean any = partnership != null;
		for (UUID relative : Kinship.relativesOf(family, npc.getUuid())) {
			Kinship kin = Kinship.of(family, npc.getUuid(), relative);
			PeopleRegistry.Person person = family.find(relative);
			if (kin == null || person == null || kin == Kinship.SPOUSE || kin == Kinship.FIANCE || kin == Kinship.PARTNER) {
				continue;
			}

			reply(c, "- " + kin.label(person.gender) + ": " + person.name + (person.alive ? "" : " (dead)"));
			any = true;
		}

		if (!any) {
			reply(c, npc.nickname + " has no family yet.");
		}

		return 1;
	}

	/** /npc family <npc> parent|child <target>: records who is whose parent. */
	private static int linkParent(CommandContext<CommandSource> c, boolean targetIsParent) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		Entity target = findTarget(c);
		UUID id = requireId(target);
		Romance.register(npc);
		registerPerson(target);
		PeopleRegistry family = PeopleRegistry.get(npc.world);
		UUID child = targetIsParent ? npc.getUuid() : id;
		UUID parent = targetIsParent ? id : npc.getUuid();
		if (!family.addParent(child, parent)) {
			throw error("Couldn't link them: already linked, the same person, or the child already has two parents.");
		}

		return reply(c, family.nameOf(parent) + " is now " + family.nameOf(child) + "'s parent.");
	}

	private static int setCouple(CommandContext<CommandSource> c) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		Entity target = findTarget(c);
		UUID id = requireId(target);
		String stage = ArgumentTypeString.getString(c, "stage").toLowerCase(Locale.ROOT);
		Romance.register(npc);
		registerPerson(target);
		PeopleRegistry family = PeopleRegistry.get(npc.world);
		long now = npc.world.getWorldTime();
		if (stage.equals("none")) {
			family.breakUp(npc.getUuid());
			return reply(c, npc.nickname + " is single now.");
		}

		PeopleRegistry.Stage next = switch (stage) {
			case "dating" -> PeopleRegistry.Stage.DATING;
			case "engaged" -> PeopleRegistry.Stage.ENGAGED;
			case "married" -> PeopleRegistry.Stage.MARRIED;
			default -> throw error("Use dating, engaged, married or none.");
		};
		UUID current = family.partnerOf(npc.getUuid());
		if (current == null || !current.equals(id)) {
			family.breakUp(npc.getUuid());
			family.breakUp(id);
			family.startDating(npc.getUuid(), id, now);
		}

		family.setStage(npc.getUuid(), next, now);
		return reply(c, npc.nickname + " and " + nameOf(target) + " are now " + stage + ".");
	}

	private FamilyCommands() {
	}
}
