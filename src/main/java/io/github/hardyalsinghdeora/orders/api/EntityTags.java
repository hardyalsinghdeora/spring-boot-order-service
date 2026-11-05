package io.github.hardyalsinghdeora.orders.api;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;

/** The order version doubles as its ETag: {@code ETag: "3"} out, {@code If-Match: "3"} in. */
final class EntityTags {

    private static final Pattern VERSION = Pattern.compile("\\d{1,18}");

    private EntityTags() {
    }

    static String of(long version) {
        return "\"" + version + "\"";
    }

    /** Accepts the quoted form RFC 9110 requires and, leniently, bare digits. Weak tags never match. */
    static long parseVersion(String ifMatch) {
        String value = ifMatch.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        if (!VERSION.matcher(value).matches()) {
            throw Problems.exception(HttpStatus.BAD_REQUEST, "malformed-if-match",
                    "If-Match must be the quoted order version, for example If-Match: \"3\"");
        }
        return Long.parseLong(value);
    }
}
