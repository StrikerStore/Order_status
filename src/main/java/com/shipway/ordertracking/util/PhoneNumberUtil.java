package com.shipway.ordertracking.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for formatting phone numbers for Botspace (e.g. Indian +91 format).
 */
public final class PhoneNumberUtil {

    private static final Logger log = LoggerFactory.getLogger(PhoneNumberUtil.class);

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

        // Already has 91 prefix (12 digits): ensure +91 format, must have 10 digits after 91
        if (digits.startsWith("91") && digits.length() == 12) {
            return "+91" + digits.substring(2);
        }

        // Invalid length (e.g. 6 digits): Indian mobile must be 10 digits or 91+10
        log.warn("Invalid phone number: length={} (expected 10 or 12 digits with 91 prefix), input=\"{}\"",
                digits.length(), phone);
        return "";
    }
}
