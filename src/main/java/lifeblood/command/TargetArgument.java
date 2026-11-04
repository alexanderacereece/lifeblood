package lifeblood.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.ArgumentTypeString;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.net.command.CommandSource;
import net.minecraft.core.net.command.arguments.ArgumentTypeEntity;
import net.minecraft.core.net.command.exceptions.CommandExceptions;
import net.minecraft.core.net.command.helpers.EntitySelector;
import net.minecraft.core.net.command.helpers.EntitySelectorParser;
import lifeblood.entity.EntityNPC;
import org.jetbrains.annotations.Nullable;

/**
 * A command argument naming NPCs, or people (NPCs and players), the way vanilla commands name entities, plus NPC names:
 * - any vanilla selector: @s, @p, @a, @r, @e, with options like @e[type=lifeblood:npc,name=Walter,limit=1,sort=nearest];
 * - an entity id as vanilla writes it (entity.12345);
 * - a name: an NPC's name (quoted if it has spaces: "Walter White"), or a player's username.
 * Like vanilla, the single forms refuse a selector that could match more than one; a name matching several NPCs picks the
 * one nearest to whoever ran the command.
 */
final class TargetArgument implements ArgumentType<TargetArgument.Selection> {
	private static final Collection<String> EXAMPLES = Arrays.asList("Walter", "\"Walter White\"", "@s", "@e[type=lifeblood:npc,limit=1,sort=nearest]");
	private static final String NPC_SELECTOR = "@e[type=lifeblood:npc]";

	private final boolean single;
	private final boolean npcsOnly;

	private TargetArgument(boolean single, boolean npcsOnly) {
		this.single = single;
		this.npcsOnly = npcsOnly;
	}

	/** One or more NPCs. */
	static TargetArgument npcs() {
		return new TargetArgument(false, true);
	}

	/** Exactly one NPC. */
	static TargetArgument npc() {
		return new TargetArgument(true, true);
	}

	/** One or more people: NPCs and players. */
	static TargetArgument people() {
		return new TargetArgument(false, false);
	}

	/** Exactly one person: an NPC or a player. */
	static TargetArgument person() {
		return new TargetArgument(true, false);
	}

	/** What was written: a vanilla selector, or a name. */
	record Selection(@Nullable EntitySelector selector, @Nullable String name, boolean single, boolean npcsOnly) {
		/** The NPCs (or people) this stands for right now; throws if there are none. */
		List<Entity> resolve(CommandSource source) throws CommandSyntaxException {
			List<Entity> found = new ArrayList<>();
			if (this.selector != null) {
				for (Entity entity : this.selector.get(source)) {
					if (entity instanceof EntityNPC npc && !npc.removed || !this.npcsOnly && entity instanceof Player) {
						found.add(entity);
					}
				}
			} else {
				for (EntityNPC npc : CommandSupport.getNpcs(source.getWorld())) {
					if (npc.nickname.equalsIgnoreCase(this.name)) {
						found.add(npc);
					}
				}

				if (!this.npcsOnly) {
					for (Player player : source.getWorld().players) {
						if (player.username.equalsIgnoreCase(this.name) || this.name.equalsIgnoreCase(player.nickname)) {
							found.add(player);
						}
					}
				}

				Player sender = source.getSender();
				if (sender != null) {
					found.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(sender)));
				}
			}

			if (found.isEmpty()) {
				String what = this.npcsOnly ? "NPC" : "NPC or player";
				throw CommandSupport.error(this.name != null ? "No loaded " + what + " is called " + this.name + ". Try /lb npc list."
					: "That selector matched no " + what + "s.");
			}

			return this.single ? found.subList(0, 1) : found;
		}
	}

	@Override
	public Selection parse(StringReader reader) throws CommandSyntaxException {
		int cursor = reader.getCursor();
		if (reader.canRead() && (reader.peek() == '@' || reader.getRemaining().startsWith(EntitySelector.ENTITY_PREFIX))) {
			EntitySelector selector = new EntitySelectorParser(reader, true).parse();
			if (this.single && selector.getMaxResults() > 1) {
				reader.setCursor(cursor);
				throw CommandExceptions.singleEntityOnly().createWithContext(reader);
			}

			return new Selection(selector, null, this.single, this.npcsOnly);
		}

		String name = reader.readString();
		if (name.isEmpty()) {
			reader.setCursor(cursor);
			throw CommandSupport.error("Expected an NPC's name or a selector like @s.");
		}

		return new Selection(null, name, this.single, this.npcsOnly);
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
		String typed = builder.getRemaining();
		if (typed.startsWith("@")) {
			// Vanilla knows selectors and their options best.
			return ArgumentTypeEntity.entities().listSuggestions(context, builder);
		}

		if (!(context.getSource() instanceof CommandSource source)) {
			return Suggestions.empty();
		}

		String lower = typed.toLowerCase(Locale.ROOT);
		List<String> options = new ArrayList<>();
		for (EntityNPC npc : CommandSupport.getNpcs(source.getWorld())) {
			options.add(ArgumentTypeString.escapeIfRequired(npc.nickname));
		}

		if (!this.npcsOnly) {
			for (Player player : source.getWorld().players) {
				options.add(player.username);
			}

			options.add("@s");
			options.add("@p");
		}

		options.add(this.single ? "@e[type=lifeblood:npc,limit=1,sort=nearest]" : NPC_SELECTOR);
		for (String option : options) {
			if (option.toLowerCase(Locale.ROOT).startsWith(lower) || option.toLowerCase(Locale.ROOT).startsWith("\"" + lower)) {
				builder.suggest(option);
			}
		}

		return builder.buildFuture();
	}

	@Override
	public Collection<String> getExamples() {
		return EXAMPLES;
	}
}
