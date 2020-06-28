package org.mewx.wenku8.util;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import static org.junit.Assert.*;

@SmallTest
public class LightToolTest {

    @Test
    public void isInteger() {
        // true cases
        assertTrue(LightTool.isInteger("0"));
        assertTrue(LightTool.isInteger("1"));

        // false cases
        assertFalse(LightTool.isInteger(""));
        assertFalse(LightTool.isInteger("1.0"));
        assertFalse(LightTool.isInteger("1.."));
        assertFalse(LightTool.isInteger("a"));
    }

    @Test
    public void isDouble() {
        // true
        assertTrue(LightTool.isDouble("0.0"));
        assertTrue(LightTool.isDouble("1.0"));
        assertTrue(LightTool.isDouble("-1.0000009"));

        // false
        assertFalse(LightTool.isDouble(""));
        assertFalse(LightTool.isDouble("0"));
        assertFalse(LightTool.isDouble("1"));
        assertFalse(LightTool.isDouble("-1..0000009"));
        assertFalse(LightTool.isDouble("a"));
    }
}