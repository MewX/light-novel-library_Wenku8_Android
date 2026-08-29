package org.mewx.wenku8.reader;

import org.mewx.wenku8.global.api.OldNovelContentParser;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Decides where a chapter's text comes from: the downloaded copy, or the network.
 *
 * <p>This is the decision Phase 1 item 10 fixed. Treating a downloaded chapter as the only
 * possible source turned one interrupted download into a permanent dead end — the reader reported
 * a server error for a server it had never contacted, and failed identically on every later
 * attempt. The cache is preferred but never required, and an unusable one is reported rather than
 * silently retried, so how often downloads produce bad files stays visible.
 *
 * <p>Both readers ran their own copy of this inside an {@code AsyncTask.doInBackground}, reachable
 * only by opening a chapter on a device. The I/O is injected here so the decision can be tested
 * without a filesystem or a network, and the outcome is deliberately neutral rather than an
 * {@code ErrorCode}: the two readers map failures differently — {@code Wenku8ReaderActivityV1}
 * separates an empty response from a parse failure, {@code VerticalReaderActivity} does not — and
 * a shared seam must not quietly change either.
 */
public final class ChapterContentLoader {

    private ChapterContentLoader() {
    }

    /** Where the text came from, or why it could not be got. */
    public enum Outcome {
        /** The downloaded copy was present and parsed. The network was not contacted. */
        LOADED_FROM_CACHE,

        /** Fetched and parsed from the network. */
        LOADED_FROM_NETWORK,

        /** The network call returned nothing at all, so no response was received. */
        NETWORK_UNAVAILABLE,

        /** The server answered with an empty body. */
        EMPTY_RESPONSE,

        /** The server answered, but the body did not parse into any content. */
        PARSE_FAILED,

        /** The response bytes could not be decoded. Not reachable in practice; UTF-8 is required
         *  of every JVM. Kept because the readers each map it to a distinct code. */
        ENCODING_UNSUPPORTED,
    }

    /** Why a downloaded chapter could not be used. Reported so bad downloads stay countable. */
    public enum CacheProblem {
        /** No file, or a zero-length one. */
        MISSING_OR_EMPTY,

        /** A file with bytes in it that yielded no content — a truncated download. */
        UNPARSEABLE,
    }

    /** Supplies the downloaded chapter, or {@code ""} when there is none. */
    public interface CachedChapterSource {
        String load();
    }

    /** Fetches the chapter, returning {@code null} when the request could not be completed. */
    public interface NetworkChapterSource {
        byte[] fetch();
    }

    /** Told when a downloaded chapter was present but unusable, before the network is tried. */
    public interface CacheProblemListener {
        void onUnusableCache(CacheProblem problem);
    }

    /** What the load produced. {@link #content} is empty unless the outcome is a LOADED_* one. */
    public static final class Result {
        public final Outcome outcome;
        public final List<OldNovelContentParser.NovelContent> content;

        private Result(Outcome outcome, List<OldNovelContentParser.NovelContent> content) {
            this.outcome = outcome;
            this.content = content;
        }

        public boolean isLoaded() {
            return outcome == Outcome.LOADED_FROM_CACHE || outcome == Outcome.LOADED_FROM_NETWORK;
        }
    }

    private static Result failure(Outcome outcome) {
        return new Result(outcome, Collections.emptyList());
    }

    /**
     * @param preferCache    whether the downloaded copy should be tried first, i.e. the reader was
     *                       opened from the bookshelf
     * @param cached         the downloaded chapter; consulted only when {@code preferCache}
     * @param network        the fallback, and the only source when {@code preferCache} is false
     * @param onProgress     forwarded to the parser so a reader can size its progress bar
     * @param cacheProblem   notified when a downloaded chapter existed but could not be used
     */
    public static Result load(boolean preferCache,
                              CachedChapterSource cached,
                              NetworkChapterSource network,
                              IntConsumer onProgress,
                              CacheProblemListener cacheProblem) {
        if (preferCache) {
            final String text = cached.load();
            if (!text.isEmpty()) {
                final List<OldNovelContentParser.NovelContent> parsed =
                        OldNovelContentParser.parseNovelContent(text, onProgress);
                if (!parsed.isEmpty()) {
                    return new Result(Outcome.LOADED_FROM_CACHE, parsed);
                }
            }
            cacheProblem.onUnusableCache(
                    text.isEmpty() ? CacheProblem.MISSING_OR_EMPTY : CacheProblem.UNPARSEABLE);
        }

        final byte[] response = network.fetch();
        if (response == null) {
            return failure(Outcome.NETWORK_UNAVAILABLE);
        }

        final String xml;
        try {
            xml = new String(response, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return failure(Outcome.ENCODING_UNSUPPORTED);
        }

        final List<OldNovelContentParser.NovelContent> parsed =
                OldNovelContentParser.parseNovelContent(xml, onProgress);
        if (parsed.isEmpty()) {
            return failure(xml.isEmpty() ? Outcome.EMPTY_RESPONSE : Outcome.PARSE_FAILED);
        }

        return new Result(Outcome.LOADED_FROM_NETWORK, parsed);
    }
}
