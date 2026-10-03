package dev.nexus.core.account;

import dev.nexus.auth.AppUser;
import dev.nexus.auth.agreements.AgreementAcceptanceRepository;
import dev.nexus.core.content.UserContentPreferenceRepository;
import dev.nexus.core.domain.ActivityRepository;
import dev.nexus.core.domain.ExternalAccountRepository;
import dev.nexus.core.domain.NotificationRepository;
import dev.nexus.core.domain.ProviderActivityRepository;
import dev.nexus.core.domain.ReviewRepository;
import dev.nexus.core.domain.UserEntryRepository;
import dev.nexus.core.preferences.DisabledModuleRepository;
import dev.nexus.core.preferences.FavouriteRowRepository;
import dev.nexus.core.preferences.ProfileBannerRepository;
import dev.nexus.core.preferences.PictureUpload;
import dev.nexus.core.preferences.PictureUploadRepository;
import dev.nexus.core.preferences.ProfilePictureRepository;
import dev.nexus.core.tracking.dto.TrackedItemResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything this service holds about one reader, as a file they can take away.
 *
 * <p>Its own class rather than three more fields on {@link AccountService}: answering Art. 15
 * properly means reading most of the schema, and a constructor listing a dozen repositories
 * belongs to the thing that needs them rather than to the thing that also changes passwords.
 *
 * <p><strong>What is deliberately left out, and must stay out.</strong> Anything that is a
 * credential rather than a record of the reader: OAuth access tokens (encrypted at rest, and
 * the privacy policy promises they are never exported), refresh tokens, and the digests of
 * confirmation and reset links. An export is a file that ends up in a downloads folder; a live
 * session in one is a session for whoever finds it. A refresh token is not a fact about
 * somebody, it is the ability to act as them, and Art. 15 is not a reason to hand it over in
 * writing.
 *
 * <p>Row counts are bounded, and when a bound is reached the file says so under
 * {@code truncated} rather than quietly ending. A short export that looks complete is worse
 * than one that admits where it stops.
 */
@Service
public class AccountExport {

    /**
     * Per collection, not overall. An export is one request holding its whole answer in memory,
     * so this is where a library stops being one — but it is high enough that reaching it is a
     * real reader's real history, which is why reaching it is now reported.
     */
    private static final int MAX_ROWS = 20_000;

    private final UserEntryRepository entries;
    private final ExternalAccountRepository accounts;
    private final ActivityRepository activity;
    private final ReviewRepository reviews;
    private final NotificationRepository notifications;
    private final ProviderActivityRepository providerActivity;
    private final AgreementAcceptanceRepository acceptances;
    private final UserContentPreferenceRepository contentPreferences;
    private final DisabledModuleRepository disabledModules;
    private final FavouriteRowRepository favouriteRows;
    private final ProfileBannerRepository banners;
    private final ProfilePictureRepository pictures;
    private final PictureUploadRepository uploads;

    public AccountExport(
            UserEntryRepository entries,
            ExternalAccountRepository accounts,
            ActivityRepository activity,
            ReviewRepository reviews,
            NotificationRepository notifications,
            ProviderActivityRepository providerActivity,
            AgreementAcceptanceRepository acceptances,
            UserContentPreferenceRepository contentPreferences,
            DisabledModuleRepository disabledModules,
            FavouriteRowRepository favouriteRows,
            ProfileBannerRepository banners,
            ProfilePictureRepository pictures,
            PictureUploadRepository uploads) {
        this.entries = entries;
        this.accounts = accounts;
        this.activity = activity;
        this.reviews = reviews;
        this.notifications = notifications;
        this.providerActivity = providerActivity;
        this.acceptances = acceptances;
        this.contentPreferences = contentPreferences;
        this.disabledModules = disabledModules;
        this.favouriteRows = favouriteRows;
        this.banners = banners;
        this.pictures = pictures;
        this.uploads = uploads;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> of(AppUser user) {
        long userId = user.getId();
        List<String> truncated = new ArrayList<>();

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", Instant.now().toString());
        export.put("account", account(user));
        export.put("agreementsAccepted", agreements(userId));
        export.put("contentSettings", contentSettings(userId));
        export.put("entries", entries.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(TrackedItemResponse::from)
                .toList());
        export.put("reviews", reviews(userId));
        export.put("connectedAccounts", connections(userId));
        export.put("activity", activity(userId, truncated));
        export.put("providerActivity", providerActivity(userId, truncated));
        export.put("notifications", notifications(userId, truncated));
        export.put("modulePreferences", modulePreferences(userId));
        export.put("profile", profile(userId));
        export.put("truncated", truncated);
        return export;
    }

    /** Not Map.of: an account made before V23 has no date of birth, and the export says so. */
    private Map<String, Object> account(AppUser user) {
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("email", user.getEmail());
        account.put("username", user.getUsername());
        account.put("registeredAt", String.valueOf(user.getCreatedAt()));
        account.put("dateOfBirth", user.getDateOfBirth() == null ? null : user.getDateOfBirth().toString());
        account.put("emailVerified", user.isEmailVerified());
        return account;
    }

    /** What this reader agreed to and when — the record that answers "who consented to what". */
    private List<Map<String, Object>> agreements(long userId) {
        return acceptances.findByUserIdOrderByAcceptedAtDesc(userId).stream()
                .map(row -> row(
                        "document", String.valueOf(row.getDocument()),
                        "platform", String.valueOf(row.getPlatform()),
                        "version", row.getVersion(),
                        "acceptedAt", String.valueOf(row.getAcceptedAt())))
                .toList();
    }

    private Map<String, Object> contentSettings(long userId) {
        return contentPreferences
                .findByUserId(userId)
                .map(preference -> row("showAdult", preference.isShowAdult(), "blurAdult", preference.isBlurAdult()))
                // The defaults a reader who never opened the setting is on, said out loud
                // rather than left absent for them to guess at.
                .orElseGet(() -> row("showAdult", false, "blurAdult", true));
    }

    /**
     * The reader's own writing, and the largest thing the export used to leave out. Carries the
     * entry's id so it can be read beside the entry it belongs to, which is in this same file.
     */
    private List<Map<String, Object>> reviews(long userId) {
        return reviews.findAllForUser(userId).stream()
                .map(review -> row(
                        "entryId", review.getEntry().getId(),
                        "title", review.getEntry().getItem().getTitle(),
                        "body", review.getBody(),
                        "containsSpoilers", review.isContainsSpoilers(),
                        "createdAt", String.valueOf(review.getCreatedAt()),
                        "updatedAt", String.valueOf(review.getUpdatedAt())))
                .toList();
    }

    private List<Map<String, Object>> connections(long userId) {
        return accounts.findByUserId(userId).stream()
                .map(account -> row(
                        "provider", String.valueOf(account.getProvider()),
                        "externalUserId", account.getExternalUserId(),
                        "connectedAt", String.valueOf(account.getConnectedAt())))
                .toList();
    }

    /**
     * The whole row, not its type and date. What an activity entry meant — which title, and
     * what changed — is the part a reader would recognise as theirs.
     */
    private List<Map<String, Object>> activity(long userId, List<String> truncated) {
        var rows = activity.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(MAX_ROWS));
        note(truncated, "activity", rows.size());

        return rows.stream()
                .map(row -> row(
                        "type", String.valueOf(row.getType()),
                        "title", row.getItem() == null ? null : row.getItem().getTitle(),
                        "payload", row.getPayload(),
                        "createdAt", String.valueOf(row.getCreatedAt())))
                .toList();
    }

    private List<Map<String, Object>> providerActivity(long userId, List<String> truncated) {
        var rows = providerActivity.findByUserIdOrderByHappenedOnDescIdDesc(userId, Limit.of(MAX_ROWS));
        note(truncated, "providerActivity", rows.size());

        return rows.stream()
                .map(row -> row(
                        "provider", String.valueOf(row.getProvider()),
                        "externalId", row.getExternalId(),
                        "status", row.getStatus(),
                        "progress", row.getProgress(),
                        "happenedOn", String.valueOf(row.getHappenedOn())))
                .toList();
    }

    private List<Map<String, Object>> notifications(long userId, List<String> truncated) {
        var rows = notifications.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(MAX_ROWS));
        note(truncated, "notifications", rows.size());

        return rows.stream()
                .map(row -> row(
                        "type", String.valueOf(row.getType()),
                        "subject", row.getSubject(),
                        "title", row.getItem() == null ? null : row.getItem().getTitle(),
                        "payload", row.getPayload(),
                        "readAt", String.valueOf(row.getReadAt()),
                        "createdAt", String.valueOf(row.getCreatedAt())))
                .toList();
    }

    private Map<String, Object> modulePreferences(long userId) {
        return row(
                "disabledModules",
                        disabledModules.findByUserId(userId).stream()
                                .map(module -> String.valueOf(module.getMediaType()))
                                .toList(),
                "favouriteRows",
                        favouriteRows.findByUserIdOrderBySortOrderAsc(userId).stream()
                                // Already in the reader's own order, which is what sort_order
                                // encodes — so the order of this list is the value.
                                .map(favourite -> row(
                                        "mediaType", String.valueOf(favourite.getMediaType()),
                                        "sharesLane", favourite.sharesLane()))
                                .toList());
    }

    /**
     * The banner, and a picture picked before uploads, are references to catalogue art. An
     * uploaded picture is the reader's own file and comes back with them, as the server
     * encoded it.
     */
    private Map<String, Object> profile(long userId) {
        Map<String, Object> profile = new LinkedHashMap<>();

        profile.put(
                "picture",
                pictures.findByUserId(userId)
                        .map(picture -> row(
                                "characterName", picture.getCharacterName(),
                                "imageUrl", picture.getImageUrl(),
                                "uploadedImage", uploads.findByUserId(userId)
                                        .map(PictureUpload::getContent)
                                        .map(bytes -> "data:image/jpeg;base64,"
                                                + Base64.getEncoder().encodeToString(bytes))
                                        .orElse(null),
                                "chosenAt", String.valueOf(picture.getChosenAt())))
                        .orElse(null));
        profile.put(
                "banner",
                banners.findByUserId(userId)
                        .map(banner -> row(
                                "imageUrl", banner.getImageUrl(),
                                "chosenAt", String.valueOf(banner.getChosenAt())))
                        .orElse(null));
        return profile;
    }

    private static void note(List<String> truncated, String collection, int returned) {
        if (returned >= MAX_ROWS) {
            truncated.add(collection + " stops at " + MAX_ROWS + " rows, newest first");
        }
    }

    /** {@code Map.of} refuses nulls, and half of what is exported is legitimately absent. */
    private static Map<String, Object> row(Object... keysAndValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            row.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return row;
    }
}
