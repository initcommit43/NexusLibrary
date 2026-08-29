package dev.nexus.core.preferences;

import dev.nexus.auth.CurrentUser;
import dev.nexus.core.domain.MediaType;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The order a reader's favourite rows are shown in.
 *
 * <p>Keyed by the authenticated reader and taking no id from the request, as the module
 * switches beside it are: there is no id here to tamper with.
 */
@Validated
@RestController
@RequestMapping("/settings/favourite-rows")
public class FavouriteRowController {

    /**
     * The rows, first to last, and which of them share a band with the row before them.
     * Media types absent from the order keep the app's own.
     */
    public record RowOrder(
            @Size(max = 16) List<MediaType> order, @Size(max = 16) List<MediaType> paired) {

        static RowOrder of(FavouriteRowService.Arrangement arrangement) {
            return new RowOrder(arrangement.order(), List.copyOf(arrangement.paired()));
        }
    }

    private final FavouriteRowService rows;

    public FavouriteRowController(FavouriteRowService rows) {
        this.rows = rows;
    }

    @GetMapping
    public RowOrder order(@AuthenticationPrincipal CurrentUser user) {
        return RowOrder.of(rows.arrangementFor(user.id()));
    }

    @PutMapping
    public RowOrder replace(@AuthenticationPrincipal CurrentUser user, @RequestBody RowOrder body) {
        return RowOrder.of(rows.replaceFor(user.id(), body.order(), body.paired()));
    }
}
