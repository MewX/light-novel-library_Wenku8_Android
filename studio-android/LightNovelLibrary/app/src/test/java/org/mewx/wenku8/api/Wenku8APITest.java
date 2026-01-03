package org.mewx.wenku8.api;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mewx.wenku8.BuildConfig;
import org.mewx.wenku8.util.LightBase64;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;
import java.util.Objects;


@RunWith(MockitoJUnitRunner.class)
public class Wenku8APITest {

    @Test
    public void testGetEncryptedMAP() {
        String str = "test";
        Map<String, String> map = Wenku8API.getEncryptedMAP(str);

        Assert.assertEquals(map.size(), 3);
        Assert.assertEquals(map.get("appver"), BuildConfig.VERSION_NAME);
        Assert.assertEquals(LightBase64.DecodeBase64String(Objects.requireNonNull(map.get("request"))), str);
        Assert.assertTrue(Long.parseLong(Objects.requireNonNull(map.get("timetoken"))) > 0L);
    }
}