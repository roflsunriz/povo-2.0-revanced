package dev.roflsunriz.povo.automation;

import org.junit.Test;
import static org.junit.Assert.*;

public class DisplayEndpointTest {
    @Test public void acceptsHttpsEndpoint() {
        assertEquals("https://pc.example:8443/api/v1/status",
                DisplayEndpoint.validate(" https://pc.example:8443/api/v1/status "));
    }

    @Test public void rejectsCredentialsRedirectTargetsAndCleartext() {
        for (String value : new String[]{"http://192.168.1.2/api/v1/status", "https://u:p@pc/api/v1/status",
                "https://pc/api/v1/status?token=secret", "https://pc/api/v1/status#x",
                "https://pc/other", "https://pc:0/api/v1/status", "https://pc:65536/api/v1/status"}) {
            try {
                DisplayEndpoint.validate(value);
                fail(value);
            } catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void tokenCannotInjectHeaders() {
        assertTrue(DisplayEndpoint.validToken("a".repeat(32)));
        assertFalse(DisplayEndpoint.validToken("short"));
        assertFalse(DisplayEndpoint.validToken("a".repeat(32) + "\r\nX: y"));
        assertFalse(DisplayEndpoint.validToken("a".repeat(257)));
        assertFalse(DisplayEndpoint.validToken(null));
    }
}
