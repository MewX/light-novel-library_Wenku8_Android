package org.mewx.wenku8.global;

/**
 * Which of the two list screens the user is currently on.
 *
 * <p>Moved out of {@code GlobalConfig} as step 5 of the sequencing in {@code STABILITY_PLAN.md}.
 * It had nothing to do with configuration: these are transient flags describing what is on screen
 * right now, and they sat beside the credential and storage handling purely because
 * {@code GlobalConfig} was where everything global ended up.
 *
 * <p><b>Still global mutable state, and deliberately so for now.</b> Moving it does not fix it —
 * {@code NovelItemAdapterUpdate} genuinely needs to know whether it is rendering inside the
 * bookshelf, and today that is communicated through a static rather than passed in. The honest fix
 * is to pass the mode to the adapter at construction, which is a change to three screens and is not
 * what this step is for. Naming it for what it is makes that fix easier to find later.
 *
 * <p>Written from {@code onResume}/{@code onPause} of {@code FavFragment} and
 * {@code LatestFragment}, and read by {@code NovelItemAdapterUpdate} to decide whether a row may
 * refresh itself in place.
 */
public final class ScreenState {

    private ScreenState() {
    }

    private static boolean inBookshelf = false;
    private static boolean inLatest = false;

    public static boolean isInBookshelf() {
        return inBookshelf;
    }

    public static void enterBookshelf() {
        inBookshelf = true;
    }

    public static void leaveBookshelf() {
        inBookshelf = false;
    }

    public static boolean isInLatest() {
        return inLatest;
    }

    public static void enterLatest() {
        inLatest = true;
    }

    public static void leaveLatest() {
        inLatest = false;
    }
}
