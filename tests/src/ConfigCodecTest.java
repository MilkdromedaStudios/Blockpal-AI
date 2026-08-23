import com.milkdromeda.blockpal.network.ConfigData;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.lang.reflect.*;
import java.util.*;

/**
 * Every ConfigData component must survive the hand-written StreamCodec unchanged.
 * Write order and read order drifting apart compiles perfectly and shows one setting's
 * value in another setting's box, so each field gets a value unlike every other.
 */
public class ConfigCodecTest {
    public static void main(String[] a) throws Exception {
        RecordComponent[] comps = ConfigData.class.getRecordComponents();
        Constructor<?> ctor = ConfigData.class.getDeclaredConstructors()[0];
        Object[] values = new Object[comps.length];
        for (int i = 0; i < comps.length; i++) {
            Class<?> t = comps[i].getType();
            // Distinct per index, so a swap between two same-typed fields is visible.
            if (t == boolean.class) values[i] = (i % 3 != 0);
            else if (t == int.class) values[i] = 1000 + i;
            else if (t == double.class) values[i] = 0.5 + i;
            else values[i] = "v" + i + "-" + comps[i].getName();
        }
        Object original = ctor.newInstance(values);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ConfigData.STREAM_CODEC.encode(buf, (ConfigData) original);
        Object decoded = ConfigData.STREAM_CODEC.decode(buf);

        int bad = 0;
        for (int i = 0; i < comps.length; i++) {
            Object want = values[i];
            Object got = comps[i].getAccessor().invoke(decoded);
            if (!Objects.equals(want, got)) {
                System.out.printf("  FAIL %-26s wrote %-22s read %s%n", comps[i].getName(), want, got);
                bad++;
            }
        }
        System.out.println("ConfigData components: " + comps.length);
        System.out.println("leftover bytes after decode: " + buf.readableBytes());
        System.out.println(bad == 0 ? "  ok   every field round-trips in the right order"
                                    : "  " + bad + " FIELDS CORRUPTED");
        if (buf.readableBytes() != 0) { System.out.println("  FAIL codec wrote more than it read"); bad++; }
        System.out.println(bad == 0 ? "PASS" : "FAIL");
        if (bad != 0) System.exit(1);
    }
}
