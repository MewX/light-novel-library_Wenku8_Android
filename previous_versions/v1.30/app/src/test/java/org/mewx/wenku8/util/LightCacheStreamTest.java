package org.mewx.wenku8.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * JVM coverage for {@link LightCache#loadStream(InputStream)}.
 * <p>
 * This is the read that silently truncated novel XML: it sized a single {@code read()} from
 * {@code available()}, which is an estimate of what can be read without blocking rather than
 * the length of the stream. The failure never surfaced as an I/O error -- it surfaced as a
 * parse error or a blank chapter much further downstream -- so the tests here work in terms
 * of streams that under-report {@code available()} or return short reads, which is what a
 * buffered or compressed source does in practice.
 * <p>
 * Deliberately plain JUnit rather than Robolectric: nothing on this path touches the Android
 * framework, and the instrumented {@code LightCacheTest} in androidTest still covers the
 * storage-path methods that do.
 */
public class LightCacheStreamTest {

    /** A stream that lies about available() and never fills a buffer in one read(). */
    private static class DripFeedStream extends InputStream {
        private final byte[] data;
        private final int bytesPerRead;
        private final int availableToReport;
        private int pos = 0;
        private boolean closed = false;

        DripFeedStream(byte[] data, int bytesPerRead, int availableToReport) {
            this.data = data;
            this.bytesPerRead = bytesPerRead;
            this.availableToReport = availableToReport;
        }

        @Override
        public int available() {
            return availableToReport;
        }

        @Override
        public int read() {
            return pos >= data.length ? -1 : data[pos++] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (pos >= data.length) return -1;
            int count = Math.min(Math.min(bytesPerRead, len), data.length - pos);
            System.arraycopy(data, pos, b, off, count);
            pos += count;
            return count;
        }

        @Override
        public void close() {
            closed = true;
        }

        boolean isClosed() {
            return closed;
        }
    }

    private static byte[] bytesOfLength(int length) {
        byte[] bs = new byte[length];
        for (int i = 0; i < length; i++) {
            bs[i] = (byte) (i % 251); // 251 is prime, so the pattern does not align to any buffer
        }
        return bs;
    }

    @Test
    public void readsTheWholeStreamWhenAvailableUnderReports() {
        // The regression this method existed to cause: available() promises 10 bytes, the
        // stream actually holds 100k. The old single-read implementation returned 10 bytes.
        byte[] content = bytesOfLength(100_000);
        DripFeedStream stream = new DripFeedStream(content, 4096, 10);

        byte[] result = LightCache.loadStream(stream);

        assertArrayEquals(content, result);
    }

    @Test
    public void readsTheWholeStreamWhenAvailableOverReports() {
        // The other half of the same bug: a buffer sized from an over-reported available()
        // used to come back zero-padded to that length rather than trimmed to the content.
        byte[] content = bytesOfLength(1024);
        DripFeedStream stream = new DripFeedStream(content, 64, 8192);

        byte[] result = LightCache.loadStream(stream);

        assertArrayEquals(content, result);
    }

    @Test
    public void readsAcrossManyShortReads() {
        byte[] content = bytesOfLength(5000);
        DripFeedStream stream = new DripFeedStream(content, 1, content.length);

        assertArrayEquals(content, LightCache.loadStream(stream));
    }

    @Test
    public void readsAStreamLargerThanTheInternalBuffer() {
        // Spans several fills of the 8k chunk, including a final partial one.
        byte[] content = bytesOfLength(8192 * 3 + 17);
        byte[] result = LightCache.loadStream(new ByteArrayInputStream(content));

        assertArrayEquals(content, result);
    }

    @Test
    public void preservesContentExactlyForRealNovelXml() {
        // Characterization: what callers actually feed this, via GlobalConfig.
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<package><volume vid=\"1\" 名称=\"第一卷\">章节</volume></package>";
        byte[] content = xml.getBytes(StandardCharsets.UTF_8);

        byte[] result = LightCache.loadStream(new DripFeedStream(content, 7, 3));

        assertEquals(xml, new String(result, StandardCharsets.UTF_8));
    }

    @Test
    public void returnsAnEmptyArrayForAnEmptyStream() {
        // A deliberate behaviour change, and the only one in this rewrite. The old code sized
        // its buffer at 0, and read(b, 0, 0) on an exhausted stream returns -1 rather than 0,
        // so an empty file came back as null -- indistinguishable from a read failure. Every
        // caller reaches loadFile() through testFileExist(), which deletes empty files and
        // returns false, so no call site can currently observe the difference; the new
        // behaviour is what loadFile()'s own contract already documents ("can be empty") and
        // it makes a null return mean "could not read" and nothing else.
        byte[] result = LightCache.loadStream(new ByteArrayInputStream(new byte[0]));

        assertEquals(0, result.length);
    }

    @Test
    public void closesTheStreamOnSuccess() {
        DripFeedStream stream = new DripFeedStream(bytesOfLength(64), 8, 64);

        LightCache.loadStream(stream);

        assertTrue(stream.isClosed());
    }

    @Test
    public void returnsNullAndClosesTheStreamOnIoError() {
        // A failure part-way through must not hand back the partial content: a truncated
        // chapter is exactly the silent corruption this method used to produce.
        final boolean[] closed = {false};
        InputStream failing = new InputStream() {
            private int reads = 0;

            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (reads++ == 0) {
                    Arrays.fill(b, off, off + Math.min(len, 16), (byte) 'a');
                    return Math.min(len, 16);
                }
                throw new IOException("boom");
            }

            @Override
            public void close() {
                closed[0] = true;
            }
        };

        assertNull(LightCache.loadStream(failing));
        assertTrue(closed[0]);
    }
}
