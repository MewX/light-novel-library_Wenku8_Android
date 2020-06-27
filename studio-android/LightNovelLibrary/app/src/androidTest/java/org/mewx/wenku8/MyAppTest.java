package org.mewx.wenku8;

import android.app.Activity;
import androidx.test.filters.SmallTest;

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