package lifeblood.command;

import static lifeblood.command.CommandSupport.*;

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
import lifeblood.perf.NpcProfiler;
import lifeblood.skin.NpcSkinFiles;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

/** /npc subcommands: summoning and directing NPCs: where they go, what they do, and how hard they are to kill. */
final class NpcBasicsCommands {
	static void addTo(ArgumentBuilderLiteral<CommandSource> root) {
		root
			.then(literal("summon")
				.then(argument("name", ArgumentTypeString.string())
					.executes(c -> summon(c, null, null))
					.then(argument("skin", ArgumentTypeString.word())
						.suggests(SKIN_NAMES)
						.executes(c -> summon(c, ArgumentTypeString.getString(c, "skin"), null))
						.then(argument("faction", ArgumentTypeString.word())
							.suggests(FACTION_OR_NONE_NAMES)
							.executes(c -> summon(c, ArgumentTypeString.getString(c, "skin"), requireFactionOrNone(c, "faction")))))))
			.then(literal("follow")
				.then(npcArgument().executes(c -> {
					Player player = requirePlayer(c.getSource());
					EntityNPC npc = findNpc(c);
					overrideRecovery(c, npc);
					npc.brain.set(new FollowPlayerBehavior(player.uuid));
					return reply(c, npc.nickname + " is now following you.");
				})))
			.then(literal("goto")
				.then(npcArgument()
					.executes(c -> {
						Player player = requirePlayer(c.getSource());
						return goTo(c, player.x, player.bb.minY(), player.z, "you");
					})
					.then(argument("position", ArgumentTypeVector3d.vec3d()).executes(c -> {
						double[] pos = position(c);
						return goTo(c, pos[0], pos[1], pos[2], describe(pos));
					}))
					.then(literal("@s").executes(c -> {
						Player player = requirePlayer(c.getSource());
						return goTo(c, player.x, player.bb.minY(), player.z, "you");
					}))
					.then(argument("target", ArgumentTypeString.string()).suggests(TARGET_NAMES).executes(c -> {
						Entity target = findTarget(c);
						return goTo(c, target.x, target.bb.minY(), target.z, nameOf(target));
					}))))
			.then(literal("teleport")
				.then(npcArgument()
					.executes(c -> {
						Player player = requirePlayer(c.getSource());
						return teleport(c, player.x, player.bb.minY(), player.z, "you");
					})
					.then(argument("position", ArgumentTypeVector3d.vec3d()).executes(c -> {
						double[] pos = position(c);
						return teleport(c, pos[0], pos[1], pos[2], describe(pos));
					}))
					.then(literal("@s").executes(c -> {
						Player player = requirePlayer(c.getSource());
						return teleport(c, player.x, player.bb.minY(), player.z, "you");
					}))
					.then(argument("target", ArgumentTypeString.string()).suggests(TARGET_NAMES).executes(c -> {
						Entity target = findTarget(c);
						return teleport(c, target.x, target.bb.minY(), target.z, nameOf(target));
					}))))
			.then(literal("tpto")
				.then(npcArgument().executes(c -> {
					Player player = requirePlayer(c.getSource());
					EntityNPC npc = findNpc(c);
					c.getSource().teleportPlayerToPos(player, npc.x, npc.y, npc.z);
					return reply(c, "Teleported you to " + npc.nickname + ".");
				})))
			.then(literal("gender")
				.then(npcArgument()
					.executes(c -> {
						EntityNPC npc = findNpc(c);
						return reply(c, npc.nickname + " is " + npc.getGender().displayName().toLowerCase(Locale.ROOT) + ".");
					})
					.then(argument("gender", ArgumentTypeString.word()).suggests(GENDER_NAMES).executes(c -> {
						EntityNPC npc = findNpc(c);
						String name = ArgumentTypeString.getString(c, "gender");
						NpcGender gender = NpcGender.byName(name);
						if (gender == null) {
							throw error("Unknown gender '" + name + "'. Use male or female.");
						}

						npc.setGender(gender);
						return reply(c, npc.nickname + " is now " + gender.displayName().toLowerCase(Locale.ROOT) + ".");
					}))))
			.then(literal("heal")
				.then(npcArgument().executes(c -> {
					EntityNPC npc = findNpc(c);
					npc.health.healFully();
					return reply(c, npc.nickname + " is fully healed.");
				})))
			.then(literal("perf").executes(c -> {
				int count = getNpcs(c.getSource().getWorld()).size();
				return reply(c, count + " NPC(s) loaded. " + NpcProfiler.report());
			}))
			.then(literal("stay")
				.then(npcArgument().executes(c -> {
					EntityNPC npc = findNpc(c);
					overrideRecovery(c, npc);
					npc.setHomeHere();
					npc.brain.set(new StayBehavior());
					return reply(c, npc.nickname + " will stay here.");
				})))
			.then(literal("free")
				.then(npcArgument().executes(c -> {
					EntityNPC npc = findNpc(c);
					npc.setHomeHere();
					npc.brain.clear();
					return reply(c, npc.nickname + " has no orders now, and goes about its day around here.");
				})))
			.then(literal("wander")
				.then(npcArgument()
					.executes(c -> wander(c, WanderBehavior.DEFAULT_RADIUS))
					.then(argument("radius", ArgumentTypeInteger.integer(1, 64))
						.executes(c -> wander(c, c.getArgument("radius", Integer.class))))))
			.then(literal("mortality")
				.then(npcArgument()
					.executes(c -> {
						EntityNPC npc = findNpc(c);
						return reply(c, npc.nickname + " is " + npc.health.getMode().displayName() + ".");
					})
					.then(argument("mode", ArgumentTypeString.word()).suggests(HEALTH_MODES).executes(c -> {
						EntityNPC npc = findNpc(c);
						String name = ArgumentTypeString.getString(c, "mode");
						HealthMode mode = HealthMode.byName(name);
						if (mode == null) {
							throw error("Unknown mode '" + name + "'. Use mortal, invincible or invulnerable.");
						}

						npc.health.setMode(mode);
						return reply(c, npc.nickname + " is now " + mode.displayName() + ".");
					}))))
			.then(literal("remove")
				.then(npcArgument().executes(c -> {
					EntityNPC npc = findNpc(c);
					npc.remove();
					return reply(c, "Removed " + npc.nickname + ".");
				})))
			.then(literal("list").executes(NpcBasicsCommands::list));
	}

	/** skin: null picks a skin matching the name if one exists; "default" means no skin. faction: null means the default. */
	private static int summon(CommandContext<CommandSource> c, @Nullable String skin, @Nullable String faction) throws CommandSyntaxException {
		CommandSource source = c.getSource();
		Player player = requirePlayer(source);
		Vector3dc pos = source.getCoordinates(false);
		if (pos == null) {
			throw error("You must be in a world to summon an NPC.");
		}

		String name = ArgumentTypeString.getString(c, "name").trim();
		if (name.isEmpty() || name.length() > 32) {
			throw error("NPC names must be 1 to 32 characters long.");
		}

		// With no skin given, use a skin file with the same name as the NPC if there is one.
		if (skin == null) {
			String fromName = name.toLowerCase(Locale.ROOT).replace(' ', '_');
			skin = NpcSkinFiles.exists(fromName) ? fromName : "";
		} else if (skin.equalsIgnoreCase("default")) {
			skin = "";
		}

		World world = source.getWorld();
		EntityNPC npc = EntityDispatcher.getInstance().createEntityInWorld(EntityNPC.class, world);
		if (npc == null) {
			throw error("Could not create the NPC entity.");
		}

		npc.moveTo(pos.x(), pos.y() - player.heightOffset, pos.z(), player.yRot, 0.0F);
		npc.setNickname(name);
		npc.setSkin(skin);
		// A first guess, changeable with /npc gender: slim-armed skins are usually female characters.
		// A first guess, changeable with /npc gender: slim-armed skins are usually female characters. Without a skin
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

		String skinInfo = skin.isEmpty() ? "default skin" : "skin '" + skin + "'";
		if (!skin.isEmpty() && !NpcSkinFiles.exists(skin)) {
			skinInfo += ", which is not in " + NpcSkinFiles.FOLDER_NAME + " yet";
		}

		return reply(c, "Summoned " + name + ", " + factionLabel(npc.getFaction()) + " (" + skinInfo + ").");
	}

	private static int goTo(CommandContext<CommandSource> c, double x, double y, double z, String where) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		overrideRecovery(c, npc);
		npc.brain.set(new GoToBehavior(x, y, z));
		return reply(c, npc.nickname + " is heading to " + where + ".");
	}

	/** An order by command beats recovering from being downed (which otherwise sends it home and ignores orders). */
	private static void overrideRecovery(CommandContext<CommandSource> c, EntityNPC npc) {
		if (npc.health.isRecovering()) {
			npc.health.stopRecovering();
			reply(c, npc.nickname + " was recovering at home; it's done now.");
		}
	}

	private static int teleport(CommandContext<CommandSource> c, double x, double y, double z, String where) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		npc.navigator.stop();
		npc.teleportTo(x, y, z);
		npc.setHomeHere();
		return reply(c, "Teleported " + npc.nickname + " to " + where + ".");
	}

	private static int wander(CommandContext<CommandSource> c, int radius) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		overrideRecovery(c, npc);
		npc.setHomeHere();
		npc.brain.set(new WanderBehavior(radius));
		return reply(c, npc.nickname + " will wander within " + radius + " blocks of here.");
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
			String skin = npc.getSkin().isEmpty() ? "default" : npc.getSkin();
			c.getSource().sendMessage(String.format(Locale.ROOT, "- %s (%s): %s, skin %s, health %d, %s style, faction %s, at %d %d %d%s",
				npc.nickname, npc.getGender().displayName().toLowerCase(Locale.ROOT), doing, skin, npc.getHealth(),
				npc.getCombatStyle().displayName().toLowerCase(Locale.ROOT), npc.getFaction() != null ? npc.getFaction() : "none",
				(int) Math.floor(npc.x), (int) Math.floor(npc.y), (int) Math.floor(npc.z),
				npc.health.getMode() != HealthMode.MORTAL ? ", " + npc.health.getMode().displayName() : ""));
		}

		return npcs.size();
	}

	private NpcBasicsCommands() {
	}
}
