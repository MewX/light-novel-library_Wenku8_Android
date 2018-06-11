package org.mewx.wenku8.global.api;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mewx.wenku8.util.LightBase64;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Base64;
import java.util.Map;

import static org.mockito.Matchers.any;


@RunWith(PowerMockRunner.class)
@PrepareForTest({System.class, LightBase64.class, Wenku8API.class})
public class Wenku8APITest {

    private static final long CURRENT_TIME = System.currentTimeMillis();

    @Before
    public void init() {
        // mock base64 because it's on Android
        PowerMockito.mockStatic(LightBase64.class);
        PowerMockito.when(LightBase64.EncodeBase64(any(String.class))).thenAnswer((Answer<String>) invocation -> {
            Object[] args = invocation.getArguments();
            String input = (String) args[0];
            return new String(Base64.getEncoder().encode(input.getBytes()));
        });

        // init time and make time millis fixed
        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(CURRENT_TIME);

        // still need to spy current object
        PowerMockito.spy(Wenku8API.class);
    }

    @Test
    public void timeStampFixed() {
        Assert.assertEquals(System.currentTimeMillis(), CURRENT_TIME);
    }

    @Test
    public void testGetEncryptedMAP() {
        final String str = "test";
        Map<String, String> map = Wenku8API.getEncryptedMAP(str);
        Assert.assertEquals(map.size(), 1);
        Assert.assertEquals(map.get("request"), LightBase64.EncodeBase64(str + "&timetoken=" + CURRENT_TIME));
    }
}