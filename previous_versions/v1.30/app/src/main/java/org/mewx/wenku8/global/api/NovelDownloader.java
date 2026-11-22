package org.mewx.wenku8.global.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mewx.wenku8.util.CrashReporter;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Downloads one novel's cached documents and commits them to the device, all or nothing.
 *
 * <p>This ran inline in {@code FavFragment.AsyncLoadAllFromCloud.doInBackground}, in a loop over
 * every novel a sync had to fetch. Three things about that were wrong, and none of them could be
 * tested where they were, because reaching them meant hosting the Fragment and syncing a real
 * account:
 *
 * <ol>
 *     <li>The {@code catch} around a novel's download logged the failure and then fell through to
 *     {@code addToLocalBookshelf(aid)}. A novel whose files never arrived was put on the shelf
 *     anyway, and the next {@code loadAllLocal} could not parse its intro -- which is the
 *     "sync the novel info again" toast, and a row showing the aid where its title should be.</li>
 *
 *     <li>One failure returned {@code NETWORK_ERROR} out of the whole loop, abandoning every
 *     novel after it. On a shelf of seventy that made a single flaky response cost the entire
 *     sync (issue #114).</li>
 *
 *     <li>A fresh three-thread pool was built per novel and shut down only on the paths that
 *     returned normally. Any exception left it running, and its threads are not daemons.</li>
 * </ol>
 *
 * <p>So the contract here is deliberately narrow: <b>{@link Store#commit} is reached only when all
 * three documents have been fetched, parsed and written.</b> Anything else returns
 * {@link Outcome#FAILED} having written at most some files, and the novel is simply not on the
 * shelf -- which is exactly the state that makes the next sync pick it up again, since
 * {@link BookshelfSync#plan} computes what to download as the cloud's list minus the device's.
 * Resumability falls out of the commit rule; there is no separate record of what finished.
 *
 * <p>The executor belongs to the caller, so one pool serves a whole sync and its shutdown sits in
 * a {@code finally} that no outcome here can skip.
 */
public final class NovelDownloader {

    /**
     * Where one novel's sync ended.
     *
     * <p>Deliberately not split by cause. The caller's decision is the same for a timeout, an
     * unparseable response and a failed write -- leave the novel off the shelf and carry on -- and
     * the cause is already recorded through {@link CrashReporter}.
     */
    public enum Outcome {
        /** All three documents are on disk and the novel is on the shelf. */
        COMMITTED,
        /** The user cancelled; the caller should stop the whole sync, not just this novel. */
        CANCELLED,
        /** This novel did not make it. Nothing was committed, so a later sync retries it. */
        FAILED
    }

    /** One of the three documents a novel needs on the device. */
    public enum Document {
        /** The chapter index; the readers rebuild themselves from this after process death. */
        VOLUME_INDEX,
        /** Title, author, status, last update and latest section. */
        META,
        /** The synopsis. */
        FULL_INTRO
    }

    /**
     * Fetches one document.
     *
     * <p>Named by {@link Document} rather than by a prepared request, so that nothing here needs
     * {@code Wenku8API}. That is not only tidiness: building a request reaches
     * {@code TokenManager}, whose static initialiser loads a native library, and a JVM test that
     * touched it would die on {@code UnsatisfiedLinkError} rather than test anything.
     */
    public interface Fetcher {
        @Nullable
        byte[] fetch(@NonNull Document document, int aid);
    }

    /** Where a novel's documents are kept, and how it is published to the bookshelf. */
    public interface Store {
        /** @return whether the file was written */
        boolean write(@NonNull String subFolder, @NonNull String fileName, @NonNull String content);

        /** Publishes the novel. Called only once every document above is safely written. */
        void commit(int aid);
    }

    /** Lets a sync notice that the user cancelled it, between and during a novel's requests. */
    public interface Cancellation {
        boolean isCancelled();
    }

    /** Save subfolder holding all three documents. */
    public static final String SUB_FOLDER = "intro";

    private NovelDownloader() {
    }

    /** Must match {@code GlobalConfig.getVolumeIndexFileName}; the readers rebuild from this. */
    @NonNull
    public static String volumeFileName(int aid) {
        return aid + "-volume.xml";
    }

    @NonNull
    public static String fullIntroFileName(int aid) {
        return aid + "-introfull.xml";
    }

    /**
     * The file {@code FavFragment.loadAllLocal} probes to decide whether a novel is on the device,
     * which is why it is written last -- see {@link #syncOne}.
     */
    @NonNull
    public static String introFileName(int aid) {
        return aid + "-intro.xml";
    }

    /**
     * Fetches, parses and stores one novel.
     *
     * <p>The volume index and the metadata are requested together; the full intro follows, as it
     * did before. Writes go out content-first and finish with {@link #introFileName}, so a process
     * killed mid-novel leaves a set that {@code loadAllLocal} reads as absent rather than as
     * present-but-broken.
     *
     * @param executor owned by the caller, and shared across a whole sync
     */
    @NonNull
    public static Outcome syncOne(int aid,
                                  @NonNull ExecutorService executor,
                                  @NonNull Fetcher fetcher,
                                  @NonNull Store store,
                                  @NonNull Cancellation cancellation) {
        if (cancellation.isCancelled()) {
            return Outcome.CANCELLED;
        }

        try {
            final Callable<byte[]> volumeTask = () -> cancellation.isCancelled() ? null
                    : fetcher.fetch(Document.VOLUME_INDEX, aid);
            final Callable<byte[]> introTask = () -> cancellation.isCancelled() ? null
                    : fetcher.fetch(Document.META, aid);

            final Future<byte[]> volumeFuture = executor.submit(volumeTask);
            final Future<byte[]> introFuture = executor.submit(introTask);
            final byte[] rawVolume = volumeFuture.get();
            final byte[] rawIntro = introFuture.get();

            if (cancellation.isCancelled()) {
                return Outcome.CANCELLED;
            }
            if (rawVolume == null || rawIntro == null) {
                return Outcome.FAILED;
            }

            final String volumeXml = new String(rawVolume, "UTF-8");
            final String introXml = new String(rawIntro, "UTF-8");
            final List<VolumeList> volumes = Wenku8Parser.getVolumeList(volumeXml);
            final NovelItemMeta meta = Wenku8Parser.parseNovelFullMeta(introXml);
            if (volumes.isEmpty() || meta == null) {
                return Outcome.FAILED;
            }

            if (cancellation.isCancelled()) {
                return Outcome.CANCELLED;
            }
            final byte[] rawFullIntro = executor.submit(() -> cancellation.isCancelled() ? null
                    : fetcher.fetch(Document.FULL_INTRO, aid)).get();
            if (rawFullIntro == null) {
                return cancellation.isCancelled() ? Outcome.CANCELLED : Outcome.FAILED;
            }
            final String fullIntro = new String(rawFullIntro, "UTF-8");

            // Content first, the probe file last. A failed write stops the commit, so a novel is
            // never published with a document missing.
            if (!store.write(SUB_FOLDER, volumeFileName(aid), volumeXml)
                    || !store.write(SUB_FOLDER, fullIntroFileName(aid), fullIntro)
                    || !store.write(SUB_FOLDER, introFileName(aid), introXml)) {
                return Outcome.FAILED;
            }

            store.commit(aid);
            return Outcome.COMMITTED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.CANCELLED;
        } catch (Exception e) {
            // Reached by a failed request, an unparseable response or a write error alike. The
            // point is what does not happen next: commit() is not called, so the novel stays off
            // the shelf and the next sync fetches it again.
            CrashReporter.recordException("NovelDownloader.syncOne", e);
            return Outcome.FAILED;
        }
    }
}
