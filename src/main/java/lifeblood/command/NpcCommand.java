package lifeblood.command;

import static lifeblood.command.CommandSupport.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import net.minecraft.core.net.command.CommandManager;
import net.minecraft.core.net.command.CommandSource;

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
 * /npc free <name>          (drops its orders: it goes about its routine around where it is)
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
 * /npc limits [on|off]                        (debug: the one-gift-a-day and five-chats-a-minute limits)
 * /npc list
 * /npc family <name>                           (its partner and relatives)
 * /npc family <name> parent|child <target>     (records that the target is its parent, or its child)
 * /npc family <name> unlink <target>           (removes a parent/child link between them)
 * /npc couple <name> <target> dating|engaged|married|none   (debug: sets their relationship, clock starting now)
 * /npc words                                   (every word players have taught, with ids)
 * /npc words delete <id>                       (removes a word from everyone who knows it)
 * /npc baby <name>                             (debug: it and its partner have a child now)
 * /npc grow <name> [0..100]                    (how grown up it is; children grow up over 18 years)
 * /npc look <name>                             (the parts and colors its look is built from)
 * /npc look <name> randomize                   (a new random look from the parts available)
 * /npc look <name> <layer> <part|none> [palette]   (e.g. hair bob auburn)
 * /npc parts [reload]                          (lists appearance parts and palettes, or reads them again)
 * /npc skins [reload]
 *
 * Names with spaces need quotes: /npc follow "Walter White"
 */
public class NpcCommand implements CommandManager.CommandRegistry {
	@Override
	public void register(CommandDispatcher<CommandSource> dispatcher) {
		ArgumentBuilderLiteral<CommandSource> npc = literal("npc").requires(CommandSource::hasAdmin);
		NpcBasicsCommands.addTo(npc);
		PersonalityCommands.addTo(npc);
		FactionCommands.addTo(npc);
		FamilyCommands.addTo(npc);
		LookCommands.addTo(npc);
		OwnershipCommands.addTo(npc);
		dispatcher.register(npc);
	}
}
