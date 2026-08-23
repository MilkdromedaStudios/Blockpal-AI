import com.milkdromeda.blockpal.ai.AiConnection;
import com.milkdromeda.blockpal.ai.LocalModel;
import com.milkdromeda.blockpal.network.ConfigData;
import com.milkdromeda.blockpal.config.ModConfig;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/**
 * The local-model feature's two load-bearing promises:
 *   1. nothing Blockpal offers to download is over 3 GB, and
 *   2. agreeing to a download is something a person does at a prompt — never something
 *      a settings packet can do on their behalf.
 */
public class LocalAiTest {
    static int pass = 0, fail = 0;
    static void check(String what, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("  ok   " + what + (detail.isEmpty()?"":"  ("+detail+")")); }
        else { fail++; System.out.println("  FAIL " + what + "  " + detail); }
    }

    public static void main(String[] a) throws Exception {
        System.out.println("The 3 GB promise");
        for (LocalModel m : LocalModel.values()) {
            check(m.id() + " is under 3 GB", m.bytes() < LocalModel.MAX_BYTES,
                    m.sizeText() + " — " + m.display());
        }
        check("MAX_BYTES really is 3 GB", LocalModel.MAX_BYTES == 3L*1024*1024*1024,
                LocalModel.MAX_BYTES + "");
        check("there is more than one to choose from", LocalModel.values().length >= 3,
                LocalModel.values().length + " models");

        System.out.println("\nCatalogue sanity");
        Set<String> ids = new HashSet<>();
        boolean urlsOk = true, dupes = false;
        for (LocalModel m : LocalModel.values()) {
            if (!ids.add(m.id())) dupes = true;
            String u = m.downloadUrl();
            if (!u.startsWith("https://huggingface.co/") || !u.contains("/resolve/main/")
                    || !u.contains(m.fileName())) {
                urlsOk = false;
                System.out.println("      bad url: " + u);
            }
            if (!m.fileName().endsWith(".gguf")) urlsOk = false;
        }
        check("ids are unique", !dupes, "");
        check("every download URL is a HuggingFace GGUF resolve link", urlsOk, "");
        check("byId round-trips", LocalModel.byId("qwen3b") == LocalModel.QWEN3B, "");
        check("byId is forgiving about case/space", LocalModel.byId("  QWEN3B ") == LocalModel.QWEN3B, "");
        check("byId rejects nonsense", LocalModel.byId("gpt-5-ultra") == null, "");
        check("there is a default", LocalModel.defaultModel() != null,
                LocalModel.defaultModel().display());
        check("the default is under 2 GB so it fits a 4 GB card",
                LocalModel.defaultModel().bytes() < 2L*1024*1024*1024,
                LocalModel.defaultModel().sizeText());

        System.out.println("\nThe LOCAL connection");
        check("'local' resolves to the new connection", AiConnection.byId("local") == AiConnection.LOCAL, "");
        check("'localgpt' and 'gpu' are aliases for it",
                AiConnection.byId("localgpt") == AiConnection.LOCAL
                && AiConnection.byId("gpu") == AiConnection.LOCAL, "");
        check("'lmstudio' still means Ollama", AiConnection.byId("lmstudio") == AiConnection.OLLAMA, "");
        check("the old free service still exists for servers on it",
                AiConnection.byId("free") == AiConnection.FREE, "");
        check("LOCAL talks to a model", AiConnection.LOCAL.usesModel(), "");

        System.out.println("\nConsent cannot be granted by a settings packet");
        Path dir = Files.createTempDirectory("bp-local");
        System.setProperty("blockpal.test.config", dir.toString());
        Files.createDirectories(dir.resolve("blockpal"));
        Files.writeString(dir.resolve("blockpal/config.json"),
                "{\"configVersion\":15,\"aiConnection\":\"local\",\"localConsented\":false}");
        ModConfig.load();
        check("a fresh install has not consented", !ModConfig.get().localConsented, "");

        // Forge a packet that says "yes, they agreed" and apply it, as a modified client would.
        RecordComponent[] comps = ConfigData.class.getRecordComponents();
        Constructor<?> ctor = ConfigData.class.getDeclaredConstructors()[0];
        Object[] vals = new Object[comps.length];
        for (int i = 0; i < comps.length; i++) {
            Class<?> t = comps[i].getType();
            String n = comps[i].getName();
            if (t == boolean.class) vals[i] = n.equals("localConsented");
            else if (t == int.class) vals[i] = n.equals("localPort") ? 9999
                                    : n.equals("localContext") ? 8192 : 1;
            else if (t == double.class) vals[i] = 0.5;
            else if (n.equals("localModelId")) vals[i] = "qwen1.5b";
            else vals[i] = "";
        }
        ConfigData forged = (ConfigData) ctor.newInstance(vals);
        forged.applyTo(ModConfig.get());
        check("a packet claiming consent does NOT grant it",
                !ModConfig.get().localConsented, "this is the whole safety property");
        check("but ordinary local settings DO apply (port)",
                ModConfig.get().localPort == 9999, "" + ModConfig.get().localPort);
        check("and the chosen model applies", "qwen1.5b".equals(ModConfig.get().localModelId),
                ModConfig.get().localModelId);
        check("and the context window applies", ModConfig.get().localContext == 8192,
                "" + ModConfig.get().localContext);

        System.out.println("\nSchema 15 migration");
        Files.writeString(dir.resolve("blockpal/config.json"),
                "{\"configVersion\":14,\"aiConnection\":\"free\",\"freeAiFallback\":true}");
        ModConfig.load();
        ModConfig c = ModConfig.get();
        check("migrated to 15", c.configVersion == 15, "" + c.configVersion);
        check("a server already on the free service is NOT moved off it",
                "free".equals(c.aiConnection),
                "switching them would mean a surprise 2 GB download");
        check("and has not silently consented to anything", !c.localConsented, "");
        check("local defaults filled in", "qwen3b".equals(c.localModelId) && c.localPort == 8081
                && c.localContext == 4096 && c.localAutoStart,
                c.localModelId + " :" + c.localPort);

        System.out.println("\nGarbage local values are clamped");
        Files.writeString(dir.resolve("blockpal/config.json"),
                "{\"configVersion\":15,\"aiConnection\":\"local\",\"localModelId\":\"enormous\","
              + "\"localPort\":70000,\"localGpuLayers\":-99,\"localContext\":9}");
        ModConfig.load();
        c = ModConfig.get();
        check("unknown model falls back to the default", "qwen3b".equals(c.localModelId), c.localModelId);
        check("port clamped into range", c.localPort == 8081, "" + c.localPort);
        check("gpu layers clamped to auto", c.localGpuLayers == -1, "" + c.localGpuLayers);
        check("context clamped up", c.localContext >= 512, "" + c.localContext);

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
