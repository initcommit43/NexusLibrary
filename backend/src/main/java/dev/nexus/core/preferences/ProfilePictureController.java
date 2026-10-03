package dev.nexus.core.preferences;

import dev.nexus.auth.CurrentUser;
import dev.nexus.config.NexusProperties;
import dev.nexus.core.web.RateLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The picture a reader put at the head of their profile.
 *
 * <p>Every route is the authenticated reader's own and takes no id: the picture is found by
 * who is asking, so no request can name another reader's — to read it, frame it, or replace
 * it. The image itself is served the same way, behind sign-in.
 */
@Validated
@RestController
@RequestMapping("/settings/profile-picture")
public class ProfilePictureController {

    /**
     * How the picture sits, and how to tell this upload from the last. {@code imageUrl} is set
     * only for a character picked before uploads: an upload is fetched from {@code /image}.
     */
    public record PictureResponse(String imageUrl, String version, int focusX, int focusY, int zoom) {

        static PictureResponse from(ProfilePicture picture) {
            return new PictureResponse(
                    picture.getUploadId() == null ? picture.getImageUrl() : null,
                    picture.getUploadId() == null ? null : picture.getUploadId().toString(),
                    picture.getFocusX(),
                    picture.getFocusY(),
                    picture.getZoom());
        }
    }

    /*
     * Locks the image down as far as an image can be: never sniffed into anything else, never
     * run as a document if opened directly, never embedded by another site, never kept in a
     * shared cache.
     */
    private static final String NOTHING_RUNS = "default-src 'none'; img-src 'self'; sandbox";

    private final ProfilePictureService pictures;
    private final PictureSanitizer sanitizer;
    private final RateLimiter rateLimiter;
    private final int uploadsPerMinute;

    public ProfilePictureController(
            ProfilePictureService pictures,
            PictureSanitizer sanitizer,
            RateLimiter rateLimiter,
            NexusProperties properties) {
        this.pictures = pictures;
        this.sanitizer = sanitizer;
        this.rateLimiter = rateLimiter;
        this.uploadsPerMinute = properties.rateLimit().pictureUploadsPerMinute();
    }

    /** Answers with nothing at all when none is set, which is what a plain icon is. */
    @GetMapping
    public PictureResponse current(@AuthenticationPrincipal CurrentUser user) {
        return pictures.forUser(user.id()).map(PictureResponse::from).orElse(null);
    }

    /**
     * A new picture, and how the reader cropped it before sending, in one request: a picture
     * stored first and framed second would show uncropped to anyone looking in between.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PictureResponse upload(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam("file") MultipartFile file,
            @RequestParam @Min(0) @Max(100) int focusX,
            @RequestParam @Min(0) @Max(100) int focusY,
            @RequestParam @Min(100) @Max(300) int zoom) {

        rateLimiter.check("picture-upload:" + user.id(), uploadsPerMinute);

        PictureSanitizer.Sanitized clean = sanitizer.sanitize(read(file));
        return PictureResponse.from(pictures.store(user.id(), clean, focusX, focusY, zoom));
    }

    /** The reader's own uploaded picture, as the server encoded it, and nobody else's. */
    @GetMapping("/image")
    public ResponseEntity<byte[]> image(
            @AuthenticationPrincipal CurrentUser user,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String cached) {

        ProfilePicture picture = pictures.forUser(user.id())
                .filter(held -> held.getUploadId() != null)
                .orElseThrow(PictureNotSetException::new);
        String tag = "\"" + picture.getUploadId() + "\"";

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .eTag(tag)
                .cacheControl(CacheControl.maxAge(0, TimeUnit.SECONDS).cachePrivate().mustRevalidate())
                .header("Content-Security-Policy", NOTHING_RUNS)
                .header("Cross-Origin-Resource-Policy", "same-origin")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"profile-picture.jpg\"");

        if (tag.equals(cached)) {
            return ResponseEntity.status(304).headers(response.build().getHeaders()).build();
        }
        byte[] bytes = pictures.uploadOf(user.id())
                .map(PictureUpload::getContent)
                .orElseThrow(PictureNotSetException::new);
        return response.contentType(MediaType.IMAGE_JPEG).contentLength(bytes.length).body(bytes);
    }

    @PatchMapping
    public PictureResponse frame(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody Framing body) {
        return PictureResponse.from(
                pictures.frame(user.id(), body.focusX(), body.focusY(), body.zoom()));
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(@AuthenticationPrincipal CurrentUser user) {
        pictures.clear(user.id());
        return ResponseEntity.noContent().build();
    }

    /**
     * At most one byte past the limit is read, whatever the request claims about its size: that
     * byte is all it takes to know the file is too large, and the rest is never brought in.
     */
    private static byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new PictureRejectedException("No picture was uploaded.");
        }
        if (file.getSize() > PictureSanitizer.MAX_BYTES) {
            throw new PictureTooLargeException();
        }
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(PictureSanitizer.MAX_BYTES + 1);
        } catch (IOException e) {
            throw new PictureRejectedException("That picture could not be read. Please try again.");
        }
    }
}
