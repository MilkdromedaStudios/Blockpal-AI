import com.milkdromeda.blockpal.pvt.*;
import java.io.*;
import java.util.*;

public class NetTest {
    static int pass = 0, fail = 0;
    static void check(String what, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("  ok   " + what + (detail.isEmpty() ? "" : "  (" + detail + ")")); }
        else { fail++; System.out.println("  FAIL " + what + "  " + detail); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("PvtAction");
        check("head sizes sum to LOGITS", PvtAction.LOGITS == 34, "LOGITS=" + PvtAction.LOGITS);
        int off = 0;
        boolean offsetsOk = true;
        for (int h = 0; h < PvtAction.HEADS; h++) {
            offsetsOk &= PvtAction.HEAD_OFFSETS[h] == off;
            off += PvtAction.HEAD_SIZES[h];
        }
        check("head offsets are cumulative", offsetsOk && off == PvtAction.LOGITS, "");
        check("idle() is idle", PvtAction.idle().isIdle(), PvtAction.idle().toString());
        PvtAction a = PvtAction.of(1, 0, true, false, true, false, false, 12, -4);
        check("of() forward", a.forward() == 1f, "" + a.forward());
        check("of() strafe none", a.strafe() == 0f, "" + a.strafe());
        check("of() jump", a.jump(), "");
        check("of() sprint", a.sprint(), "");
        check("of() yaw binned to 10", a.yawDelta() == 10f, "" + a.yawDelta());
        check("of() pitch binned to -3", a.pitchDelta() == -3f, "" + a.pitchDelta());
        PvtAction b = PvtAction.of(-1, 1, false, true, false, true, true, -50, 0);
        check("of() back", b.forward() == -1f, "" + b.forward());
        check("of() left", b.strafe() == 1f, "" + b.strafe());
        check("of() yaw clamps to widest bin", b.yawDelta() == -45f, "" + b.yawDelta());
        check("small drift is not a keypress", PvtAction.of(0.02, -0.01, false,false,false,false,false,0,0).isIdle(), "");
        check("toString reads", b.toString().contains("attack"), b.toString());

        // ── learn a task that needs BOTH hidden layers: XOR-ish, per head ──────────
        System.out.println("\nPvtNet learning (synthetic demonstrations)");
        int IN = 24;
        PvtNet net = new PvtNet(IN, 64, 64, 42L);
        check("parameter count", net.parameterCount() == 24*64 + 64 + 64*64 + 64 + 34*64 + 34,
                "" + net.parameterCount());

        Random rng = new Random(7);
        int N = 3000;
        float[][] xs = new float[N][IN];
        int[][] ys = new int[N][PvtAction.HEADS];
        for (int i = 0; i < N; i++) {
            for (int k = 0; k < IN; k++) xs[i][k] = (float) rng.nextGaussian();
            boolean p = xs[i][0] > 0, q = xs[i][1] > 0;
            // forward head = XOR (not linearly separable -> proves the hidden layers work)
            boolean xor = p ^ q;
            // yaw head = a 3-way split on another feature
            double yawDeg = xs[i][2] > 1 ? 20 : xs[i][2] < -1 ? -20 : 0;
            PvtAction act = PvtAction.of(xor ? 1 : -1, 0, xs[i][3] > 0, false, false,
                    xs[i][4] > 0, false, yawDeg, 0);
            ys[i] = act.heads();
        }
        // 80/20 split
        int split = (int) (N * 0.8);
        float[][] trX = Arrays.copyOfRange(xs, 0, split);
        int[][]   trY = Arrays.copyOfRange(ys, 0, split);
        float[][] teX = Arrays.copyOfRange(xs, split, N);
        int[][]   teY = Arrays.copyOfRange(ys, split, N);

        PvtNet.BatchResult before = net.evaluate(teX, teY);
        int batch = 64;
        double lastLoss = 0;
        for (int epoch = 0; epoch < 60; epoch++) {
            // shuffle
            for (int i = trX.length - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                float[] tx = trX[i]; trX[i] = trX[j]; trX[j] = tx;
                int[] ty = trY[i]; trY[i] = trY[j]; trY[j] = ty;
            }
            for (int s = 0; s + batch <= trX.length; s += batch) {
                PvtNet.BatchResult r = net.trainBatch(
                        Arrays.copyOfRange(trX, s, s + batch),
                        Arrays.copyOfRange(trY, s, s + batch), 0.003f, 2);
                lastLoss = r.loss();
            }
        }
        net.shutdown();
        PvtNet.BatchResult after = net.evaluate(teX, teY);
        System.out.printf("     held-out loss %.4f -> %.4f, accuracy %.1f%% -> %.1f%%%n",
                before.loss(), after.loss(), before.accuracy()*100, after.accuracy()*100);
        check("loss went down", after.loss() < before.loss() * 0.5, before.loss()+" -> "+after.loss());
        check("held-out accuracy > 95%", after.accuracy() > 0.95, String.format("%.1f%%", after.accuracy()*100));

        // per-head accuracy on the two learned heads
        PvtNet.Scratch sc = net.scratch();
        int fwdRight = 0, yawRight = 0;
        for (int i = 0; i < teX.length; i++) {
            PvtNet.Prediction p = net.predict(teX[i], sc);
            if (p.action().head(PvtAction.HEAD_FORWARD) == teY[i][PvtAction.HEAD_FORWARD]) fwdRight++;
            if (p.action().head(PvtAction.HEAD_YAW) == teY[i][PvtAction.HEAD_YAW]) yawRight++;
        }
        // 22 of the 24 inputs are pure noise, so some boundary error is expected here;
        // the clean-XOR case below is the one that proves the non-linearity is learned.
        check("XOR forward head well above the 50% linear ceiling", fwdRight / (double) teX.length > 0.85,
                String.format("%.1f%%", 100.0*fwdRight/teX.length));
        check("3-way yaw head learned > 90%", yawRight / (double) teX.length > 0.90,
                String.format("%.1f%%", 100.0*yawRight/teX.length));

        PvtNet.Prediction pr = net.predict(teX[0], sc);
        check("confidence in 0..1", pr.confidence() >= 0 && pr.confidence() <= 1, "" + pr.confidence());
        check("confidence is high after training", pr.confidence() > 0.6, String.format("%.2f", pr.confidence()));

        // ── save / load round-trip ────────────────────────────────────────────────
        System.out.println("\nPvtNet persistence");
        net.addFramesTrained(12345);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(bos)) { net.write(dos); }
        PvtNet loaded;
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            loaded = PvtNet.read(dis);
        }
        check("reloaded", loaded != null, "");
        check("frames trained survived", loaded.framesTrained() == 12345, "" + loaded.framesTrained());
        check("shape survived", loaded.inputSize() == IN && loaded.hidden1Size() == 64, "");
        PvtNet.Scratch sc2 = loaded.scratch();
        boolean identical = true;
        for (int i = 0; i < teX.length; i++) {
            identical &= Arrays.equals(net.predict(teX[i], sc).action().heads(),
                                       loaded.predict(teX[i], sc2).action().heads());
        }
        check("reloaded net predicts identically", identical, "");

        byte[] junk = bos.toByteArray().clone();
        junk[0] = 0x00;
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(junk))) {
            check("a foreign file is refused, not misread", PvtNet.read(dis) == null, "");
        }

        // determinism
        PvtNet n1 = new PvtNet(IN, 32, 32, 99L);
        PvtNet n2 = new PvtNet(IN, 32, 32, 99L);
        check("same seed = same init", Arrays.equals(n1.predict(teX[0], n1.scratch()).action().heads(),
                n2.predict(teX[0], n2.scratch()).action().heads()), "");

        // ── the decisive one: XOR with no noise features at all ───────────────────
        System.out.println("\nPvtNet non-linearity (clean XOR — a linear model caps at 50%)");
        Random r2 = new Random(3);
        int N2 = 4000;
        float[][] xs2 = new float[N2][2];
        int[][] ys2 = new int[N2][PvtAction.HEADS];
        for (int i = 0; i < N2; i++) {
            xs2[i][0] = (float) r2.nextGaussian();
            xs2[i][1] = (float) r2.nextGaussian();
            boolean xor = (xs2[i][0] > 0) ^ (xs2[i][1] > 0);
            ys2[i] = PvtAction.of(xor ? 1 : -1, 0, false,false,false,false,false, 0, 0).heads();
        }
        PvtNet clean = new PvtNet(2, 32, 32, 5L);
        float[][] cTrX = Arrays.copyOfRange(xs2, 0, 3200);
        int[][]   cTrY = Arrays.copyOfRange(ys2, 0, 3200);
        float[][] cTeX = Arrays.copyOfRange(xs2, 3200, N2);
        int[][]   cTeY = Arrays.copyOfRange(ys2, 3200, N2);
        for (int e = 0; e < 150; e++) {
            for (int s = 0; s + 64 <= cTrX.length; s += 64) {
                clean.trainBatch(Arrays.copyOfRange(cTrX, s, s + 64),
                                 Arrays.copyOfRange(cTrY, s, s + 64), 0.005f, 1);
            }
        }
        clean.shutdown();
        PvtNet.Scratch cs = clean.scratch();
        int cRight = 0;
        for (int i = 0; i < cTeX.length; i++) {
            if (clean.predict(cTeX[i], cs).action().head(PvtAction.HEAD_FORWARD)
                    == cTeY[i][PvtAction.HEAD_FORWARD]) cRight++;
        }
        check("clean XOR learned > 97%", cRight / (double) cTeX.length > 0.97,
                String.format("%.1f%%", 100.0 * cRight / cTeX.length));

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
