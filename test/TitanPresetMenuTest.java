import gameserver.engine.Masteries;
import gameserver.engine.TitanPresetMenu;
import gameserver.engine.TitanPresetMenu.TitanPreset;
import gameserver.entity.Titan;
import gameserver.entity.TitanType;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class TitanPresetMenuTest {

    @Test
    public void testAllCuratedPresetsValid() {
        Random rng = new Random(42);
        Assert.assertEquals("Curated outfield presets must have 11 entries", 11, TitanPresetMenu.CURATED_OUTFIELD_PRESETS.size());
        Assert.assertEquals("Curated goalie presets must have 2 entries", 2, TitanPresetMenu.CURATED_GOALIE_PRESETS.size());

        List<TitanPreset> allCurated = new ArrayList<>(TitanPresetMenu.CURATED_OUTFIELD_PRESETS);
        allCurated.addAll(TitanPresetMenu.CURATED_GOALIE_PRESETS);

        for (TitanPreset preset : allCurated) {
            Assert.assertNotNull("Preset name must not be null", preset.name);
            Assert.assertEquals("Preset must define exactly 3 tier-3 masteries", 3, preset.tierThreeIndices.length);

            Masteries m = preset.createMasteries(rng);
            Assert.assertEquals("Masteries must pass validation (sum=10, max=3)", 0, m.validate());

            int[] arr = m.asArray();
            int totalPoints = 0;
            int tierThreeCount = 0;
            for (int val : arr) {
                totalPoints += val;
                if (val == 3) tierThreeCount++;
            }
            Assert.assertEquals("Mastery point sum must be 10", 10, totalPoints);
            Assert.assertEquals("Must have exactly 3 tier-3 masteries", 3, tierThreeCount);

            for (int t3Idx : preset.tierThreeIndices) {
                Assert.assertEquals("Specified tier-3 index " + t3Idx + " must have 3 points", 3, arr[t3Idx]);
            }

            Titan t = new Titan();
            preset.applyToTitan(t, rng);
            Assert.assertEquals("Titan presetName must match preset name", preset.name, t.presetName);
            Assert.assertTrue("Titan masteries must be locked after apply", t.typeAndMasteriesLocked);
        }
    }

    @Test
    public void testAllTopThreePresetsValid() {
        Random rng = new Random(123);
        Assert.assertTrue("Must have top-3 presets for outfield classes", TitanPresetMenu.TOPTHREE_OUTFIELD_PRESETS.size() >= 12);
        Assert.assertTrue("Must have top-3 presets for goalie", TitanPresetMenu.TOPTHREE_GOALIE_PRESETS.size() >= 1);

        List<TitanPreset> allTopThree = new ArrayList<>(TitanPresetMenu.TOPTHREE_OUTFIELD_PRESETS);
        allTopThree.addAll(TitanPresetMenu.TOPTHREE_GOALIE_PRESETS);

        for (TitanPreset preset : allTopThree) {
            Assert.assertTrue("Top-3 preset must start with TOPTHREE_", preset.name.startsWith("TOPTHREE_"));
            Assert.assertNotNull("Preset dataComment must document preliminary winrate data", preset.dataComment);
            Assert.assertTrue("dataComment must mention preliminary data", preset.dataComment.contains("preliminary data"));

            Masteries m = preset.createMasteries(rng);
            Assert.assertEquals("Top-3 masteries must validate cleanly (sum=10)", 0, m.validate());
        }
    }

    @Test
    public void testDistinctCaptainAndRangerPresets() {
        TitanPreset captain = TitanPresetMenu.findByName("DPS_CAPTAIN");
        Assert.assertNotNull("DPS_CAPTAIN must exist in menu", captain);
        Assert.assertEquals(TitanType.CAPTAIN, captain.defaultType);
        Assert.assertArrayEquals(new int[]{7, 4, 2}, captain.tierThreeIndices);

        TitanPreset ranger = TitanPresetMenu.findByName("DPS_RANGER");
        Assert.assertNotNull("DPS_RANGER must exist in menu", ranger);
        Assert.assertEquals(TitanType.RANGER, ranger.defaultType);
        Assert.assertArrayEquals(new int[]{7, 4, 2}, ranger.tierThreeIndices);
    }

    @Test
    public void testFindByNameLookup() {
        Assert.assertNotNull(TitanPresetMenu.findByName("MAX_STEALTH"));
        Assert.assertNotNull(TitanPresetMenu.findByName("RIM_RUNNER_GOLEM"));
        Assert.assertNotNull(TitanPresetMenu.findByName("SHOT_MARKSMAN"));
        Assert.assertNotNull(TitanPresetMenu.findByName("DPS_CAPTAIN"));
        Assert.assertNotNull(TitanPresetMenu.findByName("DPS_RANGER"));
        Assert.assertNotNull(TitanPresetMenu.findByName("SPEED_WARRIOR"));
        Assert.assertNotNull(TitanPresetMenu.findByName("DPS_WARRIOR"));
        Assert.assertNotNull(TitanPresetMenu.findByName("WEBSPINNER"));
        Assert.assertNotNull(TitanPresetMenu.findByName("OUTLET_GOALIE"));
        Assert.assertNotNull(TitanPresetMenu.findByName("MAXDEF_GOALIE"));
        Assert.assertNotNull(TitanPresetMenu.findByName("BURST_MAGE"));
        Assert.assertNotNull(TitanPresetMenu.findByName("SUPPORTIVE_SUPPORT"));
        Assert.assertNotNull(TitanPresetMenu.findByName("FORECHECK_ARTISAN"));

        Assert.assertNotNull(TitanPresetMenu.findByName("TOPTHREE_GOLEM"));
        Assert.assertNotNull(TitanPresetMenu.findByName("TOPTHREE_WARRIOR"));
        Assert.assertNotNull(TitanPresetMenu.findByName("TOPTHREE_GOALIE"));
    }
}
