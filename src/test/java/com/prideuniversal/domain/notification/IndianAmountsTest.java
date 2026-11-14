package com.prideuniversal.domain.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit test: the amounts in dues reminders, grouped the Indian way (DecimalFormat cannot). */
class IndianAmountsTest {

    private static String indian(String amount) {
        return NotificationService.indian(new BigDecimal(amount));
    }

    @Test
    @DisplayName("thousands, then lakhs and crores in pairs")
    void grouping() {
        assertThat(indian("176642")).isEqualTo("1,76,642");
        assertThat(indian("12345678")).isEqualTo("1,23,45,678");
        assertThat(indian("2850.00")).isEqualTo("2,850");
        assertThat(indian("2850.50")).isEqualTo("2,850.5");
        assertThat(indian("642")).isEqualTo("642");
        assertThat(indian("0")).isEqualTo("0");
        assertThat(indian("-77114")).isEqualTo("-77,114");
    }
}
