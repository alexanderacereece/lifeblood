package npclaude.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.nbt.tags.CompoundTag;
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
	void currentSavesAreLeftAlone() {
		CompoundTag npc = new CompoundTag();
		npc.putString("HealthMode", "MORTAL");
		npc.putString("Faction", "raiders");
		SaveUpgrades.npc(npc);
		assertEquals("MORTAL", npc.getString("HealthMode"));
		assertEquals("raiders", npc.getString("Faction"));
	}
}
