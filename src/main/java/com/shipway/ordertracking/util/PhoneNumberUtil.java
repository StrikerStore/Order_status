package com.shipway.ordertracking.util;

/**
 * Utility for formatting phone numbers for Botspace (e.g. Indian +91 format).
 */
public final class PhoneNumberUtil {

    private PhoneNumberUtil() {
    }

    /**
     * Format phone number with +91 country code for Indian numbers.
     * Removes spaces, dashes, and non-digits; strips leading 0; ensures +91 prefix.
     *
     * @param phone raw phone number (e.g. "9876543210", "91 9876543210", "0919876543210")
     * @return formatted number (e.g. "+919876543210"), or empty string if null/empty/invalid
     */
    public static String formatPhoneNumber(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "";
        }

        String digits = phone.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return "";
        }

        // Strip leading 0 (e.g. 09876543210 -> 9876543210)
        if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }

        // 10-digit Indian number: add +91
        if (digits.length() == 10) {
            return "+91" + digits;
        }

        // Already has 91 prefix: ensure +91 format
        if ( (digits.startsWith("91") || digits.startsWith("+91")) && digits.length() > 10) {
            return "+91" + digits.substring(2);
        }

        // Log error with the phone number?
        ("Invalid phone number: " + phone);
        //Handle where this methosd sused
        //return empty string
        return "";
    }
}
