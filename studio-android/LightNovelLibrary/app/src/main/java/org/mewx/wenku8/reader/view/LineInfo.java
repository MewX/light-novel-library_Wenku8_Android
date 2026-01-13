package org.mewx.wenku8.reader.view;

import org.mewx.wenku8.reader.loader.WenkuReaderLoader;

import java.util.Objects;

public record LineInfo(WenkuReaderLoader.ElementType type, String text) {
    public LineInfo {
        Objects.requireNonNull(type);
        Objects.requireNonNull(text);
    }
}
