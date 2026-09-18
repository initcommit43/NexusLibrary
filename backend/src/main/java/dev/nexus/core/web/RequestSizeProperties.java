package dev.nexus.core.web;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * How large a JSON request body may be before it is refused outright.
 *
 * <p>Multipart uploads (the CSV import, and nothing else) declare their own, larger ceiling
 * under {@code spring.servlet.multipart}; nothing otherwise bounds an ordinary JSON body at
 * all, and reading one of any size is enough to hold a thread and fill memory decoding it.
 */
@Validated
@ConfigurationProperties(prefix = "nexus.request")
public record RequestSizeProperties(@NotNull DataSize maxJsonBodySize) {}
