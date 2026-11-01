package npclaude.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.ArgumentTypeInteger;
import com.mojang.brigadier.arguments.ArgumentTypeString;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.builder.ArgumentBuilderRequired;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Global;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.EntityDispatcher;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.net.command.CommandManager;
import net.minecraft.core.net.command.CommandSource;
import net.minecraft.core.net.command.arguments.ArgumentTypeVector3d;
import net.minecraft.core.net.command.helpers.DoubleCoordinates;
import net.minecraft.core.world.World;
import npclaude.ai.NpcBehavior;
import npclaude.ai.behavior.FollowPlayerBehavior;
import npclaude.ai.behavior.GoToBehavior;
import npclaude.ai.behavior.StayBehavior;
import npclaude.ai.behavior.WanderBehavior;
import npclaude.appearance.Appearance;
import npclaude.family.Children;
import npclaude.family.FamilyRegistry;
import npclaude.family.Kinship;
import npclaude.family.Romance;
import npclaude.personality.Attraction;
import npclaude.appearance.AppearanceLayer;
import npclaude.appearance.PartCatalog;
import npclaude.client.AppearanceTextures;
import npclaude.client.NpcSkins;
import npclaude.combat.FactionRegistry;
import npclaude.combat.FactionRelation;
import npclaude.combat.NpcFactions;
import npclaude.dialogue.SocialLimits;
import npclaude.entity.EntityNPC;
import npclaude.entity.HealthMode;
import npclaude.entity.NpcGender;
import npclaude.entity.SkinModel;
import npclaude.perf.NpcProfiler;
import npclaude.personality.Surroundings;
import npclaude.personality.Tastes;
import npclaude.personality.NpcPersonality;
import npclaude.personality.Quirk;
import npclaude.personality.TraitAxis;
import npclaude.personality.TraitTier;
import npclaude.social.Memory;
import npclaude.social.MemoryType;
import npclaude.social.Opinions;
import npclaude.speech.SpeechFacts;
import npclaude.speech.SpeechLibrary;
import npclaude.skin.NpcSkinFiles;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

/**
 * /npc summon <name> [skin|default] [faction|none]
 * /npc follow|stay|remove <name>
 * /npc goto <name> [<position>|<target>]       (walks there, then stays; no target means to you)
 * /npc teleport <name> [<position>|<target>]   (like /teleport; no target means to you)
 *   A target is another NPC's name, a player's name, or @s for yourself.
 * /npc tpto <name>                           (teleports you to the NPC)
 * /npc gender <name> [male|female]
 * /npc heal <name>         (full health, back on its feet, done recovering)
 * /npc perf                 (AI and pathfinding cost over the last second)
 * /npc wander <name> [radius]
 * /npc skin <name> <skin> [auto|wide|slim]
 * /npc mortality <name> [mortal|invincible|invulnerable]   (invincible: hurt, but knocked down instead of killed)
 * /npc personality <name>                     (trait axes, quirks, and what they like and hate most)
 * /npc personality <name> <axis> <-100..100>   (e.g. courage 80; - is the left pole, + the right)
 * /npc personality <name> randomize            (a whole new personality)
 * /npc quirk <name> add|remove <quirk>
 * /npc likes <name>                            (favorite and hated foods, things, weather, places and seasons)
 * /npc likes <name> reveal                     (debug: shows them all on the profile, without having to hear about them)
 * /npc like <name> [<-100..100>]               (what they think of the item you're holding, or sets it)
 * /npc like <name> weather|biome|season <key> [<-100..100>]   (e.g. weather storm; shows or sets it)
 * /npc faction list
 * /npc faction create <faction> [ally|neutral|hostile]   (its default stance; neutral if left out)
 * /npc faction delete <faction>
 * /npc faction default <faction> [ally|neutral|hostile]
 * /npc faction relation <faction> <faction> [ally|neutral|hostile|default]   (default removes the explicit relation)
 * /npc faction set <name> <faction|none>
 * /npc faction join <faction|none>                       (you join a faction, or leave yours)
 * /npc relation <name> [ally|neutral|hostile|default]    (debug: forces how the NPC treats you; default removes it)
 * /npc opinion <name> [<target>]              (what the NPC thinks of you or someone else, and why)
 * /npc mood <name>                            (the NPC's mood and what's behind it)
 * /npc remember <name> <memory> [<target>]    (debug: gives the NPC a memory, about you or someone else)
 * /npc forget <name> [<target>]               (debug: wipes its memories, or only those about someone)
 * /npc say <name> <symbol>                    (debug: says a line from the speech files aloud)
 * /npc speech reload                          (reads the speech files again)
 * /npc limits [on|off]                        (debug: the one-gift-a-day and three-chats-a-minute limits)
 * /npc list
 * /npc family <name>                           (its partner and relatives)
 * /npc family <name> parent|child <target>     (records that the target is its parent, or its child)
 * /npc family <name> unlink <target>           (removes a parent/child link between them)
 * /npc couple <name> <target> dating|engaged|married|none   (debug: sets their relationship, clock starting now)
 * /npc attraction <name> [opposite|same|both]  (who it can fall for)
 * /npc baby <name>                             (debug: it and its partner have a child now)
 * /npc grow <name> [0..100]                    (how grown up it is; children grow up over 20 days)
 * /npc look <name>                             (the parts and colors its look is built from)
 * /npc look <name> randomize                   (a new random look from the parts available)
 * /npc look <name> <layer> <part|none> [palette]   (e.g. hair bob auburn)
 * /npc parts [reload]                          (lists appearance parts and palettes, or reads them again)
 * /npc skins [reload]
 *
 * Names with spaces need quotes: /npc follow "Walter White"
 */
public class NpcCommand implements CommandManager.CommandRegistry {
	private static final String NONE_KEYWORD = "none";
	private static final String DEFAULT_KEYWORD = "default";
	private static final SuggestionProvider<CommandSource> NPC_NAMES = (c, builder) -> {
		for (EntityNPC npc : getNpcs(c.getSource().getWorld())) {
			builder.suggest(ArgumentTypeString.escapeIfRequired(npc.nickname));
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> SKIN_NAMES = (c, builder) -> {
		for (String skin : NpcSkinFiles.list()) {
			builder.suggest(skin);
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> FACTION_NAMES = (c, builder) -> {
		for (String faction : FactionRegistry.get(c.getSource().getWorld()).all()) {
			builder.suggest(faction);
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> RELATION_NAMES = (c, builder) -> {
		for (FactionRelation relation : FactionRelation.values()) {
			builder.suggest(relation.id());
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> RELATION_OR_DEFAULT_NAMES = (c, builder) -> {
		for (FactionRelation relation : FactionRelation.values()) {
			builder.suggest(relation.id());
		}

		builder.suggest(DEFAULT_KEYWORD);
		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> FACTION_OR_NONE_NAMES = (c, builder) -> {
		for (String faction : FactionRegistry.get(c.getSource().getWorld()).all()) {
			builder.suggest(faction);
		}

		builder.suggest(NONE_KEYWORD);
		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> TARGET_NAMES = (c, builder) -> {
		builder.suggest("@s");
		for (Player player : c.getSource().getWorld().players) {
			builder.suggest(player.username);
		}

		for (EntityNPC npc : getNpcs(c.getSource().getWorld())) {
			builder.suggest(ArgumentTypeString.escapeIfRequired(npc.nickname));
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> GENDER_NAMES = (c, builder) -> {
		for (NpcGender gender : NpcGender.values()) {
			builder.suggest(gender.name().toLowerCase(Locale.ROOT));
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> AXIS_NAMES = (c, builder) -> {
		for (TraitAxis axis : TraitAxis.values()) {
			builder.suggest(axis.id());
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> QUIRK_NAMES = (c, builder) -> {
		for (Quirk quirk : Quirk.values()) {
			builder.suggest(quirk.id());
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> LAYER_NAMES = (c, builder) -> {
		for (AppearanceLayer layer : AppearanceLayer.values()) {
			builder.suggest(layer.id());
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> PART_NAMES = (c, builder) -> {
		AppearanceLayer layer = AppearanceLayer.byName(ArgumentTypeString.getString(c, "layer"));
		builder.suggest("none");
		if (layer != null) {
			for (String part : PartCatalog.get().allParts(layer)) {
				builder.suggest(part);
			}
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> PALETTE_NAMES = (c, builder) -> {
		AppearanceLayer layer = AppearanceLayer.byName(ArgumentTypeString.getString(c, "layer"));
		if (layer != null && layer.paletteGroup != null) {
			for (String palette : PartCatalog.get().paletteNames(layer.paletteGroup)) {
				builder.suggest(palette);
			}
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> MEMORY_NAMES = (c, builder) -> {
		for (MemoryType type : MemoryType.values()) {
			builder.suggest(type.id());
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> HEALTH_MODES = (c, builder) -> {
		for (HealthMode mode : HealthMode.values()) {
			builder.suggest(mode.displayName());
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> SKIN_MODELS = (c, builder) -> {
		for (SkinModel model : SkinModel.values()) {
			builder.suggest(model.name().toLowerCase(Locale.ROOT));
		}

		return builder.buildFuture();
	};

	@Override
	public void register(CommandDispatcher<CommandSource> dispatcher) {
		dispatcher.register(literal("npc")
			.requires(CommandSource::hasAdmin)
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
					npc.healFully();
					return reply(c, npc.nickname + " is fully healed.");
				})))
			.then(literal("perf").executes(c -> {
				int count = getNpcs(c.getSource().getWorld()).size();
				return reply(c, count + " NPC(s) loaded. " + NpcProfiler.report());
			}))
			.then(literal("stay")
				.then(npcArgument().executes(c -> {
					EntityNPC npc = findNpc(c);
					npc.setHomeHere();
					npc.brain.set(new StayBehavior());
					return reply(c, npc.nickname + " will stay here.");
				})))
			.then(literal("wander")
				.then(npcArgument()
					.executes(c -> wander(c, WanderBehavior.DEFAULT_RADIUS))
					.then(argument("radius", ArgumentTypeInteger.integer(1, 64))
						.executes(c -> wander(c, c.getArgument("radius", Integer.class))))))
			.then(literal("skin")
				.then(npcArgument()
					.then(argument("skin", ArgumentTypeString.word())
						.suggests(SKIN_NAMES)
						.executes(c -> setSkin(c, SkinModel.AUTO))
						.then(argument("model", ArgumentTypeString.word())
							.suggests(SKIN_MODELS)
							.executes(c -> {
								String modelName = ArgumentTypeString.getString(c, "model");
								SkinModel model = SkinModel.byName(modelName);
								if (model == null) {
									throw error("Unknown skin model '" + modelName + "'. Use auto, wide or slim.");
								}

								return setSkin(c, model);
							})))))
			.then(literal("mortality")
				.then(npcArgument()
					.executes(c -> {
						EntityNPC npc = findNpc(c);
						return reply(c, npc.nickname + " is " + npc.getHealthMode().displayName() + ".");
					})
					.then(argument("mode", ArgumentTypeString.word()).suggests(HEALTH_MODES).executes(c -> {
						EntityNPC npc = findNpc(c);
						String name = ArgumentTypeString.getString(c, "mode");
						HealthMode mode = HealthMode.byName(name);
						if (mode == null) {
							throw error("Unknown mode '" + name + "'. Use mortal, invincible or invulnerable.");
						}

						npc.setHealthMode(mode);
						return reply(c, npc.nickname + " is now " + mode.displayName() + ".");
					}))))
			.then(literal("personality")
				.then(npcArgument()
					.executes(NpcCommand::showPersonality)
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
					.executes(NpcCommand::showLikes)
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
			.then(literal("faction")
				.then(literal("list").executes(NpcCommand::listFactions))
				.then(literal("create")
					.then(argument("faction", ArgumentTypeString.word())
						.executes(c -> createFaction(c, FactionRelation.NEUTRAL))
						.then(argument("stance", ArgumentTypeString.word()).suggests(RELATION_NAMES)
							.executes(c -> createFaction(c, requireRelation(c, "stance"))))))
				.then(literal("delete")
					.then(argument("faction", ArgumentTypeString.word()).suggests(FACTION_NAMES).executes(c -> {
						String faction = requireFaction(c, "faction");
						FactionRegistry.get(c.getSource().getWorld()).remove(faction);
						return reply(c, "Deleted the " + faction + ". Their players are unaffiliated; their NPCs still stand together.");
					})))
				.then(literal("default")
					.then(argument("faction", ArgumentTypeString.word()).suggests(FACTION_NAMES)
						.executes(c -> {
							String faction = requireFaction(c, "faction");
							FactionRelation stance = FactionRegistry.get(c.getSource().getWorld()).getDefault(faction);
							return reply(c, "The " + faction + " are " + describe(stance) + " toward anyone without an explicit relation.");
						})
						.then(argument("stance", ArgumentTypeString.word()).suggests(RELATION_NAMES).executes(c -> {
							String faction = requireFaction(c, "faction");
							FactionRelation stance = requireRelation(c, "stance");
							FactionRegistry.get(c.getSource().getWorld()).setDefault(faction, stance);
							return reply(c, "The " + faction + " are now " + describe(stance) + " toward anyone without an explicit relation.");
						}))))
				.then(literal("relation")
					.then(argument("a", ArgumentTypeString.word()).suggests(FACTION_NAMES)
						.then(argument("b", ArgumentTypeString.word()).suggests(FACTION_NAMES)
							.executes(NpcCommand::showFactionRelation)
							.then(argument("relation", ArgumentTypeString.word()).suggests(RELATION_OR_DEFAULT_NAMES).executes(c -> {
								String a = requireFaction(c, "a");
								String b = requireFaction(c, "b");
								if (a.equals(b)) {
									throw error("A faction is always allied with itself.");
								}

								FactionRelation relation = requireRelationOrDefault(c, "relation");
								FactionRegistry.get(c.getSource().getWorld()).setExplicit(a, b, relation);
								if (relation == null) {
									return reply(c, "The " + a + " and the " + b + " now treat each other by their default stances.");
								}

								return reply(c, "The " + a + " and the " + b + " are now " + describe(relation) + ".");
							})))))
				.then(literal("set")
					.then(npcArgument()
						.then(argument("faction", ArgumentTypeString.word()).suggests(FACTION_OR_NONE_NAMES).executes(c -> {
							EntityNPC npc = findNpc(c);
							String faction = requireFactionOrNone(c, "faction");
							npc.setFaction(faction);
							return reply(c, faction != null ? npc.nickname + " has joined the " + faction + "." : npc.nickname + " is now unaffiliated.");
						}))))
				.then(literal("join")
					.then(argument("faction", ArgumentTypeString.word()).suggests(FACTION_OR_NONE_NAMES).executes(c -> {
						Player player = requirePlayer(c.getSource());
						String faction = requireFactionOrNone(c, "faction");
						FactionRegistry.get(c.getSource().getWorld()).setPlayerFaction(player.uuid, faction);
						return reply(c, faction != null ? "You have joined the " + faction + "." : "You are now unaffiliated.");
					}))))
			.then(literal("relation")
				.then(npcArgument()
					.executes(NpcCommand::showPersonalRelation)
					.then(argument("relation", ArgumentTypeString.word()).suggests(RELATION_OR_DEFAULT_NAMES).executes(c -> {
						Player player = requirePlayer(c.getSource());
						EntityNPC npc = findNpc(c);
						FactionRelation relation = requireRelationOrDefault(c, "relation");
						npc.relations.set(player, relation);
						if (relation == null) {
							return reply(c, npc.nickname + " now sees you as its faction does: " + describePersonal(NpcFactions.regard(npc, player)) + ".");
						}

						return reply(c, npc.nickname + " is now " + describePersonal(relation) + ".");
					}))))
			.then(literal("opinion")
				.then(npcArgument()
					.executes(c -> showOpinion(c, requirePlayer(c.getSource())))
					.then(argument("target", ArgumentTypeString.string()).suggests(TARGET_NAMES)
						.executes(c -> showOpinion(c, findTarget(c))))))
			.then(literal("mood")
				.then(npcArgument().executes(NpcCommand::showMood)))
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
					return reply(c, "Gift and chat limits are on: one gift per NPC a day, three chats a minute.");
				}))
				.then(literal("off").executes(c -> {
					SocialLimits.setEnabled(false);
					return reply(c, "Gift and chat limits are off (until you turn them back on or restart).");
				})))
			.then(literal("speech")
				.then(literal("reload").executes(c -> reply(c, "Loaded " + SpeechLibrary.reload() + " speech lines."))))
			.then(literal("remove")
				.then(npcArgument().executes(c -> {
					EntityNPC npc = findNpc(c);
					npc.remove();
					return reply(c, "Removed " + npc.nickname + ".");
				})))
			.then(literal("list").executes(NpcCommand::list))
			.then(literal("family")
				.then(npcArgument()
					.executes(NpcCommand::showFamily)
					.then(literal("parent")
						.then(argument("target", ArgumentTypeString.string()).suggests(TARGET_NAMES).executes(c -> linkParent(c, true))))
					.then(literal("child")
						.then(argument("target", ArgumentTypeString.string()).suggests(TARGET_NAMES).executes(c -> linkParent(c, false))))
					.then(literal("unlink")
						.then(argument("target", ArgumentTypeString.string()).suggests(TARGET_NAMES).executes(c -> {
							EntityNPC npc = findNpc(c);
							Entity target = findTarget(c);
							UUID id = requireId(target);
							FamilyRegistry family = FamilyRegistry.get(npc.world);
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
						}).executes(NpcCommand::setCouple)))))
			.then(literal("baby")
				.then(npcArgument().executes(c -> {
					EntityNPC npc = findNpc(c);
					UUID partnerId = FamilyRegistry.get(npc.world).partnerOf(npc.getUuid());
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
						return reply(c, npc.nickname + " is " + Math.round(npc.getGrowth() * 100) + "% grown" + (npc.isChild() ? "." : " (an adult)."));
					})
					.then(argument("percent", ArgumentTypeInteger.integer(0, 100)).executes(c -> {
						EntityNPC npc = findNpc(c);
						boolean wasChild = npc.isChild();
						npc.setGrowth(c.getArgument("percent", Integer.class) / 100.0F);
						if (wasChild && !npc.isChild()) {
							Children.cameOfAge(npc);
						}

						return reply(c, npc.nickname + " is now " + Math.round(npc.getGrowth() * 100) + "% grown.");
					}))))
			.then(literal("attraction")
				.then(npcArgument()
					.executes(c -> {
						EntityNPC npc = findNpc(c);
						return reply(c, npc.nickname + " is attracted to: " + npc.personality.attraction.id() + " (relative to its gender).");
					})
					.then(argument("attraction", ArgumentTypeString.word()).suggests((c, builder) -> {
						for (Attraction attraction : Attraction.values()) {
							builder.suggest(attraction.id());
						}

						return builder.buildFuture();
					}).executes(c -> {
						EntityNPC npc = findNpc(c);
						Attraction attraction = Attraction.byName(ArgumentTypeString.getString(c, "attraction"));
						if (attraction == null) {
							throw error("Use opposite, same or both.");
						}

						npc.personality.attraction = attraction;
						return reply(c, npc.nickname + " is now attracted to: " + attraction.id() + ".");
					}))))
			.then(literal("look")
				.then(npcArgument()
					.executes(NpcCommand::describeLook)
					.then(literal("randomize").executes(c -> {
						EntityNPC npc = findNpc(c);
						npc.setAppearance(Appearance.random(PartCatalog.get(), npc.getGender(), npc.getRandom()));
						if (npc.getAppearance().isEmpty()) {
							throw error("There are no base parts yet. Put some in " + new File(PartCatalog.getDirectory(), "base"));
						}

						reply(c, npc.nickname + " has a new look.");
						if (!npc.getSkin().isEmpty()) {
							reply(c, "It has a skin file set, which shows instead. /npc skin " + npc.nickname + " default to use the look.");
						}

						return 1;
					}))
					.then(argument("layer", ArgumentTypeString.word()).suggests(LAYER_NAMES)
						.then(argument("part", ArgumentTypeString.word()).suggests(PART_NAMES)
							.executes(c -> setLook(c, ""))
							.then(argument("palette", ArgumentTypeString.word()).suggests(PALETTE_NAMES)
								.executes(c -> setLook(c, ArgumentTypeString.getString(c, "palette"))))))))
			.then(literal("parts")
				.executes(NpcCommand::listParts)
				.then(literal("reload").executes(c -> {
					PartCatalog catalog = PartCatalog.reload();
					if (!Global.isServer) {
						AppearanceTextures.reload();
					}

					return reply(c, "Loaded " + catalog.partCount() + " parts and " + catalog.paletteCount() + " palettes.");
				})))
			.then(literal("skins")
				.executes(NpcCommand::listSkins)
				.then(literal("reload").executes(c -> {
					if (!Global.isServer) {
						NpcSkins.reload();
					}

					return reply(c, "NPC skins will be reloaded from " + NpcSkinFiles.getDirectory());
				}))));
	}

	private static int showFamily(CommandContext<CommandSource> c) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		Romance.register(npc);
		FamilyRegistry family = FamilyRegistry.get(npc.world);
		FamilyRegistry.Partnership partnership = family.partnershipOf(npc.getUuid());
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
			FamilyRegistry.Person person = family.find(relative);
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
		FamilyRegistry family = FamilyRegistry.get(npc.world);
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
		FamilyRegistry family = FamilyRegistry.get(npc.world);
		long now = npc.world.getWorldTime();
		if (stage.equals("none")) {
			family.breakUp(npc.getUuid());
			return reply(c, npc.nickname + " is single now.");
		}

		FamilyRegistry.Stage next = switch (stage) {
			case "dating" -> FamilyRegistry.Stage.DATING;
			case "engaged" -> FamilyRegistry.Stage.ENGAGED;
			case "married" -> FamilyRegistry.Stage.MARRIED;
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

	private static void registerPerson(Entity entity) {
		if (entity instanceof EntityNPC other) {
			Romance.register(other);
		} else if (entity instanceof Player player) {
			Romance.register(player);
		}
	}

	private static UUID requireId(Entity entity) throws CommandSyntaxException {
		UUID id = Opinions.idOf(entity);
		if (id == null) {
			throw error("That's not a person.");
		}

		return id;
	}

	private static int describeLook(CommandContext<CommandSource> c) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		Appearance appearance = Appearance.decode(npc.getAppearance());
		if (appearance.isEmpty()) {
			return reply(c, npc.nickname + " has no look built from parts" + (npc.getSkin().isEmpty() ? "." : " (it uses the skin '" + npc.getSkin() + "')."));
		}

		reply(c, npc.nickname + "'s look" + (npc.getSkin().isEmpty() ? ":" : " (hidden by the skin '" + npc.getSkin() + "'):"));
		for (Map.Entry<AppearanceLayer, Appearance.Choice> entry : appearance.choices().entrySet()) {
			Appearance.Choice choice = entry.getValue();
			reply(c, "- " + entry.getKey().id() + ": " + choice.part() + (choice.palette().isEmpty() ? "" : " (" + choice.palette() + ")"));
		}

		return 1;
	}

	/** /npc look <npc> <layer> <part|none> [palette]: hair and facial hair keep sharing a color. */
	private static int setLook(CommandContext<CommandSource> c, String palette) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		AppearanceLayer layer = AppearanceLayer.byName(ArgumentTypeString.getString(c, "layer"));
		if (layer == null) {
			throw error("Unknown layer. Layers: base, eyes, mouth, facial_hair, bottom, top, hair, accessory.");
		}

		String part = ArgumentTypeString.getString(c, "part").toLowerCase(Locale.ROOT);
		PartCatalog catalog = PartCatalog.get();
		Appearance appearance = Appearance.decode(npc.getAppearance());
		if (part.equals("none")) {
			npc.setAppearance(appearance.with(layer, null, ""));
			return reply(c, npc.nickname + " has nothing on the " + layer.id() + " layer now.");
		}

		if (catalog.part(layer, part) == null) {
			throw error("No " + layer.id() + " part called '" + part + "'.");
		}

		if (palette.isEmpty()) {
			Appearance.Choice current = appearance.get(layer);
			palette = current != null ? current.palette() : "";
		}

		if (!palette.isEmpty() && (layer.paletteGroup == null || catalog.palette(layer.paletteGroup, palette) == null)) {
			throw error(layer.paletteGroup == null ? "The " + layer.id() + " layer isn't recolored." : "No " + layer.paletteGroup + " palette called '" + palette + "'.");
		}

		npc.setAppearance(appearance.with(layer, part, palette));
		return reply(c, npc.nickname + " now has " + part + (palette.isEmpty() ? "" : " (" + palette + ")") + " on the " + layer.id() + " layer.");
	}

	private static int listParts(CommandContext<CommandSource> c) {
		PartCatalog catalog = PartCatalog.get();
		reply(c, "Appearance parts (from the mod and " + PartCatalog.getDirectory() + "):");
		for (AppearanceLayer layer : AppearanceLayer.values()) {
			List<String> parts = catalog.allParts(layer);
			reply(c, "- " + layer.id() + ": " + (parts.isEmpty() ? "none yet" : String.join(", ", parts)));
		}

		for (Map.Entry<String, Map<String, int[]>> group : catalog.palettes().entrySet()) {
			reply(c, "- " + group.getKey() + " palettes: " + String.join(", ", group.getValue().keySet()));
		}

		return 1;
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

		npc.setHomeHere();
		npc.brain.set(new StayBehavior());
		npc.spawnInit();
		world.entityJoinedWorld(npc);

		String skinInfo = skin.isEmpty() ? "default skin" : "skin '" + skin + "'";
		if (!skin.isEmpty() && !NpcSkinFiles.exists(skin)) {
			skinInfo += ", which is not in " + NpcSkinFiles.FOLDER_NAME + " yet";
		}

		return reply(c, "Summoned " + name + ", " + factionLabel(npc.getFaction()) + " (" + skinInfo + ").");
	}

	private static double[] position(CommandContext<CommandSource> c) throws CommandSyntaxException {
		DoubleCoordinates position = c.getArgument("position", DoubleCoordinates.class);
		CommandSource source = c.getSource();
		return new double[]{position.getX(source), position.getY(source, true), position.getZ(source)};
	}

	private static String describe(double[] pos) {
		return String.format(Locale.ROOT, "%.0f %.0f %.0f", pos[0], pos[1], pos[2]);
	}

	/** Resolves a target like vanilla /teleport: @s is you, otherwise an NPC's name or a player's name. */
	private static Entity findTarget(CommandContext<CommandSource> c) throws CommandSyntaxException {
		String name = ArgumentTypeString.getString(c, "target");
		if (name.equals("@s")) {
			return requirePlayer(c.getSource());
		}

		for (EntityNPC npc : getNpcs(c.getSource().getWorld())) {
			if (npc.nickname.equalsIgnoreCase(name)) {
				return findNpcNamed(c, name);
			}
		}

		Player player = c.getSource().getWorld().getPlayerEntityByName(name);
		if (player == null) {
			throw error("No NPC or player named '" + name + "'.");
		}

		return player;
	}

	private static String nameOf(Entity entity) {
		if (entity instanceof EntityNPC npc) {
			return npc.nickname;
		}

		return entity instanceof Player player ? player.username : "there";
	}

	private static int goTo(CommandContext<CommandSource> c, double x, double y, double z, String where) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		npc.brain.set(new GoToBehavior(x, y, z));
		return reply(c, npc.nickname + " is heading to " + where + ".");
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
		npc.setHomeHere();
		npc.brain.set(new WanderBehavior(radius));
		return reply(c, npc.nickname + " will wander within " + radius + " blocks of here.");
	}

	private static int setSkin(CommandContext<CommandSource> c, SkinModel model) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		String skin = ArgumentTypeString.getString(c, "skin");
		npc.setSkin(skin);
		npc.setSkinModel(model);
		String message = npc.nickname + " now uses skin '" + skin + "' (" + model.name().toLowerCase(Locale.ROOT) + " arms).";
		if (!NpcSkinFiles.exists(skin)) {
			message += " Note: " + skin + ".png is not in " + NpcSkinFiles.getDirectory();
		}

		return reply(c, message);
	}

	private static int list(CommandContext<CommandSource> c) {
		List<EntityNPC> npcs = getNpcs(c.getSource().getWorld());
		if (npcs.isEmpty()) {
			return reply(c, "There are no NPCs loaded.");
		}

		c.getSource().sendMessage(npcs.size() + " NPC(s) loaded:");
		for (EntityNPC npc : npcs) {
			NpcBehavior active = npc.brain.getActive();
			String doing = active != null ? active.describe() : "idle";
			String skin = npc.getSkin().isEmpty() ? "default" : npc.getSkin();
			c.getSource().sendMessage(String.format(Locale.ROOT, "- %s (%s): %s, skin %s, health %d, %s style, faction %s, at %d %d %d%s",
				npc.nickname, npc.getGender().displayName().toLowerCase(Locale.ROOT), doing, skin, npc.getHealth(),
				npc.getCombatStyle().displayName().toLowerCase(Locale.ROOT), npc.getFaction() != null ? npc.getFaction() : "none",
				(int) Math.floor(npc.x), (int) Math.floor(npc.y), (int) Math.floor(npc.z),
				npc.getHealthMode() != HealthMode.MORTAL ? ", " + npc.getHealthMode().displayName() : ""));
		}

		return npcs.size();
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

	private static String describeLike(float like) {
		if (like >= 0.6F) {
			return "loves";
		} else if (like >= 0.2F) {
			return "likes";
		} else if (like > -0.2F) {
			return "doesn't mind";
		} else if (like > -0.6F) {
			return "dislikes";
		}

		return "hates";
	}

	private static String itemName(Item item) {
		return new ItemStack(item).getDisplayName().toLowerCase(Locale.ROOT);
	}

	private static Quirk requireQuirk(CommandContext<CommandSource> c) throws CommandSyntaxException {
		String name = ArgumentTypeString.getString(c, "quirk");
		Quirk quirk = Quirk.byName(name);
		if (quirk == null) {
			throw error("Unknown quirk '" + name + "'.");
		}

		return quirk;
	}

	private static Item requireHeldItem(CommandContext<CommandSource> c) throws CommandSyntaxException {
		ItemStack held = requirePlayer(c.getSource()).inventory.getCurrentItem();
		if (held == null) {
			throw error("Hold the item you want to ask about.");
		}

		return held.getItem();
	}

	private static int listFactions(CommandContext<CommandSource> c) {
		FactionRegistry registry = FactionRegistry.get(c.getSource().getWorld());
		c.getSource().sendMessage("Factions in this world:");
		for (String faction : registry.all()) {
			List<String> details = new ArrayList<>();
			details.add(describe(registry.getDefault(faction)) + " by default");
			for (Map.Entry<String, FactionRelation> relation : registry.explicitRelationsOf(faction).entrySet()) {
				details.add(describe(relation.getValue()) + " with " + relation.getKey());
			}

			int players = registry.countPlayers(faction);
			if (players > 0) {
				details.add(players + " player(s)");
			}

			c.getSource().sendMessage("- " + faction + ": " + String.join(", ", details));
		}

		Player player = c.getSource().getSender();
		if (player != null) {
			c.getSource().sendMessage("You are " + factionLabel(registry.getPlayerFaction(player.uuid)) + ".");
		}

		return registry.all().size();
	}

	private static int createFaction(CommandContext<CommandSource> c, FactionRelation stance) throws CommandSyntaxException {
		String faction = ArgumentTypeString.getString(c, "faction").toLowerCase(Locale.ROOT);
		if (!FactionRegistry.isValidId(faction) || faction.equals(NONE_KEYWORD)) {
			throw error("Faction names may only use a-z, 0-9 and _, up to 32 characters, and can't be 'none'.");
		}

		if (!FactionRegistry.get(c.getSource().getWorld()).create(faction, stance)) {
			throw error("The faction '" + faction + "' already exists.");
		}

		return reply(c, "Created the " + faction + ", " + describe(stance) + " toward anyone without an explicit relation.");
	}

	private static int showFactionRelation(CommandContext<CommandSource> c) throws CommandSyntaxException {
		String a = requireFaction(c, "a");
		String b = requireFaction(c, "b");
		FactionRegistry registry = FactionRegistry.get(c.getSource().getWorld());
		FactionRelation explicit = registry.getExplicit(a, b);
		if (a.equals(b)) {
			return reply(c, "A faction is always allied with itself.");
		}

		if (explicit != null) {
			return reply(c, "The " + a + " and the " + b + " are " + describe(explicit) + ".");
		}

		return reply(c, "No explicit relation: the " + a + " are " + describe(registry.regard(a, b)) + " toward the " + b
			+ ", who are " + describe(registry.regard(b, a)) + " toward them (their default stances).");
	}

	private static int showPersonalRelation(CommandContext<CommandSource> c) throws CommandSyntaxException {
		Player player = requirePlayer(c.getSource());
		EntityNPC npc = findNpc(c);
		FactionRelation personal = npc.relations.get(player);
		FactionRelation overall = NpcFactions.regard(npc, player);
		float opinion = Opinions.of(npc, player);
		String member = npc.getFaction() != null ? "one of the " + npc.getFaction() : "someone unaffiliated";
		String reason;
		if (personal != null) {
			reason = "set by command";
		} else if (opinion >= Opinions.ALLY_THRESHOLD || opinion <= Opinions.hostileThreshold(npc)) {
			reason = "because its opinion of you is " + Math.round(opinion);
		} else {
			reason = "as " + member + ", toward someone " + factionLabel(FactionRegistry.get(c.getSource().getWorld()).getPlayerFaction(player.uuid));
		}

		reply(c, npc.nickname + " is " + describePersonal(overall) + " (" + reason + ").");
		if (overall == FactionRelation.HOSTILE && !player.getGamemode().hasHostileMobs()) {
			reply(c, "You're in a game mode monsters ignore, so " + npc.nickname + " won't attack you either.");
		}

		return 1;
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

	private static String factionLabel(@Nullable String faction) {
		return faction != null ? "of the " + faction : "unaffiliated";
	}

	private static String describePersonal(FactionRelation relation) {
		return switch (relation) {
			case ALLY -> "your ally";
			case NEUTRAL -> "neutral toward you";
			case HOSTILE -> "hostile toward you";
		};
	}

	private static String describe(FactionRelation relation) {
		return switch (relation) {
			case ALLY -> "allied";
			case NEUTRAL -> "neutral";
			case HOSTILE -> "hostile";
		};
	}

	private static String requireFaction(CommandContext<CommandSource> c, String argument) throws CommandSyntaxException {
		String faction = ArgumentTypeString.getString(c, argument).toLowerCase(Locale.ROOT);
		FactionRegistry registry = FactionRegistry.get(c.getSource().getWorld());
		if (!registry.exists(faction)) {
			throw error("Unknown faction '" + faction + "'. Factions: " + String.join(", ", registry.all()));
		}

		return faction;
	}

	/** A faction name, or null for "none" (unaffiliated). */
	@Nullable
	private static String requireFactionOrNone(CommandContext<CommandSource> c, String argument) throws CommandSyntaxException {
		return ArgumentTypeString.getString(c, argument).equalsIgnoreCase(NONE_KEYWORD) ? null : requireFaction(c, argument);
	}

	private static FactionRelation requireRelation(CommandContext<CommandSource> c, String argument) throws CommandSyntaxException {
		String name = ArgumentTypeString.getString(c, argument);
		FactionRelation relation = FactionRelation.byName(name);
		if (relation == null) {
			throw error("Unknown relation '" + name + "'. Use ally, neutral or hostile.");
		}

		return relation;
	}

	/** A relation, or null for "default" (remove the explicit setting). */
	@Nullable
	private static FactionRelation requireRelationOrDefault(CommandContext<CommandSource> c, String argument) throws CommandSyntaxException {
		return ArgumentTypeString.getString(c, argument).equalsIgnoreCase(DEFAULT_KEYWORD) ? null : requireRelation(c, argument);
	}

	private static int listSkins(CommandContext<CommandSource> c) {
		List<String> skins = NpcSkinFiles.list();
		if (skins.isEmpty()) {
			return reply(c, "No skins found. Put 64x64 PNG skins in " + NpcSkinFiles.getDirectory());
		}

		return reply(c, "Skins: " + String.join(", ", skins));
	}

	// Helpers

	private static List<EntityNPC> getNpcs(World world) {
		List<EntityNPC> npcs = new ArrayList<>();
		if (world != null) {
			for (Entity entity : world.getLoadedEntityList()) {
				if (entity instanceof EntityNPC npc && !npc.removed) {
					npcs.add(npc);
				}
			}
		}

		return npcs;
	}

	/** Finds the NPC named in the "npc" argument; with duplicates, the nearest one wins. */
	private static EntityNPC findNpc(CommandContext<CommandSource> c) throws CommandSyntaxException {
		return findNpcNamed(c, ArgumentTypeString.getString(c, "npc"));
	}

	private static EntityNPC findNpcNamed(CommandContext<CommandSource> c, String name) throws CommandSyntaxException {
		Player player = c.getSource().getSender();
		EntityNPC best = null;
		double bestDistance = Double.MAX_VALUE;
		for (EntityNPC npc : getNpcs(c.getSource().getWorld())) {
			if (!npc.nickname.equalsIgnoreCase(name)) {
				continue;
			}

			double distance = player != null ? npc.distanceToSqr(player) : 0.0;
			if (distance < bestDistance) {
				best = npc;
				bestDistance = distance;
			}
		}

		if (best == null) {
			throw error("No loaded NPC named '" + name + "'. Try /npc list.");
		}

		return best;
	}

	private static Player requirePlayer(CommandSource source) throws CommandSyntaxException {
		Player player = source.getSender();
		if (player == null) {
			throw error("This command must be run by a player.");
		}

		return player;
	}

	private static int reply(CommandContext<CommandSource> c, String message) {
		c.getSource().sendMessage(message);
		return 1;
	}

	private static CommandSyntaxException error(String message) {
		return new SimpleCommandExceptionType(() -> message).create();
	}

	private static ArgumentBuilderLiteral<CommandSource> literal(String name) {
		return ArgumentBuilderLiteral.literal(name);
	}

	private static <T> ArgumentBuilderRequired<CommandSource, T> argument(String name, ArgumentType<T> type) {
		return ArgumentBuilderRequired.argument(name, type);
	}

	private static ArgumentBuilderRequired<CommandSource, String> npcArgument() {
		return NpcCommand.<String>argument("npc", ArgumentTypeString.string()).suggests(NPC_NAMES);
	}
}
