package dev.nexus.core.adapter;

/**
 * One character out of a title, reduced to the three things anything outside its source needs.
 *
 * <p>The id is the source's own, so a choice made now can be resolved again later; the name is
 * what gets credited; the url is where the portrait lives. Nothing here is the source's shape
 * — that stays behind the adapter that read it.
 */
public record CharacterPortrait(String id, String name, String imageUrl) {}
