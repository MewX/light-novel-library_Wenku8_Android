package org.mewx.wenku8;

import org.junit.Assume;
import org.mewx.wenku8.api.Wenku8API;
import org.mewx.wenku8.global.GlobalConfig;

/**
 * Skips a test that cannot run against {@code api-stub}.
 *
 * <p>{@code settings.gradle} swaps the {@code :api} module for {@code api-stub/} whenever the
 * private submodule is absent, which is always the case on CI. Most stub methods do not return an
 * inert value — they {@code throw new UnsupportedOperationException("stub")}. A screen that calls
 * one during startup therefore dies, and because those calls sit inside {@code doInBackground} the
 * exception takes the whole process with it rather than failing a single assertion.
 *
 * <p>That makes a class of test pass locally and fail on CI with a stack trace pointing at the
 * app rather than at the configuration — the trap described under "Device setup traps" in
 * STABILITY_PLAN.md. It has now caught two commits' worth of tests
 * ({@code FavFragmentHostingTest}, {@code NovelReviewScreensTest}), which is what this exists to
 * stop happening a third time.
 *
 * <p><b>Skipping rather than failing is the correct outcome, and worth defending.</b> These tests
 * assert that a screen survives a failed network fetch. Against the stub there is no fetch to
 * fail: the call throws before any request is attempted, so the screen never reaches the state
 * under test and there is nothing meaningful left to assert. A test that cannot observe its
 * subject should say so, not go red as though the subject were broken.
 *
 * <p>The cost is real and should be named: these screens are therefore <b>not</b> covered on CI,
 * only on a developer machine holding the private module. The coverage figure Coveralls publishes
 * is measured on CI and so does not include them.
 */
public final class RealApi {

    private RealApi() {
    }

    /**
     * Skips the calling test unless the real API implementation is present.
     *
     * <p>Probed by calling a stub method and watching for its exception rather than by inspecting
     * the build, because the build is what the swap already happened in — the running code is the
     * only thing that knows which one it got.
     */
    public static void require() {
        boolean real = true;
        try {
            Wenku8API.getBookshelfListAid(GlobalConfig.getCurrentLang());
        } catch (UnsupportedOperationException stubbed) {
            real = false;
        } catch (RuntimeException other) {
            // Anything else means a real implementation ran and disliked the arguments, which is
            // still a real implementation. Only the stub's own signal counts as absent.
            real = true;
        }

        Assume.assumeTrue(
                "needs the private api/ module; this build uses api-stub, whose methods throw "
                        + "before any network call is attempted, so the screen never reaches the "
                        + "failed-fetch state these tests are about",
                real);
    }
}
