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

    /** The rows, first to last. Media types absent from it keep the app's own order. */
    public record RowOrder(@Size(max = 16) List<MediaType> order) {}

    private final FavouriteRowService rows;

    public FavouriteRowController(FavouriteRowService rows) {
        this.rows = rows;
    }

    @GetMapping
    public RowOrder order(@AuthenticationPrincipal CurrentUser user) {
        return new RowOrder(rows.orderFor(user.id()));
    }

    @PutMapping
    public RowOrder replace(@AuthenticationPrincipal CurrentUser user, @RequestBody RowOrder body) {
        return new RowOrder(rows.replaceFor(user.id(), body.order()));
    }
}
