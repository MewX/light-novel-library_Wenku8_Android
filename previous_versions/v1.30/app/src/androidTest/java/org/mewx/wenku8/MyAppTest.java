package org.mewx.wenku8;

import android.app.Activity;
import android.content.Context;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@SmallTest
public class MyAppTest {
    @InjectMocks
    private MyApp myApp = new MyApp();

    @Mock
    private Activity activity;

    @Before
    public void init() {
        initMocks(this);
    }

    /**
     * Puts the real application context back.
     *
     * <p>{@code MyApp.context} is process-wide static, and both tests here leave it holding a
     * mock or null. Anything that runs afterwards and asks {@code MyApp} for a context gets that
     * instead of the real one — {@code SaveFileMigration.getInternalSavePath} did, and cached a
     * save path built from a mocked {@code getFilesDir()} for the rest of the run, which broke
     * every storage test in the suite. Root cause 2 of STABILITY_PLAN.md, reaching the tests.
     *
     * <p>Written to the field rather than driven through {@code onCreate}. Going through
     * {@code onCreate} would mean stubbing a method on {@code myApp}, which is a real instance
     * and not a mock, so whether the stub takes at all is a question about Mockito rather than
     * about this test — and a restore that quietly does not restore is worse than none, since it
     * fails somewhere else entirely. It would also re-run every {@code Application#onCreate}
     * side effect after each test. The assertion is here for the same reason: this method has no
     * value unless it demonstrably worked.
     */
    @After
    public void restoreRealContext() throws Exception {
        Context real = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getApplicationContext();

        Field contextField = MyApp.class.getDeclaredField("context");
        contextField.setAccessible(true);
        contextField.set(null, real);

        assertSame("MyApp.context was not restored; later tests will see a stale context",
                real, MyApp.getContext());
    }

    @Test
    public void getContextTest() {
        when(myApp.getApplicationContextLocal()).thenReturn(activity);
        myApp.onCreate();
        assertEquals(activity, MyApp.getContext());
    }

    @Test
    public void getContextNullTest() {
        when(myApp.getApplicationContextLocal()).thenReturn(null);
        myApp.onCreate();
        assertNull(MyApp.getContext());
    }
}