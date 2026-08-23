package com.milkdromeda.blockpal.pvt;

/**
 * One recorded moment: what the agent could see, and what it did about it.
 *
 * <p>Observations are stored <b>quantised to bytes</b> rather than floats. Every feature
 * in {@link PvtObservation} lives in −1..1, so a signed byte carries them to about 1/127,
 * which is far finer than the difference between two frames of real play — and it makes
 * a frame 297 bytes instead of 1,161. An hour of recorded play is then about 21 MB
 * instead of 84 MB, which is the difference between a feature people leave on and one
 * they turn off after a week.
 *
 * @param obs     quantised observation, {@link PvtObservation#SIZE} bytes
 * @param labels  one class per {@link PvtAction} head
 * @param labelled false when the action is a guess from the inverse dynamics model
 *                 rather than something a person was seen to do
 */
public record PvtFrame(byte[] obs, int[] labels, boolean labelled) {

    /** Packs a float observation into the stored byte form. */
    public static byte[] quantise(float[] obs) {
        byte[] out = new byte[obs.length];
        for (int i = 0; i < obs.length; i++) {
            float v = Math.max(-1f, Math.min(1f, obs[i]));
            out[i] = (byte) Math.round(v * 127f);
        }
        return out;
    }

    /** Unpacks a stored observation back into network input. */
    public static float[] dequantise(byte[] obs) {
        float[] out = new float[obs.length];
        for (int i = 0; i < obs.length; i++) out[i] = obs[i] / 127f;
        return out;
    }

    public float[] observation() { return dequantise(obs); }

    public PvtAction action() { return new PvtAction(labels); }
}
