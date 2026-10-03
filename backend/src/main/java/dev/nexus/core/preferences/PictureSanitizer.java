package dev.nexus.core.preferences;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Turns an uploaded file into a picture this app made itself, or refuses it.
 *
 * <p>Nothing of the upload is ever stored or served. It is identified by its leading bytes —
 * never by its name or the type the client claimed — decoded by the JDK's own JPEG or PNG
 * reader and nothing else, and drawn onto a fresh canvas that is encoded anew. Whatever rode
 * along in the file is left behind with it: EXIF and its GPS position, comments, chunks after
 * the image ends, a script pasted onto the tail of a polyglot. What comes out is pixels in a
 * JPEG this class wrote.
 *
 * <p>The size the header declares is read and bounded before a single pixel is decoded, so a
 * small file that announces an enormous image is turned away without the memory to hold it
 * ever being asked for. Decoding is also capped at a couple at once: one is cheap, but a
 * burst of them is the shortest route to running a small instance out of memory.
 */
@Component
public class PictureSanitizer {

    /** The most an upload may be. Checked again here, whatever the controller let through. */
    public static final int MAX_BYTES = 1024 * 1024;

    /** Larger than any photo a phone takes, and far short of what would strain the heap. */
    static final int MAX_SIDE = 4096;

    /** Below this there is nothing a circle the size of the profile's could show. */
    static final int MIN_SIDE = 32;

    /** The longest side kept: twice the largest the picture is ever drawn, for sharp screens. */
    static final int STORED_SIDE = 1024;

    private static final float QUALITY = 0.88f;

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {
        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'
    };

    /** The JDK's own codecs. A plugin dropped on the classpath later must not take over here. */
    private static final String JDK_CODECS = "com.sun.imageio.plugins.";

    private final Semaphore decoding = new Semaphore(2);

    public PictureSanitizer() {
        // Decoding through memory alone: the disk cache would write an attacker's bytes to a
        // temp file for no benefit at these sizes.
        ImageIO.setUseCache(false);
    }

    /** A picture this app encoded, and the size it came out at. */
    public record Sanitized(byte[] jpeg, int width, int height) {}

    public Sanitized sanitize(byte[] upload) {
        if (upload == null || upload.length == 0) {
            throw new PictureRejectedException("No picture was uploaded.");
        }
        if (upload.length > MAX_BYTES) {
            throw new PictureTooLargeException();
        }
        String format = formatOf(upload).orElseThrow(() ->
                new PictureRejectedException("Only JPEG and PNG pictures can be uploaded."));

        acquire();
        try {
            BufferedImage decoded = decode(upload, format);
            BufferedImage redrawn = redraw(decoded);
            return new Sanitized(encode(redrawn), redrawn.getWidth(), redrawn.getHeight());
        } finally {
            decoding.release();
        }
    }

    static Optional<String> formatOf(byte[] bytes) {
        if (startsWith(bytes, JPEG_MAGIC)) {
            return Optional.of("jpeg");
        }
        if (startsWith(bytes, PNG_MAGIC)) {
            return Optional.of("png");
        }
        return Optional.empty();
    }

    private void acquire() {
        try {
            if (!decoding.tryAcquire(10, TimeUnit.SECONDS)) {
                throw new PictureRejectedException("Pictures are busy right now. Please try again.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PictureRejectedException("Pictures are busy right now. Please try again.");
        }
    }

    private static BufferedImage decode(byte[] bytes, String format) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            ImageReader reader = jdkReader(format);
            try {
                // Seek forward only, and skip metadata entirely: nothing in it is wanted, and
                // every parser not run is one an odd file cannot reach.
                reader.setInput(in, true, true);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_SIDE || height > MAX_SIDE) {
                    throw new PictureRejectedException(
                            "That picture is too large. Please use one at most " + MAX_SIDE + " pixels across.");
                }
                if (width < MIN_SIDE || height < MIN_SIDE) {
                    throw new PictureRejectedException("That picture is too small to use.");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (PictureRejectedException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // A codec fails on hostile input in more ways than it documents; every one of them
            // means the same thing to the reader, and none of its detail belongs in a reply.
            throw new PictureRejectedException("That file could not be read as a picture.");
        }
    }

    private static ImageReader jdkReader(String format) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
        while (readers.hasNext()) {
            ImageReader candidate = readers.next();
            if (candidate.getClass().getName().startsWith(JDK_CODECS)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No JDK reader for " + format);
    }

    /**
     * Onto an opaque canvas of this class's own, at most {@link #STORED_SIDE} on its longest
     * side. Transparency becomes white rather than the black a JPEG would otherwise make it.
     */
    private static BufferedImage redraw(BufferedImage source) {
        double scale = Math.min(1.0, (double) STORED_SIDE / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage current = source;
        // Halved in steps first: one bilinear pass across a large ratio skips pixels and shimmers.
        while (current.getWidth() / 2 >= width && current.getHeight() / 2 >= height) {
            current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return draw(current, width, height);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height) {
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return canvas;
    }

    private static byte[] encode(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            // No metadata passed: the file carries the default JFIF header and nothing else.
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode a picture", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
