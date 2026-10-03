package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.BinaryResponse;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * An uploaded profile picture: whose it is, who can see it, and what of the upload survives.
 *
 * <p>The authorization half is the point of most of these. Every route is keyed by the signed-in
 * reader alone, and these hold that to the claim: no other reader, and nobody signed out, can
 * read, frame, replace or remove someone's picture by any route.
 */
class ProfilePictureIntegrationTest extends PostgresIntegrationTest {

    private static final String PICTURE = "/settings/profile-picture";

    @LocalServerPort
    int port;

    private HttpTestClient http;
    private String ownerToken;
    private String intruderToken;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);
        ownerToken = registerAndGetToken(http, "owner@example.com", "owner");
        intruderToken = registerAndGetToken(http, "intruder@example.com", "intruder");
    }

    @Test
    void thereIsNoPictureUntilTheReaderUploadsOne() {
        assertThat(current(ownerToken)).isEmpty();
        assertThat(image(ownerToken).status()).isEqualTo(404);
    }

    @Test
    void anUploadIsStoredWithTheFramingItWasCroppedTo() throws IOException {
        Response response = upload(ownerToken, png(400, 300), 30, 60, 150);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body())
                .containsEntry("focusX", 30)
                .containsEntry("focusY", 60)
                .containsEntry("zoom", 150)
                .containsEntry("imageUrl", null)
                .containsKey("version");
        assertThat(current(ownerToken)).containsEntry("version", response.body().get("version"));
    }

    /** Served as a JPEG the server wrote, under headers that keep it from being anything else. */
    @Test
    void theImageIsServedAsAJpegThatCannotRunAsAPage() throws IOException {
        upload(ownerToken, png(400, 300), 50, 50, 100);

        BinaryResponse served = image(ownerToken);

        assertThat(served.status()).isEqualTo(200);
        assertThat(served.header("Content-Type")).contains("image/jpeg");
        assertThat(served.header("X-Content-Type-Options")).contains("nosniff");
        assertThat(served.header("Content-Security-Policy").orElseThrow()).contains("sandbox");
        assertThat(served.header("Cache-Control").orElseThrow()).contains("private");
        assertThat(served.body()[0]).isEqualTo((byte) 0xFF);
        assertThat(served.body()[1]).isEqualTo((byte) 0xD8);
    }

    @Test
    void anotherReaderCannotSeeSomeoneElsesPicture() throws IOException {
        upload(ownerToken, png(400, 300), 50, 50, 100);

        assertThat(image(intruderToken).status()).isEqualTo(404);
        assertThat(current(intruderToken)).isEmpty();
    }

    @Test
    void anotherReaderCannotReframeOrRemoveSomeoneElsesPicture() throws IOException {
        upload(ownerToken, png(400, 300), 30, 30, 120);

        assertThat(frame(intruderToken, 90, 90, 300).status()).isEqualTo(404);
        http.delete(PICTURE, "Authorization", "Bearer " + intruderToken);

        assertThat(current(ownerToken))
                .containsEntry("focusX", 30)
                .containsEntry("focusY", 30)
                .containsEntry("zoom", 120);
        assertThat(image(ownerToken).status()).isEqualTo(200);
    }

    /** An upload only ever lands on the uploader's own profile. */
    @Test
    void anotherReadersUploadDoesNotTouchSomeoneElsesPicture() throws IOException {
        String ownerVersion = (String) upload(ownerToken, png(400, 300), 50, 50, 100).body().get("version");

        upload(intruderToken, png(200, 200), 50, 50, 100);

        assertThat(current(ownerToken)).containsEntry("version", ownerVersion);
    }

    @Test
    void nothingIsReachableSignedOut() throws IOException {
        upload(ownerToken, png(400, 300), 50, 50, 100);

        assertThat(http.getBytes(PICTURE + "/image").status()).isEqualTo(401);
        assertThat(http.get(PICTURE).status()).isEqualTo(401);
        assertThat(http.postMultipart(PICTURE + "/upload?focusX=50&focusY=50&zoom=100", "file", "a.png", png(64, 64))
                        .status())
                .isEqualTo(401);
    }

    /** Named and sent as a PNG, but its bytes are a page. Nothing is stored. */
    @Test
    void aFileThatIsNotAPictureIsRefused() {
        byte[] page = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        Response response = upload(ownerToken, page, 50, 50, 100);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().get("message").toString()).contains("JPEG and PNG");
        assertThat(current(ownerToken)).isEmpty();
    }

    @Test
    void anUploadOverAMegabyteIsRefused() {
        byte[] heavy = new byte[1024 * 1024 + 1];
        heavy[0] = (byte) 0x89;

        Response response = upload(ownerToken, heavy, 50, 50, 100);

        assertThat(response.status()).isEqualTo(413);
        assertThat(current(ownerToken)).isEmpty();
    }

    /** These go straight into the style the circle is drawn with, so they are bounded here. */
    @Test
    void framingOutsideThePictureIsRefusedOnUpload() throws IOException {
        assertThat(upload(ownerToken, png(100, 100), 50, 140, 100).status()).isEqualTo(400);
        assertThat(upload(ownerToken, png(100, 100), 50, 50, 900).status()).isEqualTo(400);
        assertThat(upload(ownerToken, png(100, 100), 50, 50, 40).status()).isEqualTo(400);
        assertThat(current(ownerToken)).isEmpty();
    }

    @Test
    void whatTheFileCarriedBesidesThePictureIsNotServed() throws IOException {
        byte[] png = png(120, 120);
        byte[] tail = "<script>steal()</script>".getBytes(StandardCharsets.US_ASCII);
        byte[] polyglot = ByteBuffer.allocate(png.length + tail.length).put(png).put(tail).array();

        upload(ownerToken, polyglot, 50, 50, 100);

        String served = new String(image(ownerToken).body(), StandardCharsets.ISO_8859_1);
        assertThat(served).doesNotContain("<script");
    }

    @Test
    void aNewUploadReplacesTheOldOne() throws IOException {
        String first = (String) upload(ownerToken, png(400, 300), 50, 50, 100).body().get("version");
        String second = (String) upload(ownerToken, png(200, 200), 20, 20, 200).body().get("version");

        assertThat(second).isNotEqualTo(first);
        assertThat(image(ownerToken, "\"" + first + "\"").status()).isEqualTo(200);
        assertThat(image(ownerToken, "\"" + second + "\"").status()).isEqualTo(304);
    }

    @Test
    void theFramingIsRememberedAgainstTheSamePicture() throws IOException {
        upload(ownerToken, png(400, 300), 50, 50, 100);

        assertThat(frame(ownerToken, 30, 20, 175).status()).isEqualTo(200);
        assertThat(current(ownerToken))
                .containsEntry("focusX", 30)
                .containsEntry("focusY", 20)
                .containsEntry("zoom", 175);
    }

    @Test
    void framingOutsideThePictureIsRefused() throws IOException {
        upload(ownerToken, png(400, 300), 50, 50, 100);

        assertThat(frame(ownerToken, 30, 140, 100).status()).isEqualTo(400);
        assertThat(frame(ownerToken, 30, 40, 900).status()).isEqualTo(400);
        assertThat(frame(ownerToken, 30, 40, 40).status()).isEqualTo(400);
    }

    @Test
    void thereIsNothingToFrameBeforeAPictureIsUploaded() {
        assertThat(frame(ownerToken, 30, 20, 150).status()).isEqualTo(404);
    }

    @Test
    void removingThePictureTakesItsImageWithIt() throws IOException {
        upload(ownerToken, png(400, 300), 50, 50, 100);

        assertThat(http.delete(PICTURE, "Authorization", "Bearer " + ownerToken).status()).isEqualTo(204);

        assertThat(current(ownerToken)).isEmpty();
        assertThat(image(ownerToken).status()).isEqualTo(404);
    }

    private Response upload(String token, byte[] file, int focusX, int focusY, int zoom) {
        return http.postMultipart(
                PICTURE + "/upload?focusX=" + focusX + "&focusY=" + focusY + "&zoom=" + zoom,
                "file",
                "picture.png",
                file,
                "Authorization",
                "Bearer " + token);
    }

    private Response frame(String token, int focusX, int focusY, int zoom) {
        return http.patchJson(
                PICTURE,
                Map.of("focusX", focusX, "focusY", focusY, "zoom", zoom),
                "Authorization",
                "Bearer " + token);
    }

    private BinaryResponse image(String token) {
        return http.getBytes(PICTURE + "/image", "Authorization", "Bearer " + token);
    }

    private BinaryResponse image(String token, String etag) {
        return http.getBytes(PICTURE + "/image", "Authorization", "Bearer " + token, "If-None-Match", etag);
    }

    private Map<String, Object> current(String token) {
        Response response = http.get(PICTURE, "Authorization", "Bearer " + token);
        assertThat(response.status()).isEqualTo(200);
        return response.body();
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage picture = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                picture.setRGB(x, y, (x * 5 + y * 11) & 0xFFFFFF);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(picture, "png", out);
        return out.toByteArray();
    }
}
