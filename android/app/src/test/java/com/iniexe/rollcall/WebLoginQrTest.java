package com.iniexe.rollcall;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WebLoginQrTest {
    private static final String ID = "abcdefghijklmnopqrstuvwx";
    private static final String SECRET = "A".repeat(43);

    @Test public void acceptsOnlyRollcallWebSignInPayloads() {
        WebLoginQr.Payload payload = WebLoginQr.parse("rollcall://web-sign-in?challenge=" + ID + "&token=" + SECRET);

        assertEquals(ID, payload.challengeId);
        assertEquals(SECRET, payload.secret);
    }

    @Test public void rejectsOtherSchemesHostsAndExtraFields() {
        assertNull(WebLoginQr.parse("https://example.com/?challenge=" + ID + "&token=" + SECRET));
        assertNull(WebLoginQr.parse("rollcall://other?challenge=" + ID + "&token=" + SECRET));
        assertNull(WebLoginQr.parse("rollcall://web-sign-in?challenge=" + ID + "&token=" + SECRET + "&uid=student"));
    }

    @Test public void rejectsDuplicateFieldsFragmentsAndMalformedTokens() {
        assertNull(WebLoginQr.parse("rollcall://web-sign-in?challenge=" + ID + "&challenge=" + ID + "&token=" + SECRET));
        assertNull(WebLoginQr.parse("rollcall://web-sign-in?challenge=" + ID + "&token=" + SECRET + "#extra"));
        assertNull(WebLoginQr.parse("rollcall://web-sign-in?challenge=" + ID + "&token=short"));
        assertNull(WebLoginQr.parse("rollcall://web-sign-in?challenge=" + ID + "&token=" + SECRET + "&"));
        assertNull(WebLoginQr.parse("x".repeat(513)));
    }
}
