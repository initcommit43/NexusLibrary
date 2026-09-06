package dev.nexus.core.preferences;

import dev.nexus.core.adapter.CharacterPortrait;
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
 * The character standing at the head of a reader's profile.
 *
 * <p>Scoped the same way the banner is, and for the same reason: the request names an entry of
 * the reader's own and a character within it, never a url. What that character's portrait
 * actually is gets resolved here, out of the title's cached detail, so nothing in a request can
 * point a profile at an arbitrary image.
 */
@Service
public class ProfilePictureService {

    private final ProfilePictureRepository pictures;
    private final UserEntryRepository entries;
    private final MediaDetailService details;
    private final MetadataAdapterRegistry adapters;

    public ProfilePictureService(
            ProfilePictureRepository pictures,
            UserEntryRepository entries,
            MediaDetailService details,
            MetadataAdapterRegistry adapters) {
        this.pictures = pictures;
        this.entries = entries;
        this.details = details;
        this.adapters = adapters;
    }

    @Transactional(readOnly = true)
    public Optional<ProfilePicture> forUser(long userId) {
        return pictures.findByUserId(userId);
    }

    /**
     * Points the profile at one character of a title behind one of this reader's entries.
     *
     * <p>The detail is fetched rather than assumed present: characters live there and not on
     * the item itself, and it is the same fetch that opening the title would make.
     */
    @Transactional
    public ProfilePicture choose(long userId, long entryId, String characterId) {
        UserEntry entry = entries.findByIdAndUserId(entryId, userId)
                .orElseThrow(EntryNotFoundException::new);

        TrackableItem item = details.findOrFetch(
                entry.getItem().getSource(), entry.getItem().getExternalId());
        CharacterPortrait character = characterOf(item, characterId)
                .orElseThrow(() -> new NoCharacterException(item.getTitle()));

        return pictures.findByUserId(userId)
                .map(held -> {
                    held.moveTo(item, character);
                    return held;
                })
                .orElseGet(() -> pictures.save(new ProfilePicture(userId, item, character)));
    }

    /**
     * Where the portrait sits inside the circle, which is a change to the framing and not to
     * the choice: the same character, held differently.
     */
    @Transactional
    public ProfilePicture frame(long userId, int focusX, int focusY, int zoom) {
        ProfilePicture picture =
                pictures.findByUserId(userId).orElseThrow(PictureNotSetException::new);
        picture.frame(focusX, focusY, zoom);
        return picture;
    }

    @Transactional
    public void clear(long userId) {
        pictures.deleteByUserId(userId);
    }

    /** Only the source that wrote a detail knows how it names and pictures its characters. */
    private Optional<CharacterPortrait> characterOf(TrackableItem item, String characterId) {
        if (!(item.getMetadata().get(MediaDetailService.DETAIL_KEY) instanceof Map<?, ?> detail)) {
            return Optional.empty();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) detail;
        return adapters.forSource(item.getSource())
                .flatMap(adapter -> adapter.characterFrom(typed, characterId));
    }
}
