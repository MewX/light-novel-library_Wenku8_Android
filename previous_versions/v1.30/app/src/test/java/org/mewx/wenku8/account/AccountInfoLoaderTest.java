package org.mewx.wenku8.account;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mewx.wenku8.api.Wenku8Error;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The account screen's load decision, which until now could only be exercised by opening that
 * screen on a device that had a session, a server and a network — and whose interesting branches
 * (a lapsed session, a refused sign-in, a server answering with a bare status code) could not be
 * produced on demand at all.
 *
 * <p>Robolectric because {@code UserInfo.parseUserInfo} uses {@code XmlPullParserFactory}, which
 * returns null under the plain android.jar stub. See the note in {@code app/build.gradle}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class AccountInfoLoaderTest {

    private static final String USER_INFO_XML = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<metadata>\n"
            + "<item name=\"uname\"><![CDATA[apptest]]></item>\n"
            + "<item name=\"nickname\"><![CDATA[apptest nick]]></item>\n"
            + "<item name=\"uid\">4321</item>\n"
            + "<item name=\"score\">100</item>\n"
            + "<item name=\"experience\">10</item>\n"
            + "<item name=\"rank\"><![CDATA[新手上路]]></item>\n"
            + "</metadata>";

    private static final byte[] AVATAR = {(byte) 0x89, 'P', 'N', 'G'};

    /**
     * Records every call, so "did it stop before touching the network" and "did it retry more than
     * once" are directly assertable rather than inferred from the result.
     */
    private static final class FakeBackend implements AccountInfoLoader.Backend {
        final List<String> calls = new ArrayList<>();

        /** Consumed in order; the second entry is what a retry receives. */
        final List<byte[]> infoResponses = new ArrayList<>();
        byte[] signResponse = body("1");
        Wenku8Error.ErrorCode restoreResult = Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
        byte[] avatar = AVATAR;
        int infoRequests = 0;

        @Nullable
        @Override
        public byte[] sendSignRequest() {
            calls.add("sign");
            return signResponse;
        }

        @Nullable
        @Override
        public byte[] sendInfoRequest() {
            calls.add("info");
            final int index = infoRequests++;
            return index < infoResponses.size() ? infoResponses.get(index) : null;
        }

        @NonNull
        @Override
        public Wenku8Error.ErrorCode restoreSession() {
            calls.add("restore");
            return restoreResult;
        }

        @Nullable
        @Override
        public byte[] downloadAvatar(int uid) {
            calls.add("avatar:" + uid);
            return avatar;
        }

        @NonNull
        @Override
        public Wenku8Error.ErrorCode serverCode(int raw) {
            // Deliberately a test-local mapping. The real one lives in the private api/ module and
            // is exactly what this seam exists to avoid depending on.
            switch (raw) {
                case 1:
                    return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
                case 4:
                    return Wenku8Error.ErrorCode.SYSTEM_4_NOT_LOGGED_IN;
                case 9:
                    return Wenku8Error.ErrorCode.SYSTEM_9_SIGN_FAILED;
                default:
                    return Wenku8Error.ErrorCode.ERROR_DEFAULT;
            }
        }
    }

    private static byte[] body(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** A backend that answers the first info request with the given body. */
    private static FakeBackend backendReturning(byte[]... infoResponses) {
        final FakeBackend backend = new FakeBackend();
        for (byte[] response : infoResponses) {
            backend.infoResponses.add(response);
        }
        return backend;
    }

    @Test
    public void aSuccessfulLoadReturnsTheParsedUserAndTheAvatar() {
        final FakeBackend backend = backendReturning(body(USER_INFO_XML));

        final AccountInfoLoader.Result result = AccountInfoLoader.load(false, backend);

        assertTrue(result.isLoaded());
        assertEquals(Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED, result.code);
        assertNotNull(result.userInfo);
        assertEquals("apptest", result.userInfo.username);
        assertEquals(4321, result.userInfo.uid);
        assertEquals(100, result.userInfo.score);
        assertArrayEquals(AVATAR, result.avatar);
        assertEquals("the avatar must be fetched for the user that was actually parsed",
                List.of("info", "avatar:4321"), backend.calls);
    }

    @Test
    public void aLoadWithoutSigningInNeverSendsTheSignRequest() {
        final FakeBackend backend = backendReturning(body(USER_INFO_XML));

        AccountInfoLoader.load(false, backend);

        assertFalse("the daily sign-in was sent when nobody asked for it",
                backend.calls.contains("sign"));
    }

    @Test
    public void anUnreachableServerIsReportedAsANetworkError() {
        final FakeBackend backend = backendReturning(); // no response at all

        final AccountInfoLoader.Result result = AccountInfoLoader.load(false, backend);

        assertEquals(Wenku8Error.ErrorCode.NETWORK_ERROR, result.code);
        assertFalse(result.isLoaded());
        assertNull(result.userInfo);
    }

    /**
     * A bare status code that is not the lapsed-session one. It is reported as-is, and nothing is
     * retried — the old code's {@code else if} branch.
     */
    @Test
    public void aStatusCodeBodyIsReportedWithoutRetrying() {
        final FakeBackend backend = backendReturning(body("7"));

        final AccountInfoLoader.Result result = AccountInfoLoader.load(false, backend);

        assertEquals(Wenku8Error.ErrorCode.ERROR_DEFAULT, result.code);
        assertEquals("a status the screen cannot act on must not trigger a re-login",
                List.of("info"), backend.calls);
    }

    /** The branch worth having: the server says the session lapsed, so log back in and retry. */
    @Test
    public void aLapsedSessionIsRestoredAndTheFetchRetriedOnce() {
        final FakeBackend backend = backendReturning(body("4"), body(USER_INFO_XML));

        final AccountInfoLoader.Result result = AccountInfoLoader.load(false, backend);

        assertTrue(result.isLoaded());
        assertNotNull(result.userInfo);
        assertEquals("apptest", result.userInfo.username);
        assertEquals(List.of("info", "restore", "info", "avatar:4321"), backend.calls);
    }

    @Test
    public void aFailedReLoginIsReportedAndTheFetchIsNotRetried() {
        final FakeBackend backend = backendReturning(body("4"), body(USER_INFO_XML));
        backend.restoreResult = Wenku8Error.ErrorCode.SYSTEM_3_ERROR_PASSWORD;

        final AccountInfoLoader.Result result = AccountInfoLoader.load(false, backend);

        assertEquals("the reason the re-login failed is what the user needs to see",
                Wenku8Error.ErrorCode.SYSTEM_3_ERROR_PASSWORD, result.code);
        assertEquals(List.of("info", "restore"), backend.calls);
    }

    /**
     * The retry is bounded at one. A server that keeps answering "not logged in" must not turn
     * into an unbounded run of login attempts against someone else's service.
     */
    @Test
    public void aServerThatKeepsRejectingTheSessionIsNotRetriedForever() {
        final FakeBackend backend = backendReturning(body("4"), body("4"));

        final AccountInfoLoader.Result result = AccountInfoLoader.load(false, backend);

        assertFalse(result.isLoaded());
        assertEquals("exactly one re-login attempt", 1,
                backend.calls.stream().filter("restore"::equals).count());
        assertEquals("exactly two fetches", 2,
                backend.calls.stream().filter("info"::equals).count());
    }

    @Test
    public void aRetryThatCannotReachTheServerIsReportedAsANetworkError() {
        final FakeBackend backend = backendReturning(body("4")); // nothing for the retry

        final AccountInfoLoader.Result result = AccountInfoLoader.load(false, backend);

        assertEquals(Wenku8Error.ErrorCode.NETWORK_ERROR, result.code);
        assertEquals(List.of("info", "restore", "info"), backend.calls);
    }

    /**
     * Well-formed XML that is not a user-info response — a captive portal or a maintenance page.
     * {@code UserInfo.parseUserInfo} returns null for these, and the screen must say so rather
     * than show a blank account.
     */
    @Test
    public void aBodyThatIsNotUserDetailsIsAParseFailure() {
        final FakeBackend backend = backendReturning(body("<html><body>maintenance</body></html>"));

        final AccountInfoLoader.Result result = AccountInfoLoader.load(false, backend);

        assertEquals(Wenku8Error.ErrorCode.XML_PARSE_FAILED, result.code);
        assertNull(result.userInfo);
        assertFalse("no avatar should be fetched for a user that did not parse",
                backend.calls.contains("avatar:0"));
    }

    @Test
    public void anAccountWithNoPictureLoadsWithANullAvatar() {
        final FakeBackend backend = backendReturning(body(USER_INFO_XML));
        backend.avatar = new byte[0];

        final AccountInfoLoader.Result result = AccountInfoLoader.load(false, backend);

        assertTrue(result.isLoaded());
        assertNull("an empty body is not a picture", result.avatar);
    }

    @Test
    public void aRefusedSignInStopsTheLoadBeforeFetching() {
        final FakeBackend backend = backendReturning(body(USER_INFO_XML));
        backend.signResponse = body("9");

        final AccountInfoLoader.Result result = AccountInfoLoader.load(true, backend);

        assertEquals(Wenku8Error.ErrorCode.SYSTEM_9_SIGN_FAILED, result.code);
        assertEquals("a refused sign-in has nothing more to say, so it must not fetch",
                List.of("sign"), backend.calls);
    }

    @Test
    public void aSuccessfulSignInStillFetchesTheDetails() {
        final FakeBackend backend = backendReturning(body(USER_INFO_XML));
        backend.signResponse = body("1");

        final AccountInfoLoader.Result result = AccountInfoLoader.load(true, backend);

        assertTrue(result.isLoaded());
        assertEquals(List.of("sign", "info", "avatar:4321"), backend.calls);
    }

    /**
     * Any sign status other than an outright refusal leaves the details worth showing. Signing in
     * twice in one day is not a reason to present an empty screen.
     */
    @Test
    public void aSignInStatusThatIsNotARefusalDoesNotStopTheLoad() {
        final FakeBackend backend = backendReturning(body(USER_INFO_XML));
        backend.signResponse = body("7");

        final AccountInfoLoader.Result result = AccountInfoLoader.load(true, backend);

        assertTrue(result.isLoaded());
        assertEquals(List.of("sign", "info", "avatar:4321"), backend.calls);
    }

    @Test
    public void aSignRequestThatCannotReachTheServerStopsTheLoad() {
        final FakeBackend backend = backendReturning(body(USER_INFO_XML));
        backend.signResponse = null;

        final AccountInfoLoader.Result result = AccountInfoLoader.load(true, backend);

        assertEquals(Wenku8Error.ErrorCode.NETWORK_ERROR, result.code);
        assertEquals(List.of("sign"), backend.calls);
    }

    @Test
    public void aSignResponseThatIsNotANumberIsAConversionError() {
        final FakeBackend backend = backendReturning(body(USER_INFO_XML));
        backend.signResponse = body("<html>not a status</html>");

        final AccountInfoLoader.Result result = AccountInfoLoader.load(true, backend);

        assertEquals(Wenku8Error.ErrorCode.STRING_CONVERSION_ERROR, result.code);
        assertEquals(List.of("sign"), backend.calls);
    }
}
