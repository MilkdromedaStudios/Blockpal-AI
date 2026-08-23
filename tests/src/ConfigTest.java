import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.agent.Tempo;
import com.milkdromeda.blockpal.combat.CombatSkill;
import java.nio.file.*;

/** Config schema 14: defaults, migration from 13, and the clamps normalize() enforces. */
public class ConfigTest {
    static int pass = 0, fail = 0;
    static void check(String what, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("  ok   " + what + (detail.isEmpty()?"":"  ("+detail+")")); }
        else { fail++; System.out.println("  FAIL " + what + "  " + detail); }
    }
    static Path dir;

    static void writeConfig(String json) throws Exception {
        Files.createDirectories(dir.resolve("blockpal"));
        Files.writeString(dir.resolve("blockpal").resolve("config.json"), json);
    }

    public static void main(String[] a) throws Exception {
        dir = Files.createTempDirectory("bp-cfg");
        System.setProperty("blockpal.test.config", dir.toString());

        System.out.println("Fresh install defaults");
        ModConfig.load();
        ModConfig c = ModConfig.get();
        // Assert against the constant, not a literal: the property under test is
        // "a fresh install lands on the current schema", which stays true across bumps.
        check("schema version is current", c.configVersion == ModConfig.CURRENT_CONFIG_VERSION,
                c.configVersion + " (current is " + ModConfig.CURRENT_CONFIG_VERSION + ")");
        check("reactionSpeed defaults to fast", "fast".equals(c.reactionSpeed), c.reactionSpeed);
        check("Tempo resolves it", Tempo.current() == Tempo.FAST, Tempo.current().id());
        check("actionTickDelay default dropped 8 -> 2", c.actionTickDelay == 2, "" + c.actionTickDelay);
        check("PvP is OFF on a fresh install", !c.allowPvp, "");
        check("combatSkill defaults to skilled", CombatSkill.current() == CombatSkill.SKILLED, c.combatSkill);
        check("PVT on, recording NOT auto-consented", c.pvtEnabled && c.pvtAutoRecord, "");
        check("pvtConfidence default", Math.abs(c.pvtConfidence - 0.30) < 1e-9, "" + c.pvtConfidence);

        System.out.println("\nUpgrading an older install (schema 13)");
        writeConfig("{\"configVersion\":13,\"hfToken\":\"\",\"hfTokenObf\":\"\",\"actionTickDelay\":8,"
                  + "\"aiConnection\":\"free\",\"mcpPort\":8000,\"defaultName\":\"Ethan\"}");
        ModConfig.load();
        c = ModConfig.get();
        check("migrated to the current schema", c.configVersion == ModConfig.CURRENT_CONFIG_VERSION,
                c.configVersion + " (current is " + ModConfig.CURRENT_CONFIG_VERSION + ")");
        check("PvP still OFF after an upgrade", !c.allowPvp, "an upgrade must never enable it");
        check("reactionSpeed filled in", "fast".equals(c.reactionSpeed), c.reactionSpeed);
        check("shipped 8-tick delay moved to the new default", c.actionTickDelay == 2, "" + c.actionTickDelay);
        check("PVT defaults filled (not Java's false/0)", c.pvtEnabled && c.pvtEpochs == 24
                && c.pvtHiddenSize == 192, c.pvtEpochs + "/" + c.pvtHiddenSize);
        check("existing connection preserved", "free".equals(c.aiConnection), c.aiConnection);

        System.out.println("\nA hand-raised developer delay is respected");
        writeConfig("{\"configVersion\":13,\"actionTickDelay\":20,\"aiConnection\":\"free\"}");
        ModConfig.load();
        check("a deliberate 20-tick delay is left alone", ModConfig.get().actionTickDelay == 20,
                "" + ModConfig.get().actionTickDelay);
        check("Tempo honours it as a floor", Tempo.stepDelayTicks() == 20, "" + Tempo.stepDelayTicks());

        System.out.println("\nGarbage values are clamped, not obeyed");
        writeConfig("{\"configVersion\":14,\"reactionSpeed\":\"ludicrous\",\"combatSkill\":\"godlike\","
                  + "\"pvtHiddenSize\":99999,\"pvtEpochs\":-4,\"pvtLearningRate\":50.0,"
                  + "\"pvtMaxFrames\":3,\"pvtConfidence\":8.5,\"aiConnection\":\"free\"}");
        ModConfig.load();
        c = ModConfig.get();
        check("bad reactionSpeed -> fast", "fast".equals(c.reactionSpeed), c.reactionSpeed);
        check("bad combatSkill -> skilled", "skilled".equals(c.combatSkill), c.combatSkill);
        check("hidden size clamped to 512", c.pvtHiddenSize == 512, "" + c.pvtHiddenSize);
        check("epochs clamped up from -4", c.pvtEpochs >= 1 && c.pvtEpochs <= 200, "" + c.pvtEpochs);
        check("learning rate clamped", c.pvtLearningRate <= 0.1, "" + c.pvtLearningRate);
        check("max frames clamped up from 3", c.pvtMaxFrames >= 1000, "" + c.pvtMaxFrames);
        check("confidence clamped into 0..1", c.pvtConfidence >= 0 && c.pvtConfidence <= 1, "" + c.pvtConfidence);

        System.out.println("\naiLogicMode accepts the new pvt value");
        for (String mode : new String[]{"code", "plan", "pvt"}) {
            writeConfig("{\"configVersion\":14,\"aiLogicMode\":\"" + mode + "\",\"aiConnection\":\"free\"}");
            ModConfig.load();
            check("aiLogicMode '" + mode + "' survives a load",
                    mode.equals(ModConfig.get().aiLogicMode), ModConfig.get().aiLogicMode);
        }
        writeConfig("{\"configVersion\":14,\"aiLogicMode\":\"nonsense\",\"aiConnection\":\"free\"}");
        ModConfig.load();
        check("an unknown thinking style falls back to code",
                "code".equals(ModConfig.get().aiLogicMode), ModConfig.get().aiLogicMode);

        System.out.println("\nSaving keeps the new fields");
        writeConfig("{\"configVersion\":14,\"aiConnection\":\"free\"}");
        ModConfig.load();
        ModConfig.get().reactionSpeed = "instant";
        ModConfig.get().allowPvp = true;
        ModConfig.get().combatSkill = "expert";
        ModConfig.save();
        ModConfig.load();
        c = ModConfig.get();
        check("reactionSpeed persisted", "instant".equals(c.reactionSpeed), c.reactionSpeed);
        check("allowPvp persisted", c.allowPvp, "");
        check("combatSkill persisted", "expert".equals(c.combatSkill), c.combatSkill);
        check("Tempo picks up instant", Tempo.current() == Tempo.INSTANT, Tempo.current().id());
        check("instant means no step delay", Tempo.stepDelayTicks() == 0, "" + Tempo.stepDelayTicks());
        check("instant turns fast", Tempo.current().turnRate() >= 180f, "" + Tempo.current().turnRate());
        check("human keeps the old 22deg turn", Tempo.HUMAN.turnRate() == 22f, "" + Tempo.HUMAN.turnRate());
        check("only instant speeds up mining", Tempo.FAST.miningMultiplier() == 1f
                && Tempo.HUMAN.miningMultiplier() == 1f && Tempo.INSTANT.miningMultiplier() > 1f, "");

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
