package org.mewx.wenku8.api;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Public immutable DTO mirroring {@code :api}'s {@code SealedRequest} for open-source stub builds.
 */
public final class SealedRequest {
    @NonNull
    private final String endpointUrl;
    @NonNull
    private final String contentType;
    @NonNull
    private final Map<String, String> headers;
    @NonNull
    private final byte[] body;

    public SealedRequest(
            @NonNull String endpointUrl,
            @NonNull String contentType,
            @NonNull Map<String, String> headers,
            @NonNull byte[] body) {
        this.endpointUrl = Objects.requireNonNull(endpointUrl, "endpointUrl");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(headers, "headers")));
        this.body = Objects.requireNonNull(body, "body").clone();
    }

    @NonNull
    public String getEndpointUrl() {
        return endpointUrl;
    }

    @NonNull
    public String getContentType() {
        return contentType;
    }

    @NonNull
    public Map<String, String> getHeaders() {
        return headers;
    }

    @NonNull
    public byte[] getBody() {
        return body.clone();
    }
}
