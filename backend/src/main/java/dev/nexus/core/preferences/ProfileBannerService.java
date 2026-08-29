package dev.nexus.core.preferences;

import dev.nexus.core.adapter.MetadataAdapterRegistry;
import dev.nexus.core.catalog.MediaDetailService;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.UserEntry;
import dev.nexus.core.domain.UserEntryRepository;
import dev.nexus.core.tracking.EntryNotFoundException;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The banner at the head of a reader's profile.
 *
 * <p>Chosen from their own library rather than from the catalogue at large: the picker shows
 * covers they already have, and taking the entry's id rather than a source and an external id
 * means the choice is scoped by ownership the same way every other write is. There is no url
 * in the request at all, so nothing here can point a profile at an arbitrary image.
 */
@Service
public class ProfileBannerService {

    private final ProfileBannerRepository banners;
    private final UserEntryRepository entries;
    private final MediaDetailService details;
    private final MetadataAdapterRegistry adapters;

    public ProfileBannerService(
            ProfileBannerRepository banners,
            UserEntryRepository entries,
            MediaDetailService details,
            MetadataAdapterRegistry adapters) {
        this.banners = banners;
        this.entries = entries;
        this.details = details;
        this.adapters = adapters;
    }

    @Transactional(readOnly = true)
    public Optional<ProfileBanner> forUser(long userId) {
        return banners.findByUserId(userId);
    }

    /**
     * Points the profile at the title behind one of this reader's entries.
     *
     * <p>The detail is fetched rather than assumed present: wide art lives there and not on
     * the item itself, and a title the reader has never opened has no detail cached yet. It
     * is the same fetch that opening the title would make, and shared with everyone after.
     */
    @Transactional
    public ProfileBanner choose(long userId, long entryId) {
        UserEntry entry = entries.findByIdAndUserId(entryId, userId)
                .orElseThrow(EntryNotFoundException::new);

        TrackableItem item = details.findOrFetch(
                entry.getItem().getSource(), entry.getItem().getExternalId());
        String url = bannerOf(item).orElseThrow(() -> new NoBannerException(item.getTitle()));

        return banners.findByUserId(userId)
                .map(held -> {
                    held.moveTo(item, url);
                    return held;
                })
                .orElseGet(() -> banners.save(new ProfileBanner(userId, item, url)));
    }

    /**
     * Where the image sits inside the strip, which is a change to the framing and not to
     * the choice: the same picture, held differently.
     */
    @Transactional
    public ProfileBanner frame(long userId, int focusX, int focusY, int zoom) {
        ProfileBanner banner = banners.findByUserId(userId).orElseThrow(BannerNotSetException::new);
        banner.frame(focusX, focusY, zoom);
        return banner;
    }

    @Transactional
    public void clear(long userId) {
        banners.deleteByUserId(userId);
    }

    /** Only the source that wrote a detail knows where its wide art sits inside it. */
    private Optional<String> bannerOf(TrackableItem item) {
        if (!(item.getMetadata().get(MediaDetailService.DETAIL_KEY) instanceof Map<?, ?> detail)) {
            return Optional.empty();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) detail;
        return adapters.forSource(item.getSource()).flatMap(adapter -> adapter.bannerFrom(typed));
    }
}
