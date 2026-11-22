package org.mewx.wenku8.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Covers the cache-or-network decision behind Phase 1 item 10, which until now could only be
 * exercised by opening a chapter on a device with a deliberately corrupted download in place.
 */
public class ChapterContentLoaderTest {

    private static final String CHAPTER =
            "    堤达穿过那扇门后，就来到异世界的餐厅。  \r\n" +
            "  \r\n" +
            "    「呼……」  ";

    private static final IntConsumer IGNORE_PROGRESS = unused -> {};

    /** Records what the loader asked for, so "did it touch the network at all" is assertable. */
    private static final class Recorder implements ChapterContentLoader.CacheProblemListener {
        final List<ChapterContentLoader.CacheProblem> problems = new ArrayList<>();
        int networkCalls = 0;
        int cacheReads = 0;

        @Override
        public void onUnusableCache(ChapterContentLoader.CacheProblem problem) {
            problems.add(problem);
        }

        ChapterContentLoader.CachedChapterSource cache(String text) {
            return () -> {
                cacheReads++;
                return text;
            };
        }

        ChapterContentLoader.NetworkChapterSource network(String body) {
            return () -> {
                networkCalls++;
                return body == null ? null : body.getBytes(StandardCharsets.UTF_8);
            };
        }
    }

    private static ChapterContentLoader.Result load(
            boolean preferCache, Recorder r, String cached, String networkBody) {
        return ChapterContentLoader.load(
                preferCache, r.cache(cached), r.network(networkBody), IGNORE_PROGRESS, r);
    }

    /** A good download is used as-is, and the network is never contacted. */
    @Test
    public void aUsableDownloadIsServedWithoutTouchingTheNetwork() {
        Recorder r = new Recorder();

        ChapterContentLoader.Result result = load(true, r, CHAPTER, "should not be fetched");

        assertEquals(ChapterContentLoader.Outcome.LOADED_FROM_CACHE, result.outcome);
        assertTrue(result.isLoaded());
        assertFalse(result.content.isEmpty());
        assertEquals(0, r.networkCalls);
        assertTrue(r.problems.isEmpty());
    }

    /**
     * The item 10 regression test. An empty file used to end the read permanently; it must now
     * fall through to the network and report that the download was bad.
     */
    @Test
    public void anEmptyDownloadFallsBackToTheNetworkAndIsReported() {
        Recorder r = new Recorder();

        ChapterContentLoader.Result result = load(true, r, "", CHAPTER);

        assertEquals(ChapterContentLoader.Outcome.LOADED_FROM_NETWORK, result.outcome);
        assertFalse(result.content.isEmpty());
        assertEquals(1, r.networkCalls);
        assertEquals(1, r.problems.size());
        assertEquals(ChapterContentLoader.CacheProblem.MISSING_OR_EMPTY, r.problems.get(0));
    }

    /**
     * A truncated download — bytes present, no content out of the parser — is the other half of
     * item 10, and is reported differently so the two causes stay distinguishable in Crashlytics.
     */
    @Test
    public void anUnparseableDownloadFallsBackToTheNetworkAndIsReportedSeparately() {
        Recorder r = new Recorder();

        ChapterContentLoader.Result result = load(true, r, "   \r\n   ", CHAPTER);

        assertEquals(ChapterContentLoader.Outcome.LOADED_FROM_NETWORK, result.outcome);
        assertEquals(1, r.networkCalls);
        assertEquals(1, r.problems.size());
        assertEquals(ChapterContentLoader.CacheProblem.UNPARSEABLE, r.problems.get(0));
    }

    /** Not opened from the bookshelf: the download is not even consulted. */
    @Test
    public void aFreshReadNeverReadsTheDownloadedCopy() {
        Recorder r = new Recorder();

        ChapterContentLoader.Result result = load(false, r, CHAPTER, CHAPTER);

        assertEquals(ChapterContentLoader.Outcome.LOADED_FROM_NETWORK, result.outcome);
        assertEquals(0, r.cacheReads);
        assertEquals(1, r.networkCalls);
        assertTrue(r.problems.isEmpty());
    }

    /** A failed request is distinct from a server that answered with nothing. */
    @Test
    public void aFailedRequestIsReportedAsNetworkUnavailable() {
        Recorder r = new Recorder();

        ChapterContentLoader.Result result = load(false, r, "", null);

        assertEquals(ChapterContentLoader.Outcome.NETWORK_UNAVAILABLE, result.outcome);
        assertFalse(result.isLoaded());
        assertTrue(result.content.isEmpty());
    }

    /**
     * An empty body and an unparseable one are separated because Wenku8ReaderActivityV1 maps them
     * to different codes — SERVER_RETURN_NOTHING against XML_PARSE_FAILED.
     */
    @Test
    public void anEmptyBodyIsDistinguishedFromAnUnparseableOne() {
        Recorder empty = new Recorder();
        assertEquals(ChapterContentLoader.Outcome.EMPTY_RESPONSE,
                load(false, empty, "", "").outcome);

        Recorder garbage = new Recorder();
        assertEquals(ChapterContentLoader.Outcome.PARSE_FAILED,
                load(false, garbage, "", "   \r\n   ").outcome);
    }

    /** The worst case of item 10: a bad download and no network leaves the reader with nothing. */
    @Test
    public void aBadDownloadWithNoNetworkStillReportsTheCacheProblem() {
        Recorder r = new Recorder();

        ChapterContentLoader.Result result = load(true, r, "", null);

        assertEquals(ChapterContentLoader.Outcome.NETWORK_UNAVAILABLE, result.outcome);
        assertEquals(1, r.networkCalls);
        // Still reported: the bad download is the underlying defect even when the retry fails.
        assertEquals(1, r.problems.size());
        assertEquals(ChapterContentLoader.CacheProblem.MISSING_OR_EMPTY, r.problems.get(0));
    }

    /** Progress is forwarded to the parser, which is how the vertical reader sizes its bar. */
    @Test
    public void progressIsForwardedToTheParser() {
        Recorder r = new Recorder();
        List<Integer> reported = new ArrayList<>();

        ChapterContentLoader.load(
                true, r.cache(CHAPTER), r.network(null), reported::add, r);

        assertFalse("the parser should have reported a size", reported.isEmpty());
    }

    /** The downloaded copy is read exactly once, not re-read on the fallback path. */
    @Test
    public void theDownloadedCopyIsReadOnce() {
        Recorder r = new Recorder();

        load(true, r, "   ", CHAPTER);

        assertEquals(1, r.cacheReads);
    }
}
