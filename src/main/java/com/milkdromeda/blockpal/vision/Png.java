package com.milkdromeda.blockpal.vision;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * A tiny, dependency-free PNG writer for the pictures the bot "sees".
 *
 * <p>Deliberately hand-rolled rather than {@code javax.imageio}: this runs on a
 * dedicated server, which may be headless (and on some JREs has no image plugins
 * registered at all), and an 8-bit RGB PNG is only three chunks — IHDR, IDAT and
 * IEND — so the whole encoder is a page of {@code java.util.zip}.
 */
public final class Png {

    private Png() {}

    /**
     * Encodes a row-major array of {@code 0xRRGGBB} pixels as an 8-bit truecolour PNG.
     *
     * @param pixels {@code width * height} packed RGB values (the alpha byte is ignored)
     */
    public static byte[] encode(int[] pixels, int width, int height) {
        try {
            // Raw scanlines: each row is prefixed with filter type 0 (None). Filtering
            // would compress better, but these images are small and the server thread
            // should spend its time on the game, not on PNG heuristics.
            byte[] raw = new byte[height * (1 + width * 3)];
            int o = 0;
            for (int y = 0; y < height; y++) {
                raw[o++] = 0;
                int rowStart = y * width;
                for (int x = 0; x < width; x++) {
                    int rgb = pixels[rowStart + x];
                    raw[o++] = (byte) ((rgb >> 16) & 0xFF);
                    raw[o++] = (byte) ((rgb >> 8) & 0xFF);
                    raw[o++] = (byte) (rgb & 0xFF);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 4 + 128);
            out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

            ByteArrayOutputStream ihdr = new ByteArrayOutputStream(13);
            writeInt(ihdr, width);
            writeInt(ihdr, height);
            ihdr.write(8);    // bit depth
            ihdr.write(2);    // colour type 2 = truecolour RGB
            ihdr.write(0);    // deflate
            ihdr.write(0);    // adaptive filtering
            ihdr.write(0);    // no interlace
            chunk(out, "IHDR", ihdr.toByteArray());

            chunk(out, "IDAT", deflate(raw));
            chunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException e) {
            // ByteArrayOutputStream never actually throws; keep callers exception-free.
            return new byte[0];
        }
    }

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream buf = new ByteArrayOutputStream(raw.length / 4 + 64);
            byte[] chunk = new byte[8192];
            while (!deflater.finished()) {
                int n = deflater.deflate(chunk);
                if (n <= 0) break;
                buf.write(chunk, 0, n);
            }
            return buf.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        writeInt(out, data.length);
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }
}
