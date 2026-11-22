package org.mewx.wenku8.global.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The rule these all circle: a novel reaches the bookshelf only when every one of its documents
 * is on disk. Before {@link NovelDownloader} existed this logic sat inside an AsyncTask in
 * FavFragment, where exercising it meant hosting the Fragment and syncing a real account -- so the
 * code deciding whether a reader's novels survive was the code that could not be tested.
 *
 * <p>Robolectric because the parsers use XmlPullParser, which is a stub returning null under the
 * plain JVM runtime.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class NovelDownloaderTest {

    private static final int AID = 1306;

    /** Attribute order matters: both parsers read positionally, by getAttributeValue(0) and (1). */
    private static final String VOLUME_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<package>\n"
                    + "<volume vid=\"41748\"><![CDATA[the first volume]]>\n"
                    + "<chapter cid=\"41749\"><![CDATA[a chapter]]></chapter>\n"
                    + "</volume>\n"
                    + "</package>";

    private static final String META_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<metadata>\n"
                    + "<data name=\"Title\" aid=\"1306\"><![CDATA[a novel]]></data>\n"
                    + "<data name=\"Author\" value=\"an author\"/>\n"
                    + "<data name=\"BookStatus\" value=\"finished\"/>\n"
                    + "<data name=\"LastUpdate\" value=\"2026-08-29\"/>\n"
                    + "<data name=\"LatestSection\" cid=\"41897\"><![CDATA[the latest]]></data>\n"
                    + "</metadata>";

    private static final String FULL_INTRO = "a synopsis";

    private ExecutorService executor;

    @Before
    public void setUp() {
        executor = Executors.newFixedThreadPool(3);
    }

    @After
    public void tearDown() {
        executor.shutdownNow();
    }

    // ---- fakes -------------------------------------------------------------------------------

    private static byte[] utf8(String s) {
        return s.getBytes(Charset.forName("UTF-8"));
    }

    /** Serves canned documents, and can be told to fail or throw for one of them. */
    private static class ScriptedFetcher implements NovelDownloader.Fetcher {
        final Map<NovelDownloader.Document, byte[]> responses =
                new EnumMap<>(NovelDownloader.Document.class);
        NovelDownloader.Document throwOn;

        ScriptedFetcher() {
            responses.put(NovelDownloader.Document.VOLUME_INDEX, utf8(VOLUME_XML));
            responses.put(NovelDownloader.Document.META, utf8(META_XML));
            responses.put(NovelDownloader.Document.FULL_INTRO, utf8(FULL_INTRO));
        }

        @Nullable
        @Override
        public byte[] fetch(@NonNull NovelDownloader.Document document, int aid) {
            if (document == throwOn) {
                throw new IllegalStateException("the network blew up");
            }
            return responses.get(document);
        }
    }

    /** Records what was written, in order, and what was committed. */
    private static class RecordingStore implements NovelDownloader.Store {
        final List<String> written = new ArrayList<>();
        final List<Integer> committed = new ArrayList<>();
        String failWritingFile;

        @Override
        public boolean write(@NonNull String subFolder, @NonNull String fileName,
                             @NonNull String content) {
            if (fileName.equals(failWritingFile)) {
                return false;
            }
            written.add(fileName);
            return true;
        }

        @Override
        public void commit(int aid) {
            committed.add(aid);
        }
    }

    private static final NovelDownloader.Cancellation RUNNING = () -> false;
    private static final NovelDownloader.Cancellation CANCELLED = () -> true;

    private NovelDownloader.Outcome run(ScriptedFetcher fetcher, RecordingStore store,
                                        NovelDownloader.Cancellation cancellation) {
        return NovelDownloader.syncOne(AID, executor, fetcher, store, cancellation);
    }

    // ---- the happy path ----------------------------------------------------------------------

    @Test
    public void everyDocumentArrivingCommitsTheNovel() {
        RecordingStore store = new RecordingStore();

        assertEquals(NovelDownloader.Outcome.COMMITTED,
                run(new ScriptedFetcher(), store, RUNNING));
        assertEquals(3, store.written.size());
        assertEquals("the novel is published exactly once",
                java.util.Collections.singletonList(AID), store.committed);
    }

    /**
     * loadAllLocal decides a novel is on the device by reading this file, so it has to land after
     * the other two. A process killed mid-novel then leaves a set that reads as absent, which is
     * a state the bookshelf already handles, rather than as present-but-incomplete.
     */
    @Test
    public void theFileTheBookshelfProbesIsWrittenLast() {
        RecordingStore store = new RecordingStore();

        run(new ScriptedFetcher(), store, RUNNING);

        assertEquals(NovelDownloader.introFileName(AID),
                store.written.get(store.written.size() - 1));
    }

    // ---- nothing partial is ever published ---------------------------------------------------

    @Test
    public void aRequestComingBackEmptyLeavesTheNovelOffTheShelf() {
        ScriptedFetcher fetcher = new ScriptedFetcher();
        fetcher.responses.put(NovelDownloader.Document.FULL_INTRO, null);
        RecordingStore store = new RecordingStore();

        assertEquals(NovelDownloader.Outcome.FAILED, run(fetcher, store, RUNNING));
        assertTrue("a novel with a document missing must not be published",
                store.committed.isEmpty());
    }

    @Test
    public void anUnparseableMetaLeavesTheNovelOffTheShelf() {
        ScriptedFetcher fetcher = new ScriptedFetcher();
        fetcher.responses.put(NovelDownloader.Document.META, utf8("<metadata></metadata>"));
        RecordingStore store = new RecordingStore();

        assertEquals(NovelDownloader.Outcome.FAILED, run(fetcher, store, RUNNING));
        assertTrue(store.committed.isEmpty());
    }

    @Test
    public void anEmptyVolumeIndexLeavesTheNovelOffTheShelf() {
        ScriptedFetcher fetcher = new ScriptedFetcher();
        fetcher.responses.put(NovelDownloader.Document.VOLUME_INDEX, utf8("<package></package>"));
        RecordingStore store = new RecordingStore();

        assertEquals(NovelDownloader.Outcome.FAILED, run(fetcher, store, RUNNING));
        assertTrue(store.committed.isEmpty());
    }

    @Test
    public void aFailedWriteStopsTheCommit() {
        RecordingStore store = new RecordingStore();
        store.failWritingFile = NovelDownloader.fullIntroFileName(AID);

        assertEquals(NovelDownloader.Outcome.FAILED,
                run(new ScriptedFetcher(), store, RUNNING));
        assertTrue(store.committed.isEmpty());
        assertFalse("the probe file must not be written once a sibling failed",
                store.written.contains(NovelDownloader.introFileName(AID)));
    }

    /**
     * The bug this replaces: the catch in FavFragment logged the exception and then fell through
     * to addToLocalBookshelf, so a novel whose download threw was shelved anyway and came back as
     * an unreadable row.
     */
    @Test
    public void aThrowingFetcherIsContainedAndCommitsNothing() {
        ScriptedFetcher fetcher = new ScriptedFetcher();
        fetcher.throwOn = NovelDownloader.Document.META;
        RecordingStore store = new RecordingStore();

        assertEquals(NovelDownloader.Outcome.FAILED, run(fetcher, store, RUNNING));
        assertTrue(store.committed.isEmpty());
    }

    // ---- cancellation ------------------------------------------------------------------------

    @Test
    public void cancellingBeforeTheFirstRequestDoesNothingAtAll() {
        RecordingStore store = new RecordingStore();

        assertEquals(NovelDownloader.Outcome.CANCELLED,
                run(new ScriptedFetcher(), store, CANCELLED));
        assertTrue(store.written.isEmpty());
        assertTrue(store.committed.isEmpty());
    }
}
