package lifeblood.command;

import static lifeblood.command.CommandSupport.*;
import static lifeblood.command.CommandTree.branch;

import com.mojang.brigadier.arguments.ArgumentTypeString;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.net.command.CommandSource;
import lifeblood.entity.EntityNPC;
import lifeblood.travel.Outing;
import lifeblood.travel.OutingKind;
import lifeblood.travel.OutingKinds;
import lifeblood.travel.Strangers;
import lifeblood.travel.TravelRegister;

/**
 * /lb travel: testing trips. NPCs set out on their own and strangers pass through on their own; these send someone now,
 * start a stranger toward you now, and list the travelers out of sight.
 */
final class TravelCommands {
	private static final SuggestionProvider<CommandSource> KINDS = (c, builder) -> {
		for (String kind : List.of("journey", "adventure", "visit")) {
			builder.suggest(kind);
		}

		return builder.buildFuture();
	};

	private TravelCommands() {
	}

	static void addTo(ArgumentBuilderLiteral<CommandSource> root) {
		root.then(branch("travel", "Trips away from home: journeys, adventures, visits, and strangers passing through.")
			.then(branch("travel send", "Sends NPCs off on a trip of a kind now (journey, adventure, visit).")
				.then(npcsArgument("npcs")
					.then(argument("kind", ArgumentTypeString.word()).suggests(KINDS).executes(TravelCommands::send))))
			.then(branch("travel stranger", "Starts a stranger walking toward you from out of sight, on their way home far off.")
				.executes(c -> {
					Player player = requirePlayer(c.getSource());
					EntityNPC stranger = Strangers.setOut(c.getSource().getWorld(), player, c.getSource().getWorld().rand);
					return reply(c, stranger.life.getFullName() + " has set out from about 220 blocks away, and will pass near you.");
				}))
			.then(branch("travel list", "Travelers out of sight, where they've got to, and how far along their trip is.").executes(TravelCommands::list)));
	}

	private static int send(CommandContext<CommandSource> c) throws CommandSyntaxException {
		String kindId = ArgumentTypeString.getString(c, "kind");
		OutingKind kind = OutingKinds.byId(kindId);
		if (kind == null) {
			throw error("There's no kind of trip called " + kindId + ".");
		}

		List<EntityNPC> npcs = getNpcs(c, "npcs");
		int sent = 0;
		for (EntityNPC npc : npcs) {
			Outing outing = kind.plan(npc, npc.getRandom());
			if (outing == null) {
				reply(c, npc.life.getFullName() + " has nowhere to go on a " + kindId + ".");
				continue;
			}

			outing.started = npc.world.getWorldTime();
			outing.homeX = (int) Math.floor(npc.home.getX());
			outing.homeY = (int) Math.floor(npc.home.getY());
			outing.homeZ = (int) Math.floor(npc.home.getZ());
			npc.outing.start(outing);
			reply(c, npc.life.getFullName() + " sets off on a " + kindId + " to " + outing.destX + " " + outing.destZ + ".");
			sent++;
		}

		return sent;
	}

	private static int list(CommandContext<CommandSource> c) {
		List<TravelRegister.Traveler> travelers = TravelRegister.get(c.getSource().getWorld()).all();
		if (travelers.isEmpty()) {
			return reply(c, "Nobody is traveling out of sight.");
		}

		reply(c, travelers.size() + (travelers.size() == 1 ? " traveler" : " travelers") + " out of sight:");
		for (TravelRegister.Traveler traveler : travelers) {
			String name = traveler.npc.getStringOrDefault("Nickname", "") + " " + traveler.npc.getCompound("Lifeblood").getCompound("Parts")
				.getCompound("Life").getStringOrDefault("FamilyName", "");
			reply(c, "- " + name.trim() + ": " + traveler.outing.kind + ", " + traveler.outing.stage.name().toLowerCase(Locale.ROOT) + ", at "
				+ Math.round(traveler.x) + " " + Math.round(traveler.z) + (traveler.outing.passingThrough ? " (passing through)" : "")
				+ (traveler.found.isEmpty() ? "" : ", carrying " + traveler.found.size() + " finds"));
		}

		return travelers.size();
	}
}
