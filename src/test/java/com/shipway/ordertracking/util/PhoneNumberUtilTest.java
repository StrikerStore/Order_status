package com.shipway.ordertracking.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhoneNumberUtilTest {

    @Test
    void formatPhoneNumber_nullOrEmpty_returnsEmpty() {
        assertEquals("", PhoneNumberUtil.formatPhoneNumber(null));
        assertEquals("", PhoneNumberUtil.formatPhoneNumber(""));
    }

    @Test
    void formatPhoneNumber_nonDigitsOnly_returnsEmpty() {
        assertEquals("", PhoneNumberUtil.formatPhoneNumber("abc---"));
    }

    @Test
    void formatPhoneNumber_tenDigits_adds91() {
        assertEquals("+919876543210", PhoneNumberUtil.formatPhoneNumber("9876543210"));
    }

    @Test
    void formatPhoneNumber_tenDigitsWithFormatting_adds91() {
        assertEquals("+919876543210", PhoneNumberUtil.formatPhoneNumber("98765 43210"));
        assertEquals("+919876543210", PhoneNumberUtil.formatPhoneNumber("98765-43210"));
    }

    @Test
    void formatPhoneNumber_leadingZeroStrippedThenTenDigits() {
        assertEquals("+919876543210", PhoneNumberUtil.formatPhoneNumber("09876543210"));
    }

    @Test
    void formatPhoneNumber_twelveDigitsStartingWith91() {
        assertEquals("+919876543210", PhoneNumberUtil.formatPhoneNumber("919876543210"));
        assertEquals("+919876543210", PhoneNumberUtil.formatPhoneNumber("+91 98765 43210"));
    }

    @Test
    void formatPhoneNumber_tenDigitsStartingWith91() {
        assertEquals("+919198765432", PhoneNumberUtil.formatPhoneNumber("9198765432"));
    }

    @Test
    void formatPhoneNumber_invalidLength_returnsEmpty() {
        assertEquals("", PhoneNumberUtil.formatPhoneNumber("12345"));
        assertEquals("", PhoneNumberUtil.formatPhoneNumber("12345678901234"));
    }
}
