package dev.roflsunriz.povo.automation;

import java.net.URI;

final class DisplayEndpoint {
    private DisplayEndpoint() {}

    static String validate(String value) {
        URI uri = URI.create(value.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535
                || !"/api/v1/status".equals(uri.getPath())) {
            throw new IllegalArgumentException("Expected HTTPS status endpoint");
        }
        return uri.toASCIIString();
    }

    static boolean validToken(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{32,256}");
    }
}
