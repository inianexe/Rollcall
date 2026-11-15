package com.iniexe.rollcall;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

final class WebLoginQr {
    private WebLoginQr() { }

    static Payload parse(String raw) {
        if (raw == null || raw.length() > 512) return null;
        try {
            URI uri = URI.create(raw);
            if (!"rollcall".equals(uri.getScheme()) || !"web-sign-in".equals(uri.getHost())
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawUserInfo() != null || uri.getPort() != -1 || uri.getRawFragment() != null) return null;
            String query = uri.getRawQuery();
            if (query == null) return null;
            Map<String, String> fields = new HashMap<>();
            for (String pair : query.split("&", -1)) {
                int equals = pair.indexOf('=');
                if (equals <= 0 || equals == pair.length() - 1) return null;
                String key = pair.substring(0, equals);
                String value = pair.substring(equals + 1);
                if (fields.put(key, value) != null) return null;
            }
            String challengeId = fields.get("challenge"), secret = fields.get("token");
            if (fields.size() != 2 || challengeId == null || secret == null
                    || !challengeId.matches("[A-Za-z0-9_-]{24}")
                    || !secret.matches("[A-Za-z0-9_-]{43}")) return null;
            return new Payload(challengeId, secret);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    static final class Payload {
        final String challengeId;
        final String secret;

        Payload(String challengeId, String secret) {
            this.challengeId = challengeId;
            this.secret = secret;
        }
    }
}
