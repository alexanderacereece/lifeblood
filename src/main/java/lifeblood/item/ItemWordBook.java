package lifeblood.item;

import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.world.World;
import lifeblood.ClientHooks;
import org.jetbrains.annotations.NotNull;

/** Opens the word book: every word players have taught NPCs, who knows each, and a way to delete them. */
public class ItemWordBook extends Item {
	public ItemWordBook(String translationKey, String namespaceId, int id) {
		super(translationKey, namespaceId, id);
	}

	@Override
	public ItemStack onUse(@NotNull ItemStack selfStack, @NotNull World world, @NotNull Player player) {
		ClientHooks.openWordBook(player);
		return selfStack;
	}
}
