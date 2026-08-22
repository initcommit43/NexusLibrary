package dev.nexus.core.adapter;

import dev.nexus.core.domain.Source;

/** An item identified in the catalogue that owns it, ready for cache-on-miss. */
public record CanonicalRef(Source source, String externalId) {}
