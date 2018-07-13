package org.mewx.wenku8.global.api;

import org.junit.Test;

import static org.junit.Assert.*;

public class Wenku8APITest {

    @Test
    public void searchBadWordsTestValid() {
        final String src = "就一些句子呗";
        assertNull(Wenku8API.searchBadWords(src));
    }

    @Test
    public void searchBadWordsTestInvalid() {
        final String src = "就一些句子呗。 。 。 。";
        assertEquals("。。。。", Wenku8API.searchBadWords(src));
    }

    @Test
    public void searchBadWordsTestTraditionalInvalid() {
        final String src = "就一些句子法輪功？？？";
        assertEquals("法輪功", Wenku8API.searchBadWords(src));
    }
}