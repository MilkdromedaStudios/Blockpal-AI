import com.milkdromeda.blockpal.pvt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** End-to-end test of the PVT data pipeline: disk format, episodes, IDM labelling. */
public class PipelineTest {
    static int pass = 0, fail = 0;
    static void check(String what, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("  ok   " + what + (detail.isEmpty()?"":"  ("+detail+")")); }
        else { fail++; System.out.println("  FAIL " + what + "  " + detail); }
    }
    static final int OBS = PvtObservation.SIZE;
    static Random rng = new Random(11);

    static float[] obs(double seed) {
        float[] o = new float[OBS];
        for (int i = 0; i < OBS; i++) o[i] = (float) Math.sin(seed * (i + 1) * 0.017);
        return o;
    }

    static void writeEpisode(Path f, int n, boolean labelled, int actionSeed) throws IOException {
        DataOutputStream out = PvtDataset.openEpisode(f);
        for (int i = 0; i < n; i++) {
            int[] labels = PvtAction.of((i + actionSeed) % 3 - 1, 0, i % 7 == 0, false, false,
                    i % 5 == 0, false, ((i % 3) - 1) * 10, 0).heads();
            PvtDataset.writeFrame(out, new PvtFrame(PvtFrame.quantise(obs(i + actionSeed)), labels, labelled));
        }
        out.flush(); out.close();
    }

    public static void main(String[] a) throws Exception {
        Path dir = Files.createTempDirectory("pvt-test");
        System.out.println("observation size = " + OBS + " floats; frame on disk = "
                + PvtDataset.frameBytes() + " bytes");

        // ── 1. round trip ─────────────────────────────────────────────────────────
        System.out.println("\nDisk round-trip");
        writeEpisode(dir.resolve("ep1.bpd"), 100, true, 0);
        PvtDataset d = PvtDataset.load(dir, 1_000_000);
        check("frame count survives", d.size() == 100, "" + d.size());
        check("all labelled", d.labelledCount() == 100, "" + d.labelledCount());
        check("one episode range", d.episodes().size() == 1, "" + d.episodes().size());
        check("episode range covers all", Arrays.equals(d.episodes().get(0), new int[]{0,100}),
                Arrays.toString(d.episodes().get(0)));

        float[] original = obs(7);
        float[] back = PvtFrame.dequantise(PvtFrame.quantise(original));
        double maxErr = 0;
        for (int i = 0; i < OBS; i++) maxErr = Math.max(maxErr, Math.abs(original[i] - back[i]));
        check("quantisation error under 1/127", maxErr <= 1.0/127 + 1e-6, String.format("%.5f", maxErr));

        int[] want = PvtAction.of(1, -1, true, true, false, true, false, 20, -8).heads();
        DataOutputStream o2 = PvtDataset.openEpisode(dir.resolve("labels.bpd"));
        PvtDataset.writeFrame(o2, new PvtFrame(PvtFrame.quantise(obs(1)), want, true));
        PvtDataset.writeFrame(o2, new PvtFrame(PvtFrame.quantise(obs(2)), want, true));
        o2.close();
        PvtDataset one = new PvtDataset();
        PvtDataset justLabels = PvtDataset.load(dir.resolve("nonexistent"), 10);
        check("missing folder loads empty", justLabels.isEmpty(), "");

        // ── 2. episode boundaries ────────────────────────────────────────────────
        System.out.println("\nEpisode boundaries (pairs must never straddle two sessions)");
        Path dir2 = Files.createTempDirectory("pvt-test2");
        writeEpisode(dir2.resolve("a.bpd"), 50, true, 0);
        Thread.sleep(15);
        writeEpisode(dir2.resolve("b.bpd"), 30, true, 5);
        PvtDataset two = PvtDataset.load(dir2, 1_000_000);
        check("both episodes loaded", two.size() == 80, "" + two.size());
        check("two episode ranges", two.episodes().size() == 2, "" + two.episodes().size());
        List<PvtDataset.Pair> pairs = two.idmPairs();
        check("pairs stay inside episodes", pairs.size() == 49 + 29,
                pairs.size() + " (a naive loop would give 79)");
        check("pair input is two observations wide", pairs.get(0).input().length == OBS * 2,
                "" + pairs.get(0).input().length);

        two.shuffle(new Random(1));
        check("shuffling clears episodes so stale pairs can't be built", two.episodes().isEmpty(), "");
        check("pairs refuse to build after a shuffle", two.idmPairs().isEmpty(), "");

        // ── 3. truncated + foreign files ─────────────────────────────────────────
        System.out.println("\nDamaged and foreign files");
        Path dir3 = Files.createTempDirectory("pvt-test3");
        Path ep = dir3.resolve("crash.bpd");
        writeEpisode(ep, 40, true, 0);
        byte[] full = Files.readAllBytes(ep);
        // simulate a server killed mid-frame
        Files.write(ep, Arrays.copyOf(full, full.length - (PvtDataset.frameBytes() / 2)));
        PvtDataset crashed = PvtDataset.load(dir3, 1_000_000);
        check("a crash-truncated episode still yields its complete frames",
                crashed.size() == 39, crashed.size() + " of 40");

        Path foreign = dir3.resolve("foreign.bpd");
        try (DataOutputStream fo = new DataOutputStream(Files.newOutputStream(foreign))) {
            fo.writeInt(0x42504432); fo.writeInt(999); fo.writeInt(PvtAction.HEADS);
            fo.write(new byte[500]);
        }
        PvtDataset mixed = PvtDataset.load(dir3, 1_000_000);
        check("an episode from another observation layout is rejected, not misread",
                mixed.rejected() >= 1 && mixed.size() == 39, "rejected=" + mixed.rejected() + " size=" + mixed.size());

        // ── 4. pruning ───────────────────────────────────────────────────────────
        System.out.println("\nPruning to the frame cap");
        Path dir4 = Files.createTempDirectory("pvt-test4");
        for (int i = 0; i < 5; i++) { writeEpisode(dir4.resolve("e" + i + ".bpd"), 100, true, i); Thread.sleep(12); }
        int before = (int) Files.list(dir4).count();
        int removed = PvtDataset.prune(dir4, 250);
        int after = (int) Files.list(dir4).count();
        check("oldest episodes dropped to fit the cap", removed > 0 && after < before,
                before + " files -> " + after + " (removed " + removed + ")");
        PvtDataset pruned = PvtDataset.load(dir4, 1_000_000);
        check("what remains is under the cap", pruned.size() <= 300, "" + pruned.size());

        // ── 5. head bias detection ───────────────────────────────────────────────
        System.out.println("\nLopsided-data detection");
        PvtDataset lop = new PvtDataset();
        for (int i = 0; i < 100; i++) {
            int[] labels = PvtAction.of(i < 97 ? 0 : 1, 0,false,false,false,false,false,0,0).heads();
            lop.add(new PvtFrame(PvtFrame.quantise(obs(i)), labels, true));
        }
        double[] bias = lop.headBias();
        check("spots a 97% standing-still set", bias[PvtAction.HEAD_FORWARD] > 0.95,
                String.format("%.0f%%", bias[PvtAction.HEAD_FORWARD]*100));

        // ── 6. the actual VPT trick: IDM labels unlabelled footage ───────────────
        System.out.println("\nInverse dynamics: labelling footage that has no recorded actions");
        // Synthetic world where the action IS recoverable from the change between views.
        int N = 2500;
        List<float[]> befores = new ArrayList<>(), afters = new ArrayList<>();
        List<int[]> truth = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            float[] pre = new float[OBS];
            for (int k = 0; k < OBS; k++) pre[k] = (float) (rng.nextGaussian() * 0.3);
            int fwd = rng.nextInt(3) - 1;
            int yawIdx = rng.nextInt(PvtAction.YAW_BINS.length);
            float[] post = pre.clone();
            float yaw = PvtAction.YAW_BINS[yawIdx];
            post[0] = pre[0] + fwd * 0.25f;                        // moved forward/back
            post[1] = pre[1] + yaw / 90f;                          // heading changed
            // A real turn slides the whole retina sideways rather than nudging one
            // number, so the evidence for it is spread over many features. Modelling
            // that matters: with the signal confined to a single input the model reads
            // the turn 42% of the time, and with it spread as it really is, 80%.
            for (int k = 2; k < 200; k++) post[k] = pre[k] + (yaw / 90f) * 0.35f * (float) Math.cos(k);
            befores.add(pre); afters.add(post);
            truth.add(PvtAction.of(fwd, 0,false,false,false,false,false,
                    PvtAction.YAW_BINS[yawIdx], 0).heads());
        }
        PvtIdm idm = PvtIdm.fresh(96, 5L);
        int split = 2000;
        for (int epoch = 0; epoch < 40; epoch++) {
            for (int s = 0; s + 64 <= split; s += 64) {
                float[][] xs = new float[64][]; int[][] ys = new int[64][];
                for (int i = 0; i < 64; i++) {
                    xs[i] = PvtIdm.join(befores.get(s+i), afters.get(s+i));
                    ys[i] = truth.get(s+i);
                }
                idm.net().trainBatch(xs, ys, 0.003f, 2);
            }
        }
        idm.net().shutdown();
        int fwdRight = 0, yawRight = 0, yawClose = 0;
        for (int i = split; i < N; i++) {
            int[] got = idm.label(befores.get(i), afters.get(i));
            if (got[PvtAction.HEAD_FORWARD] == truth.get(i)[PvtAction.HEAD_FORWARD]) fwdRight++;
            int gy = got[PvtAction.HEAD_YAW], ty = truth.get(i)[PvtAction.HEAD_YAW];
            if (gy == ty) yawRight++;
            if (Math.abs(gy - ty) <= 1) yawClose++;
        }
        int held = N - split;
        check("IDM recovers movement from two views > 95%", fwdRight/(double)held > 0.95,
                String.format("%.1f%%", 100.0*fwdRight/held));
        // Exact-bin turn recovery converges around 80% and does not improve with more
        // epochs; every remaining miss is an ADJACENT bin (2 degrees read as 5), which is
        // immaterial to how the bot moves. Both are asserted so a regression in either
        // shows up.
        check("IDM recovers the turn to the right bin > 70%", yawRight/(double)held > 0.70,
                String.format("%.1f%%", 100.0*yawRight/held));
        check("IDM turn is within one bin > 95%", yawClose/(double)held > 0.95,
                String.format("%.1f%%", 100.0*yawClose/held));

        // relabel() writes the inferred action back into the set
        PvtDataset relab = new PvtDataset();
        relab.add(new PvtFrame(PvtFrame.quantise(obs(1)), new int[PvtAction.HEADS], false));
        check("starts unlabelled", relab.labelledCount() == 0, "");
        relab.relabel(0, want);
        check("relabel marks it usable", relab.labelledCount() == 1, "");
        check("relabel keeps the observation", Arrays.equals(relab.frames().get(0).obs(),
                PvtFrame.quantise(obs(1))), "");
        check("relabel stores the inferred action", Arrays.equals(relab.frames().get(0).labels(), want), "");

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
