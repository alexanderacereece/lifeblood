package lifeblood.command;

import static lifeblood.command.CommandSupport.*;
import static lifeblood.command.CommandTree.branch;

import com.mojang.brigadier.arguments.ArgumentTypeInteger;
import com.mojang.brigadier.arguments.ArgumentTypeString;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.EntityDispatcher;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.net.command.CommandSource;
import net.minecraft.core.net.command.arguments.ArgumentTypeVector3d;
import net.minecraft.core.world.World;
import lifeblood.ai.NpcBehavior;
import lifeblood.ai.behavior.FollowPlayerBehavior;
import lifeblood.ai.behavior.GoToBehavior;
import lifeblood.ai.behavior.StayBehavior;
import lifeblood.ai.behavior.WanderBehavior;
import lifeblood.entity.EntityNPC;
import lifeblood.entity.HealthMode;
import lifeblood.entity.NpcGender;
import lifeblood.family.Children;
import lifeblood.skin.NpcSkinFiles;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

/**
 * /lb npc: making NPCs and directing them, like vanilla commands do for entities: summon, list, remove, heal, teleport,
 * plus orders (go to, follow, stay, wander, free), gender, how hard they are to kill, growing up, and saying a line.
 */
final class NpcCommands {
	private NpcCommands() {
	}

	static void addTo(ArgumentBuilderLiteral<CommandSource> root) {
		root.then(branch("npc", "Making NPCs and telling them what to do.")
			.then(branch("npc summon", "Summons an NPC where you stand, with a skin file of its name if there is one.")
				.then(argument("name", ArgumentTypeString.string())
					.executes(c -> summon(c, null))
					.then(argument("faction", ArgumentTypeString.word()).suggests(FACTION_OR_NONE_NAMES)
						.executes(c -> summon(c, requireFactionOrNone(c, "faction"))))))
			.then(branch("npc list", "Every loaded NPC: what it's doing and where.").executes(NpcCommands::list))
			.then(branch("npc remove", "Removes NPCs from the world, like /kill but without dying.")
				.then(npcsArgument("npcs").executes(c -> {
					List<EntityNPC> npcs = getNpcs(c, "npcs");
					npcs.forEach(EntityNPC::remove);
					return replyEach(c, npcs, name -> "Removed " + name + ".", "Removed %d NPCs.");
				})))
			.then(branch("npc heal", "Heals NPCs fully: back on their feet and done recovering.")
				.then(npcsArgument("npcs").executes(c -> {
					List<EntityNPC> npcs = getNpcs(c, "npcs");
					npcs.forEach(npc -> npc.health.healFully());
					return replyEach(c, npcs, name -> name + " is fully healed.", "Healed %d NPCs.");
				})))
			.then(branch("npc teleport", "Teleports NPCs to a position or to someone, like /teleport.")
				.then(npcsArgument("npcs")
					.then(argument("location", ArgumentTypeVector3d.vec3d()).executes(c -> {
						double[] pos = position(c, "location");
						return teleport(c, pos[0], pos[1], pos[2], describe(pos));
					}))
					.then(personArgument("destination").executes(c -> {
						Entity target = getPerson(c, "destination");
						return teleport(c, target.x, target.bb.minY(), target.z, nameOf(target));
					}))))
			.then(branch("npc goto", "Sends NPCs walking to a position or to someone, where they stay.")
				.then(npcsArgument("npcs")
					.then(argument("location", ArgumentTypeVector3d.vec3d()).executes(c -> {
						double[] pos = position(c, "location");
						return goTo(c, pos[0], pos[1], pos[2], describe(pos));
					}))
					.then(personArgument("destination").executes(c -> {
						Entity target = getPerson(c, "destination");
						return goTo(c, target.x, target.bb.minY(), target.z, nameOf(target));
					}))))
			.then(branch("npc follow", "NPCs follow a player (you, if no one is given).")
				.then(npcsArgument("npcs")
					.executes(c -> follow(c, requirePlayer(c.getSource())))
					.then(personArgument("leader").executes(c -> {
						if (!(getPerson(c, "leader") instanceof Player leader)) {
							throw error("NPCs can only follow players for now.");
						}

						return follow(c, leader);
					}))))
			.then(branch("npc stay", "NPCs stay where they are.")
				.then(npcsArgument("npcs").executes(c -> {
					List<EntityNPC> npcs = getNpcs(c, "npcs");
					for (EntityNPC npc : npcs) {
						overrideRecovery(c, npc);
						npc.setHomeHere();
						npc.brain.set(new StayBehavior());
					}

					return replyEach(c, npcs, name -> name + " will stay here.", "%d NPCs will stay where they are.");
				})))
			.then(branch("npc wander", "NPCs wander around where they are, within a radius in blocks.")
				.then(npcsArgument("npcs")
					.executes(c -> wander(c, WanderBehavior.DEFAULT_RADIUS))
					.then(argument("radius", ArgumentTypeInteger.integer(1, 64))
						.executes(c -> wander(c, c.getArgument("radius", Integer.class))))))
			.then(branch("npc free", "Drops NPCs' orders: they go about their routine around where they are.")
				.then(npcsArgument("npcs").executes(c -> {
					List<EntityNPC> npcs = getNpcs(c, "npcs");
					for (EntityNPC npc : npcs) {
						npc.setHomeHere();
						npc.brain.clear();
					}

					return replyEach(c, npcs, name -> name + " has no orders now and goes about its day around here.", "%d NPCs have no orders now.");
				})))
			.then(branch("npc gender", "Shows or sets an NPC's gender.")
				.then(npcsArgument("npcs")
					.executes(c -> showEach(c, npc -> npc.nickname + " is " + npc.getGender().displayName().toLowerCase(Locale.ROOT) + "."))
					.then(argument("gender", ArgumentTypeString.word()).suggests(GENDER_NAMES).executes(c -> {
						String name = ArgumentTypeString.getString(c, "gender");
						NpcGender gender = NpcGender.byName(name);
						if (gender == null) {
							throw error("Unknown gender '" + name + "'. Use male or female.");
						}

						List<EntityNPC> npcs = getNpcs(c, "npcs");
						npcs.forEach(npc -> npc.setGender(gender));
						String label = gender.displayName().toLowerCase(Locale.ROOT);
						return replyEach(c, npcs, npc -> npc + " is now " + label + ".", "%d NPCs are now " + label + ".");
					}))))
			.then(branch("npc mortality", "Shows or sets how NPCs take damage: mortal, invincible (knocked down, not killed) or invulnerable.")
				.then(npcsArgument("npcs")
					.executes(c -> showEach(c, npc -> npc.nickname + " is " + npc.health.getMode().displayName() + "."))
					.then(argument("mode", ArgumentTypeString.word()).suggests(HEALTH_MODES).executes(c -> {
						String name = ArgumentTypeString.getString(c, "mode");
						HealthMode mode = HealthMode.byName(name);
						if (mode == null) {
							throw error("Unknown mode '" + name + "'. Use mortal, invincible or invulnerable.");
						}

						List<EntityNPC> npcs = getNpcs(c, "npcs");
						npcs.forEach(npc -> npc.health.setMode(mode));
						return replyEach(c, npcs, npc -> npc + " is now " + mode.displayName() + ".", "%d NPCs are now " + mode.displayName() + ".");
					}))))
			.then(branch("npc grow", "Shows or sets how grown up an NPC is, 0 to 100; children grow up over 18 years.")
				.then(npcsArgument("npcs")
					.executes(c -> showEach(c, npc -> npc.nickname + " is " + Math.round(npc.life.getGrowth() * 100) + "% grown"
						+ (npc.life.isChild() ? "." : " (an adult).")))
					.then(argument("percent", ArgumentTypeInteger.integer(0, 100)).executes(c -> {
						int percent = c.getArgument("percent", Integer.class);
						List<EntityNPC> npcs = getNpcs(c, "npcs");
						for (EntityNPC npc : npcs) {
							boolean wasChild = npc.life.isChild();
							npc.life.setGrowth(percent / 100.0F);
							if (wasChild && !npc.life.isChild()) {
								Children.cameOfAge(npc);
							}
						}

						return replyEach(c, npcs, npc -> npc + " is now " + percent + "% grown.", "%d NPCs are now " + percent + "% grown.");
					}))))
			.then(branch("npc say", "Makes an NPC say a line from a speech file symbol, aloud.")
				.then(npcArgument("npc")
					.then(argument("symbol", ArgumentTypeString.word()).executes(c -> {
						EntityNPC npc = getNpc(c, "npc");
						String symbol = ArgumentTypeString.getString(c, "symbol");
						String line = npc.voice.line(symbol, c.getSource().getSender(), null);
						if (line == null) {
							throw error(npc.nickname + " has nothing to say for '" + symbol + "' right now.");
						}

						npc.voice.sayAloud(line);
						return 1;
					})))));
	}

	/** Shows something about each NPC the "npcs" argument names. */
	private static int showEach(CommandContext<CommandSource> c, java.util.function.Function<EntityNPC, String> line) throws CommandSyntaxException {
		List<EntityNPC> npcs = getNpcs(c, "npcs");
		for (EntityNPC npc : npcs) {
			reply(c, line.apply(npc));
		}

		return npcs.size();
	}

	/** faction: null means the default. */
	private static int summon(CommandContext<CommandSource> c, @Nullable String faction) throws CommandSyntaxException {
		CommandSource source = c.getSource();
		Vector3dc pos = source.getCoordinates(false);
		if (pos == null) {
			throw error("You must be in a world to summon an NPC.");
		}

		String name = ArgumentTypeString.getString(c, "name").trim();
		if (name.isEmpty() || name.length() > 32) {
			throw error("NPC names must be 1 to 32 characters long.");
		}

		// A skin file with the same name as the NPC is used if there is one; otherwise it's built from parts.
		String fromName = name.toLowerCase(Locale.ROOT).replace(' ', '_');
		String skin = NpcSkinFiles.exists(fromName) ? fromName : "";
		World world = source.getWorld();
		EntityNPC npc = EntityDispatcher.getInstance().createEntityInWorld(EntityNPC.class, world);
		if (npc == null) {
			throw error("Could not create the NPC entity.");
		}

		Player player = source.getSender();
		double feet = player != null ? pos.y() - player.heightOffset : pos.y();
		npc.moveTo(pos.x(), feet, pos.z(), player != null ? player.yRot : 0.0F, 0.0F);
		npc.setNickname(name);
		npc.setSkin(skin);
		// A first guess, changeable with /lb npc gender: slim-armed skins are usually female characters. Without a skin
		// the NPC is built from appearance parts, and either is equally likely.
		if (skin.isEmpty()) {
			npc.setGender(npc.getRandom().nextBoolean() ? NpcGender.FEMALE : NpcGender.MALE);
		} else {
			npc.setGender(NpcSkinFiles.isSlim(skin) ? NpcGender.FEMALE : NpcGender.MALE);
		}

		npc.rollAppearanceIfNeeded();
		if (faction != null) {
			npc.setFaction(faction);
		}

		// No orders: it goes about its routine around where it appeared.
		npc.setHomeHere();
		npc.spawnInit();
		world.entityJoinedWorld(npc);
		return reply(c, "Summoned " + name + ", " + factionLabel(npc.getFaction()) + (skin.isEmpty() ? "." : " (skin '" + skin + "')."));
	}

	private static int goTo(CommandContext<CommandSource> c, double x, double y, double z, String where) throws CommandSyntaxException {
		List<EntityNPC> npcs = getNpcs(c, "npcs");
		for (EntityNPC npc : npcs) {
			overrideRecovery(c, npc);
			npc.brain.set(new GoToBehavior(x, y, z));
		}

		return replyEach(c, npcs, name -> name + " is heading to " + where + ".", "%d NPCs are heading to " + where + ".");
	}

	private static int teleport(CommandContext<CommandSource> c, double x, double y, double z, String where) throws CommandSyntaxException {
		List<EntityNPC> npcs = getNpcs(c, "npcs");
		for (EntityNPC npc : npcs) {
			npc.navigator.stop();
			npc.teleportTo(x, y, z);
			npc.setHomeHere();
		}

		return replyEach(c, npcs, name -> "Teleported " + name + " to " + where + ".", "Teleported %d NPCs to " + where + ".");
	}

	private static int follow(CommandContext<CommandSource> c, Player leader) throws CommandSyntaxException {
		List<EntityNPC> npcs = getNpcs(c, "npcs");
		for (EntityNPC npc : npcs) {
			overrideRecovery(c, npc);
			npc.brain.set(new FollowPlayerBehavior(leader.uuid));
		}

		String who = leader == c.getSource().getSender() ? "you" : leader.username;
		return replyEach(c, npcs, name -> name + " is now following " + who + ".", "%d NPCs are now following " + who + ".");
	}

	private static int wander(CommandContext<CommandSource> c, int radius) throws CommandSyntaxException {
		List<EntityNPC> npcs = getNpcs(c, "npcs");
		for (EntityNPC npc : npcs) {
			overrideRecovery(c, npc);
			npc.setHomeHere();
			npc.brain.set(new WanderBehavior(radius));
		}

		return replyEach(c, npcs, name -> name + " will wander within " + radius + " blocks of here.", "%d NPCs will wander within " + radius + " blocks.");
	}

	/** An order by command beats recovering from being downed (which otherwise sends it home and ignores orders). */
	private static void overrideRecovery(CommandContext<CommandSource> c, EntityNPC npc) {
		if (npc.health.isRecovering()) {
			npc.health.stopRecovering();
			reply(c, npc.nickname + " was recovering at home; it's done now.");
		}
	}

	private static int list(CommandContext<CommandSource> c) {
		List<EntityNPC> npcs = getNpcs(c.getSource().getWorld());
		if (npcs.isEmpty()) {
			return reply(c, "There are no NPCs loaded.");
		}

		c.getSource().sendMessage(npcs.size() + " NPC(s) loaded:");
		for (EntityNPC npc : npcs) {
			NpcBehavior active = npc.brain.getActive();
			String doing = active == null ? "idle" : npc.brain.isOnRoutine() ? active.describe() + " (its routine)" : active.describe();
			c.getSource().sendMessage(String.format(Locale.ROOT, "- %s (%s): %s, health %d, faction %s, at %d %d %d%s",
				npc.nickname, npc.getGender().displayName().toLowerCase(Locale.ROOT), doing, npc.getHealth(),
				npc.getFaction() != null ? npc.getFaction() : "none",
				(int) Math.floor(npc.x), (int) Math.floor(npc.y), (int) Math.floor(npc.z),
				npc.health.getMode() != HealthMode.MORTAL ? ", " + npc.health.getMode().displayName() : ""));
		}

		return npcs.size();
	}
}
