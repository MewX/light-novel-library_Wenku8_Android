package org.mewx.wenku8.global.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Narrows a bookshelf to the novels matching what was typed.
 *
 * <p>Everything it needs is already on the device: a shelf row is built by
 * {@link NovelItemInfoUpdate#convertFromMeta} out of a novel's cached metadata, so filtering costs
 * no request and no new storage. A shelf is a few dozen novels, so a scan per keystroke is far too
 * cheap to be worth indexing or moving off the main thread.
 *
 * <p>Pure and separate from any screen so it can be tested directly, the same reason
 * {@link BookshelfSync} and {@link NovelDownloader} are.
 */
public final class BookshelfFilter {

    private BookshelfFilter() {
    }

    /**
     * @param shelf the novels on the device, in the order they are shown
     * @param query what the reader typed; blank means everything
     * @return the matching novels, keeping the shelf's order
     */
    @NonNull
    public static List<NovelItemInfoUpdate> filter(@Nullable List<NovelItemInfoUpdate> shelf,
                                                   @Nullable String query) {
        final List<NovelItemInfoUpdate> matches = new ArrayList<>();
        if (shelf == null) {
            return matches;
        }

        final String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            matches.addAll(shelf);
            return matches;
        }

        for (NovelItemInfoUpdate novel : shelf) {
            if (novel != null && matches(novel, needle)) {
                matches.add(novel);
            }
        }
        return matches;
    }

    /**
     * Matches against what the row actually puts on screen: its title, its author, and the latest
     * chapter the bookshelf shows in place of a synopsis.
     *
     * <p>{@link NovelItemInfoUpdate#intro_short} is deliberately not searched. A shelf row never
     * has one -- {@code convertFromMeta} leaves it at the "Loading..." sentinel -- so including it
     * would mean typing "loading" selected the whole shelf while matching nothing a reader can
     * see. Fields still holding that sentinel are skipped for the same reason.
     */
    private static boolean matches(@NonNull NovelItemInfoUpdate novel, @NonNull String needle) {
        return contains(novel.title, needle)
                || contains(novel.author, needle)
                || contains(novel.latest_chapter, needle);
    }

    private static boolean contains(@Nullable String field, @NonNull String needle) {
        if (field == null || NovelItemInfoUpdate.isMissing(field)) {
            return false;
        }
        // Lower-cased on both sides so a Latin title matches however it was typed; a no-op for the
        // Chinese titles that make up most of a shelf.
        return field.toLowerCase(Locale.ROOT).contains(needle);
    }
}
