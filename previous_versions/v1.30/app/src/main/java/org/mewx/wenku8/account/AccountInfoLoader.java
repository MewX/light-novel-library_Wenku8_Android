package org.mewx.wenku8.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mewx.wenku8.api.Wenku8Error;
import org.mewx.wenku8.global.api.UserInfo;
import org.mewx.wenku8.util.LightTool;

import java.nio.charset.StandardCharsets;

/**
 * Decides what the account screen ends up showing: the signed-in user's details, or which failure
 * to report instead.
 *
 * <p>This lived inside {@code UserInfoActivity.AsyncGetUserInfo.doInBackground} and was reachable
 * only by opening the account screen on a device with a session, a server and a network. It is the
 * densest untested branch in the app — an optional daily sign-in, a fetch, a re-login-and-retry
 * when the server says the session lapsed, and four distinct ways of failing that the screen maps
 * to different outcomes — and one of those branches silently logs the user out for the rest of the
 * session if it gets the order wrong.
 *
 * <p>Follows {@code ChapterContentLoader}: the I/O is injected, so the decision can be tested
 * without a network. The result carries a {@code Wenku8Error.ErrorCode} rather than a neutral
 * outcome because there is exactly one caller and it already switches on those codes; inventing a
 * parallel vocabulary would only add a mapping to get wrong.
 *
 * <p><b>Why {@link Backend#serverCode(int)} is injected rather than called directly.</b> Turning a
 * response integer into an {@code ErrorCode} is {@code Wenku8Error.getSystemDefinedErrorCode},
 * which throws on {@code api-stub} — correctly. That mapping is wire-protocol knowledge belonging
 * to the private {@code api/} module, and reproducing it in the public stub would publish the very
 * thing the stub exists to keep out. Taking it as a dependency lets this class be tested on either
 * configuration without either module needing to know about the tests.
 */
public final class AccountInfoLoader {

    private AccountInfoLoader() {
    }

    /**
     * Everything this decision needs from the outside world. One interface rather than four
     * single-method ones because the caller implements it once and a test fakes it once; the
     * methods are not independently useful.
     */
    public interface Backend {
        /** Sends the daily sign-in. {@code null} when the request could not be completed. */
        @Nullable
        byte[] sendSignRequest();

        /** Requests the user's details. {@code null} when the request could not be completed. */
        @Nullable
        byte[] sendInfoRequest();

        /** Re-authenticates from stored credentials after the server reports a lapsed session. */
        @NonNull
        Wenku8Error.ErrorCode restoreSession();

        /** Fetches the avatar image bytes. {@code null} when there is no image to be had. */
        @Nullable
        byte[] downloadAvatar(int uid);

        /** Maps a numeric server response to its meaning. See the class comment. */
        @NonNull
        Wenku8Error.ErrorCode serverCode(int raw);
    }

    /**
     * What the load produced. {@link #userInfo} is non-null only when {@link #code} is
     * {@code SYSTEM_1_SUCCEEDED}; {@link #avatar} may be null even then, since an account can
     * simply have no picture.
     */
    public static final class Result {
        @NonNull
        public final Wenku8Error.ErrorCode code;
        @Nullable
        public final UserInfo userInfo;
        @Nullable
        public final byte[] avatar;

        private Result(@NonNull Wenku8Error.ErrorCode code, @Nullable UserInfo userInfo,
                       @Nullable byte[] avatar) {
            this.code = code;
            this.userInfo = userInfo;
            this.avatar = avatar;
        }

        public boolean isLoaded() {
            return code == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED && userInfo != null;
        }
    }

    private static Result failure(@NonNull Wenku8Error.ErrorCode code) {
        return new Result(code, null, null);
    }

    /**
     * @param signIn  whether to send the daily sign-in before fetching. A failed sign-in stops the
     *                load, because the screen reports it and has nothing further to say.
     * @param backend the I/O this decision depends on
     */
    @NonNull
    public static Result load(boolean signIn, @NonNull Backend backend) {
        if (signIn) {
            final Result signFailure = attemptSignIn(backend);
            if (signFailure != null) {
                return signFailure;
            }
        }

        String xml = fetchInfoBody(backend);
        if (xml == null) {
            return failure(Wenku8Error.ErrorCode.NETWORK_ERROR);
        }

        // A numeric body is a status, not user details. The lapsed-session case is the only one
        // worth retrying, and it is retried exactly once: doing it in a loop would turn credentials
        // the server keeps rejecting into an unbounded run of login attempts.
        if (LightTool.isInteger(xml)) {
            final Wenku8Error.ErrorCode status = backend.serverCode(Integer.parseInt(xml));
            if (status != Wenku8Error.ErrorCode.SYSTEM_4_NOT_LOGGED_IN) {
                return failure(status);
            }

            final Wenku8Error.ErrorCode restored = backend.restoreSession();
            if (restored != Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                return failure(restored);
            }

            xml = fetchInfoBody(backend);
            if (xml == null) {
                return failure(Wenku8Error.ErrorCode.NETWORK_ERROR);
            }
        }

        final UserInfo userInfo = UserInfo.parseUserInfo(xml);
        if (userInfo == null) {
            return failure(Wenku8Error.ErrorCode.XML_PARSE_FAILED);
        }

        return new Result(Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED, userInfo,
                emptyToNull(backend.downloadAvatar(userInfo.uid)));
    }

    /** @return the failure to report, or {@code null} when the sign-in did not stop the load. */
    @Nullable
    private static Result attemptSignIn(@NonNull Backend backend) {
        final byte[] response = backend.sendSignRequest();
        if (response == null) {
            return failure(Wenku8Error.ErrorCode.NETWORK_ERROR);
        }

        final String body = new String(response, StandardCharsets.UTF_8);
        if (!LightTool.isInteger(body)) {
            return failure(Wenku8Error.ErrorCode.STRING_CONVERSION_ERROR);
        }

        final Wenku8Error.ErrorCode status = backend.serverCode(Integer.parseInt(body));
        // Only an outright refusal stops the load. Any other status still leaves the details
        // worth fetching -- signing in twice in a day is not a reason to show an empty screen.
        return status == Wenku8Error.ErrorCode.SYSTEM_9_SIGN_FAILED ? failure(status) : null;
    }

    @Nullable
    private static String fetchInfoBody(@NonNull Backend backend) {
        final byte[] response = backend.sendInfoRequest();
        return response == null ? null : new String(response, StandardCharsets.UTF_8);
    }

    /**
     * A zero-length body is not a picture. The old code checked this at the point of decoding;
     * keeping it here means the caller never has to ask whether an empty array is meaningful.
     */
    @Nullable
    private static byte[] emptyToNull(@Nullable byte[] bytes) {
        return bytes == null || bytes.length == 0 ? null : bytes;
    }
}
