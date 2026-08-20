package org.mewx.wenku8;

import android.app.Activity;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
     */
    @After
    public void restoreRealContext() {
        when(myApp.getApplicationContextLocal())
                .thenReturn(InstrumentationRegistry.getInstrumentation().getTargetContext());
        myApp.onCreate();
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