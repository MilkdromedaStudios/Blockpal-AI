import com.milkdromeda.blockpal.agent.BotApi;
import java.lang.reflect.*;
import java.util.*;

/** Every declared verb must be dispatchable and documented; nothing may drift. */
public class ApiConsistency {
    public static void main(String[] args) throws Exception {
        Field f = BotApi.class.getDeclaredField("FUNCTIONS");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String,int[]> fns = (Map<String,int[]>) f.get(null);
        String ref = BotApi.reference().toLowerCase(Locale.ROOT);

        List<String> undocumented = new ArrayList<>();
        for (String name : fns.keySet()) {
            if (!ref.contains(name)) undocumented.add(name);
        }
        System.out.println("declared verbs: " + fns.size());
        System.out.println("undocumented in reference(): " + (undocumented.isEmpty() ? "none" : undocumented));

        // arity checks behave
        int bad = 0;
        for (Map.Entry<String,int[]> e : fns.entrySet()) {
            int min = e.getValue()[0], max = e.getValue()[1];
            if (BotApi.checkArity(e.getKey(), min) != null) { System.out.println("  FAIL min arity rejected: " + e.getKey()); bad++; }
            if (BotApi.checkArity(e.getKey(), max) != null) { System.out.println("  FAIL max arity rejected: " + e.getKey()); bad++; }
            if (max < 9 && BotApi.checkArity(e.getKey(), max + 1) == null) { System.out.println("  FAIL over-arity accepted: " + e.getKey()); bad++; }
            if (min > 0 && BotApi.checkArity(e.getKey(), min - 1) == null) { System.out.println("  FAIL under-arity accepted: " + e.getKey()); bad++; }
        }
        System.out.println("arity boundary failures: " + bad);
        if (BotApi.checkArity("definitelyNotAVerb", 0) == null) { System.out.println("FAIL unknown verb accepted"); bad++; }
        System.out.println(bad == 0 && undocumented.isEmpty() ? "OK" : "PROBLEMS");
        if (bad != 0 || !undocumented.isEmpty()) System.exit(1);
    }
}
