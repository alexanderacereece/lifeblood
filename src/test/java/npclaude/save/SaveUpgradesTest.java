package npclaude.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.nbt.tags.CompoundTag;
import com.mojang.nbt.tags.ListTag;
import com.mojang.nbt.tags.Tag;
import org.junit.jupiter.api.Test;

class SaveUpgradesTest {
	@Test
	void versionOneBraveryBecomesCourage() {
		CompoundTag npc = new CompoundTag();
		npc.putFloat("Bravery", 0.75F);
		SaveUpgrades.npc(npc);
		CompoundTag axes = npc.getCompound("Personality").getCompound("Axes");
		assertEquals(0.5F, axes.getFloat("courage"), 1e-6);
	}

	@Test
	void invulnerableFlagBecomesAHealthMode() {
		CompoundTag npc = new CompoundTag();
		npc.putBoolean("Invulnerable", true);
		SaveUpgrades.npc(npc);
		assertEquals("INVULNERABLE", npc.getString("HealthMode"));
	}

	@Test
	void defaultFactionBecomesSettlers() {
		CompoundTag npc = new CompoundTag();
		npc.putString("Faction", "default");
		SaveUpgrades.npc(npc);
		assertEquals("settlers", npc.getString("Faction"));
	}

	@Test
	void orientationBecomesAQuirk() {
		CompoundTag personality = new CompoundTag();
		personality.putString("Quirks", "glutton");
		personality.putString("Attraction", "same");
		SaveUpgrades.personality(personality);
		assertEquals("glutton,gay", personality.getString("Quirks"));
	}

	@Test
	void foodPreferencesBecomeTastes() {
		CompoundTag personality = new CompoundTag();
		personality.putCompound("Food", new CompoundTag());
		SaveUpgrades.personality(personality);
		assertTrue(personality.containsKey("Tastes"));
	}

	@Test
	void defaultWanderingBecomesTheRoutine() {
		CompoundTag npc = new CompoundTag();
		ListTag behaviors = new ListTag();
		CompoundTag wander = new CompoundTag();
		wander.putString("Id", "wander");
		wander.putInt("Radius", 8);
		CompoundTag farWander = new CompoundTag();
		farWander.putString("Id", "wander");
		farWander.putInt("Radius", 30);
		behaviors.addTag(wander);
		behaviors.addTag(farWander);
		npc.putList("Behaviors", behaviors);
		SaveUpgrades.npc(npc);
		int left = 0;
		for (Tag<?> entry : npc.getList("Behaviors")) {
			assertEquals(30, ((CompoundTag) entry).getInteger("Radius"));
			left++;
		}

		assertEquals(1, left);
	}

	@Test
	void currentSavesAreLeftAlone() {
		CompoundTag npc = new CompoundTag();
		npc.putString("HealthMode", "MORTAL");
		npc.putString("Faction", "raiders");
		SaveUpgrades.npc(npc);
		assertEquals("MORTAL", npc.getString("HealthMode"));
		assertEquals("raiders", npc.getString("Faction"));
	}
}
