package com.prideuniversal.common;

public final class Text {

    private Text() {}

    /** Empty strings from forms are absent values, not values. */
    public static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** Never log a full mobile number. */
    public static String mask(String mobile) {
        return mobile == null || mobile.length() < 4 ? "****" : "******" + mobile.substring(mobile.length() - 4);
    }
}
