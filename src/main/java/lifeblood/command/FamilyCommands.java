package lifeblood.command;

import static lifeblood.command.CommandSupport.*;
import static lifeblood.command.CommandTree.branch;

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
import lifeblood.world.Calendar;

/**
 * Families and words:
 * - /lb family: relatives, parent links, couples and babies, for NPCs and players alike;
 * - /lb words: the words players have taught.
 */
final class FamilyCommands {
	private FamilyCommands() {
	}

	static void addTo(ArgumentBuilderLiteral<CommandSource> root) {
		root.then(branch("family", "Relatives, couples and children, for NPCs and players alike.")
			.then(branch("family show", "An NPC's partner and relatives.")
				.then(npcArgument("npc").executes(FamilyCommands::showFamily)))
			.then(branch("family parent", "Records that someone is a child's parent.")
				.then(personArgument("child")
					.then(personArgument("parent").executes(FamilyCommands::linkParent))))
			.then(branch("family unlink", "Removes a parent and child link between two people.")
				.then(personArgument("a")
					.then(personArgument("b").executes(c -> {
						Entity a = getPerson(c, "a");
						Entity b = getPerson(c, "b");
						UUID aId = requireId(a);
						UUID bId = requireId(b);
						PeopleRegistry family = PeopleRegistry.get(c.getSource().getWorld());
						family.removeParent(aId, bId);
						family.removeParent(bId, aId);
						return reply(c, nameOf(a) + " and " + nameOf(b) + " are no longer parent and child.");
					}))))
			.then(branch("family couple", "Sets two people's relationship, its clock starting now: dating, engaged, married or none.")
				.then(personArgument("a")
					.then(personArgument("b")
						.then(argument("stage", ArgumentTypeString.word()).suggests((c, builder) -> {
							for (String stage : new String[] {"dating", "engaged", "married", "none"}) {
								builder.suggest(stage);
							}

							return builder.buildFuture();
						}).executes(FamilyCommands::setCouple)))))
			.then(branch("family baby", "An NPC and its partner have a child now.")
				.then(npcArgument("npc").executes(c -> {
					EntityNPC npc = getNpc(c, "npc");
					UUID partnerId = PeopleRegistry.get(npc.world).partnerOf(npc.getUuid());
					Mob partner = partnerId != null ? Romance.findPerson(npc.world, partnerId) : null;
					if (partner == null) {
						throw error(npc.nickname + " needs a partner who's here (see /lb family couple).");
					}

					EntityNPC child = Children.born(npc, partner);
					if (child == null) {
						throw error("The child couldn't be created.");
					}

					return reply(c, npc.nickname + " and " + nameOf(partner) + " have a new baby: " + child.nickname + ".");
				}))));

		root.then(branch("words", "The words players have taught NPCs.")
			.then(branch("words list", "Every taught word, with its id.").executes(c -> {
				WordBook book = WordBook.get(c.getSource().getWorld());
				if (book.all().isEmpty()) {
					return reply(c, "Nobody has been taught any words yet.");
				}

				for (WordBook.TaughtWord word : book.all()) {
					reply(c, String.format(Locale.ROOT, "- #%d %s (%s, %s), taught by %s, known by %d", word.id, word.word.singular(),
						word.category.id(), word.personal ? "personal" : "general", word.teacherName, word.knownBy.size()));
				}

				return book.all().size();
			}))
			.then(branch("words delete", "Removes a word from everyone who knows it.")
				.then(argument("id", ArgumentTypeInteger.integer(1, Integer.MAX_VALUE)).executes(c -> {
					WordBook book = WordBook.get(c.getSource().getWorld());
					int id = c.getArgument("id", Integer.class);
					WordBook.TaughtWord word = book.find(id);
					if (word == null || !book.delete(id)) {
						throw error("There's no word #" + id + ".");
					}

					return reply(c, "Deleted '" + word.word.singular() + "'. Nobody knows it anymore.");
				}))));
	}

	private static int showFamily(CommandContext<CommandSource> c) throws CommandSyntaxException {
		EntityNPC npc = getNpc(c, "npc");
		Romance.register(npc);
		PeopleRegistry family = PeopleRegistry.get(npc.world);
		PeopleRegistry.Partnership partnership = family.partnershipOf(npc.getUuid());
		if (partnership != null) {
			reply(c, String.format(Locale.ROOT, "%s is %s %s (%s).", npc.life.getFullName(), switch (partnership.stage) {
				case DATING -> "dating";
				case ENGAGED -> "engaged to";
				case MARRIED -> "married to";
			}, family.fullNameOf(partnership.other(npc.getUuid())), Calendar.describeLength(npc.world, npc.world.getWorldTime() - partnership.since)));
		}

		boolean any = partnership != null;
		for (UUID relative : Kinship.relativesOf(family, npc.getUuid())) {
			Kinship kin = Kinship.of(family, npc.getUuid(), relative);
			PeopleRegistry.Person person = family.find(relative);
			if (kin == null || person == null || kin == Kinship.SPOUSE || kin == Kinship.FIANCE || kin == Kinship.PARTNER) {
				continue;
			}

			reply(c, "- " + kin.label(person.gender) + ": " + family.fullNameOf(relative) + (person.alive ? "" : " (dead)"));
			any = true;
		}

		if (!any) {
			reply(c, npc.nickname + " has no family yet.");
		}

		return 1;
	}

	private static int linkParent(CommandContext<CommandSource> c) throws CommandSyntaxException {
		Entity childEntity = getPerson(c, "child");
		Entity parentEntity = getPerson(c, "parent");
		UUID child = requireId(childEntity);
		UUID parent = requireId(parentEntity);
		registerPerson(childEntity);
		registerPerson(parentEntity);
		PeopleRegistry family = PeopleRegistry.get(c.getSource().getWorld());
		if (!family.addParent(child, parent)) {
			throw error("Couldn't link them: already linked, the same person, or the child already has two parents.");
		}

		return reply(c, family.nameOf(parent) + " is now " + family.nameOf(child) + "'s parent.");
	}

	private static int setCouple(CommandContext<CommandSource> c) throws CommandSyntaxException {
		Entity a = getPerson(c, "a");
		Entity b = getPerson(c, "b");
		UUID aId = requireId(a);
		UUID bId = requireId(b);
		if (aId.equals(bId)) {
			throw error("That's the same person twice.");
		}

		String stage = ArgumentTypeString.getString(c, "stage").toLowerCase(Locale.ROOT);
		registerPerson(a);
		registerPerson(b);
		PeopleRegistry family = PeopleRegistry.get(c.getSource().getWorld());
		long now = c.getSource().getWorld().getWorldTime();
		if (stage.equals("none")) {
			UUID current = family.partnerOf(aId);
			if (current == null || !current.equals(bId)) {
				throw error(nameOf(a) + " and " + nameOf(b) + " aren't together.");
			}

			family.breakUp(aId);
			return reply(c, nameOf(a) + " and " + nameOf(b) + " aren't together anymore.");
		}

		PeopleRegistry.Stage next = switch (stage) {
			case "dating" -> PeopleRegistry.Stage.DATING;
			case "engaged" -> PeopleRegistry.Stage.ENGAGED;
			case "married" -> PeopleRegistry.Stage.MARRIED;
			default -> throw error("Use dating, engaged, married or none.");
		};
		UUID current = family.partnerOf(aId);
		if (current == null || !current.equals(bId)) {
			family.breakUp(aId);
			family.breakUp(bId);
			family.startDating(aId, bId, now);
		}

		family.setStage(aId, next, now);
		return reply(c, nameOf(a) + " and " + nameOf(b) + " are now " + stage + ".");
	}
}
