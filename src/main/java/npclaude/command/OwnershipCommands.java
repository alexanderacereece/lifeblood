package npclaude.command;

import static npclaude.command.CommandSupport.*;

import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import java.util.UUID;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.net.command.CommandSource;
import net.minecraft.core.net.command.arguments.ArgumentTypeVector3d;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.World;
import npclaude.ownership.BlockOwners;
import npclaude.people.PeopleRegistry;

/** /npc owner: who placed a block, for checking ownership records. */
final class OwnershipCommands {
	static void addTo(ArgumentBuilderLiteral<CommandSource> root) {
		root
			.then(literal("owner")
				.then(argument("position", ArgumentTypeVector3d.vec3d()).executes(c -> {
					double[] pos = position(c);
					World world = c.getSource().getWorld();
					int x = MathHelper.floor(pos[0]);
					int y = MathHelper.floor(pos[1]);
					int z = MathHelper.floor(pos[2]);
					UUID owner = BlockOwners.ownerAt(world, x, y, z);
					String where = x + " " + y + " " + z;
					return reply(c, owner == null ? "Nobody owns the block at " + where + "." : ownerName(world, owner) + " owns the block at " + where + ".");
				})));
	}

	private static String ownerName(World world, UUID owner) {
		for (Player player : world.players) {
			if (owner.equals(player.uuid)) {
				return player.username;
			}
		}

		PeopleRegistry.Person person = PeopleRegistry.get(world).find(owner);
		return person != null && person.name != null ? person.name : "Someone not around (" + owner + ")";
	}
}
