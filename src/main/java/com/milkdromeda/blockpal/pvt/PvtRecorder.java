package com.milkdromeda.blockpal.pvt;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * <b>Watches somebody play and writes down what they did.</b> This is the "pre-" half of
 * pre-video training: the data is banked while a person is simply playing, long before
 * any companion is trained on it.
 *
 * <p><b>How the action labels are obtained.</b> A dedicated server never receives "the
 * W key went down" — it receives where the player ended up. So each label is recovered
 * from what changed: the displacement over the sampling window, rotated into the
 * player's own facing, says which movement keys were held; the view rotation says how
 * the mouse moved; and the swing and item-use flags the server already tracks give the
 * two mouse buttons. This is an analytic inverse dynamics model, and it is exact for
 * movement in a way a learned one is not — which is precisely why it is also used to
 * generate the training set for {@link PvtIdm}, the learned one that handles footage
 * where even this much is unavailable.
 *
 * <p>Frames a companion could never reproduce are dropped rather than learned from:
 * flying, spectating, riding a vehicle, or being dead. Training a walking bot on a
 * creative-mode flight teaches it to hold jump and go nowhere.
 */
public final class PvtRecorder {

    private final LivingEntity subject;
    private final Path file;
    private DataOutputStream out;

    private int tickCounter;
    private int frames;
    private int skipped;
    private boolean closed;
    private String error = "";

    // Previous sample, for working out what changed.
    private Vec3 lastPos;
    private float lastYaw;
    private float lastPitch;
    private boolean lastOnGround = true;
    private boolean primed;

    /** A player walking flat out covers about this much ground per tick. */
    private static final double WALK_PER_TICK = 0.216;

    public PvtRecorder(LivingEntity subject, Path file) {
        this.subject = subject;
        this.file = file;
    }

    public int frames() { return frames; }
    public int skipped() { return skipped; }
    public boolean isClosed() { return closed; }
    public String error() { return error; }
    public LivingEntity subject() { return subject; }
    public Path file() { return file; }

    /**
     * Called every server tick. Samples on the decision interval only — recording at the
     * same rate the policy will later run at is what keeps a learned "turn 10°" meaning
     * the same amount of turning in play as it did in training.
     */
    public void tick() {
        if (closed) return;
        if (!subject.isAlive()) { close(); return; }
        if (++tickCounter < PvtObservation.DECISION_INTERVAL) return;
        int elapsed = tickCounter;
        tickCounter = 0;

        if (!(subject.level() instanceof ServerLevel level)) return;

        Vec3 pos = subject.position();
        float yaw = subject.getYRot();
        float pitch = subject.getXRot();
        boolean onGround = subject.onGround();

        if (!primed) {
            primed = true;
            remember(pos, yaw, pitch, onGround);
            return;
        }

        if (!usable()) {
            skipped++;
            remember(pos, yaw, pitch, onGround);
            return;
        }

        try {
            float[] obs = PvtObservation.encode(subject, level);
            PvtAction action = deriveAction(pos, yaw, pitch, onGround, elapsed);
            if (out == null) out = PvtDataset.openEpisode(file);
            PvtDataset.writeFrame(out, new PvtFrame(PvtFrame.quantise(obs), action.heads(), true));
            frames++;
        } catch (IOException e) {
            error = String.valueOf(e.getMessage());
            close();
        } catch (Exception e) {
            skipped++;
        }
        remember(pos, yaw, pitch, onGround);
    }

    private void remember(Vec3 pos, float yaw, float pitch, boolean onGround) {
        lastPos = pos;
        lastYaw = yaw;
        lastPitch = pitch;
        lastOnGround = onGround;
    }

    /** Whether this moment is something a walking companion could ever reproduce. */
    private boolean usable() {
        if (subject.isPassenger()) return false;             // riding: not our controls
        if (subject.isFallFlying()) return false;            // elytra
        if (subject instanceof Player p) {
            if (p.isSpectator()) return false;
            try {
                if (p.getAbilities().flying) return false;   // creative flight
            } catch (Exception ignored) {
                // If abilities can't be read, assume it's a normal survival moment.
            }
        }
        return true;
    }

    /** Recovers the keys and mouse from what changed since the last sample. */
    private PvtAction deriveAction(Vec3 pos, float yaw, float pitch, boolean onGround, int elapsed) {
        double dx = pos.x - lastPos.x;
        double dz = pos.z - lastPos.z;
        double dy = pos.y - lastPos.y;
        double perTick = Math.max(1, elapsed);

        // Rotate the displacement into the facing the player HAD while moving, so
        // "forward" means forward from their point of view, not north.
        double yawRad = Math.toRadians(lastYaw);
        double fx = -Math.sin(yawRad), fz = Math.cos(yawRad);
        double rx = -Math.cos(yawRad), rz = -Math.sin(yawRad);
        double forward = (dx * fx + dz * fz) / perTick / WALK_PER_TICK;
        double strafe = (dx * rx + dz * rz) / perTick / WALK_PER_TICK;

        // A jump is the moment somebody left the ground upwards under their own steam.
        boolean jump = lastOnGround && !onGround && dy > 0.05;

        boolean attack = subject.swinging;
        boolean use = subject.isUsingItem();

        float yawDelta = net.minecraft.util.Mth.wrapDegrees(yaw - lastYaw);
        float pitchDelta = pitch - lastPitch;

        return PvtAction.of(forward, strafe, jump,
                subject.isShiftKeyDown(), subject.isSprinting(), attack, use,
                yawDelta, pitchDelta);
    }

    /** Flushes and closes the episode file. Safe to call more than once. */
    public void close() {
        if (closed) return;
        closed = true;
        if (out != null) {
            try {
                out.flush();
                out.close();
            } catch (IOException e) {
                error = String.valueOf(e.getMessage());
            }
            out = null;
        }
    }

    /** A one-line summary for chat feedback. */
    public String describe() {
        return subject.getName().getString() + ": " + frames + " frames"
                + (skipped > 0 ? " (" + skipped + " skipped)" : "")
                + (error.isEmpty() ? "" : " — " + error);
    }
}
