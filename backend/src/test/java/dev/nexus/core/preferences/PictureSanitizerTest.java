package dev.nexus.core.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** What an upload has to be to become a picture, and what of it survives the trip. */
class PictureSanitizerTest {

    private final PictureSanitizer sanitizer = new PictureSanitizer();

    @Test
    void aPngComesOutAsAJpegOfTheSameSize() throws IOException {
        PictureSanitizer.Sanitized clean = sanitizer.sanitize(image("png", 300, 200));

        assertThat(PictureSanitizer.formatOf(clean.jpeg())).contains("jpeg");
        BufferedImage read = ImageIO.read(new ByteArrayInputStream(clean.jpeg()));
        assertThat(read.getWidth()).isEqualTo(300);
        assertThat(read.getHeight()).isEqualTo(200);
        assertThat(clean.width()).isEqualTo(300);
    }

    @Test
    void aLargePictureIsShrunkToTheStoredSize() throws IOException {
        PictureSanitizer.Sanitized clean = sanitizer.sanitize(image("jpeg", 3000, 1500));

        assertThat(clean.width()).isEqualTo(PictureSanitizer.STORED_SIDE);
        assertThat(clean.height()).isEqualTo(PictureSanitizer.STORED_SIDE / 2);
    }

    /** Named and typed as a picture by the client, but its bytes say otherwise. */
    @Test
    void aFileThatIsNotAPictureIsRefusedByItsBytes() {
        byte[] page = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> sanitizer.sanitize(page))
                .isInstanceOf(PictureRejectedException.class)
                .hasMessageContaining("JPEG and PNG");
    }

    @Test
    void aJpegThatIsOnlyItsMagicNumberIsRefused() {
        byte[] fake = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x13, 0x37, 0x00, 0x00};

        assertThatThrownBy(() -> sanitizer.sanitize(fake))
                .isInstanceOf(PictureRejectedException.class)
                .hasMessageContaining("could not be read");
    }

    /** A few dozen bytes announcing a picture that would take gigabytes to decode. */
    @Test
    void anEnormousDeclaredSizeIsRefusedBeforeDecoding() {
        assertThatThrownBy(() -> sanitizer.sanitize(pngHeaderOnly(20_000, 20_000)))
                .isInstanceOf(PictureRejectedException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void aPictureTooSmallToShowIsRefused() {
        assertThatThrownBy(() -> sanitizer.sanitize(image("png", 8, 8)))
                .isInstanceOf(PictureRejectedException.class)
                .hasMessageContaining("too small");
    }

    @Test
    void anythingOverAMegabyteIsRefusedUnread() {
        byte[] heavy = new byte[PictureSanitizer.MAX_BYTES + 1];
        System.arraycopy(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 0, heavy, 0, 8);

        assertThatThrownBy(() -> sanitizer.sanitize(heavy)).isInstanceOf(PictureTooLargeException.class);
    }

    @Test
    void anEmptyUploadIsRefused() {
        assertThatThrownBy(() -> sanitizer.sanitize(new byte[0])).isInstanceOf(PictureRejectedException.class);
    }

    /** A comment segment stands in for EXIF: whatever the file says about itself stays behind. */
    @Test
    void whatTheFileSaysAboutItselfIsNotKept() throws IOException {
        byte[] jpeg = image("jpeg", 120, 120);
        byte[] note = "GPS 48.2082 16.3738 secret-camera".getBytes(StandardCharsets.US_ASCII);
        byte[] tagged = ByteBuffer.allocate(jpeg.length + 4 + note.length)
                .put(jpeg, 0, 2)
                .put((byte) 0xFF).put((byte) 0xFE)
                .putShort((short) (note.length + 2))
                .put(note)
                .put(jpeg, 2, jpeg.length - 2)
                .array();

        byte[] clean = sanitizer.sanitize(tagged).jpeg();

        assertThat(new String(clean, StandardCharsets.ISO_8859_1)).doesNotContain("secret-camera");
    }

    /** A valid picture with a script on its tail is a polyglot; only the picture comes back. */
    @Test
    void bytesAfterThePictureEndsAreNotKept() throws IOException {
        byte[] png = image("png", 64, 64);
        byte[] payload = "<script>alert(document.cookie)</script>".getBytes(StandardCharsets.US_ASCII);
        byte[] polyglot = ByteBuffer.allocate(png.length + payload.length).put(png).put(payload).array();

        byte[] clean = sanitizer.sanitize(polyglot).jpeg();

        assertThat(new String(clean, StandardCharsets.ISO_8859_1)).doesNotContain("<script");
    }

    private static byte[] image(String format, int width, int height) throws IOException {
        BufferedImage picture = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                picture.setRGB(x, y, (x * 7 + y * 13) & 0xFFFFFF);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(picture, format, out);
        return out.toByteArray();
    }

    /** A PNG signature and a well-formed header chunk claiming the given size, and no pixels. */
    private static byte[] pngHeaderOnly(int width, int height) {
        ByteBuffer header = ByteBuffer.allocate(17)
                .put("IHDR".getBytes(StandardCharsets.US_ASCII))
                .putInt(width)
                .putInt(height)
                .put((byte) 8)
                .put((byte) 2)
                .put((byte) 0)
                .put((byte) 0)
                .put((byte) 0);
        CRC32 crc = new CRC32();
        crc.update(header.array());

        return ByteBuffer.allocate(8 + 4 + 17 + 4)
                .put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})
                .putInt(13)
                .put(header.array())
                .putInt((int) crc.getValue())
                .array();
    }
}
