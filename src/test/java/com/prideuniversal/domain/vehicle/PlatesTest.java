package com.prideuniversal.domain.vehicle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit test: no Spring, no database, so it runs anywhere. */
class PlatesTest {

    @Test
    @DisplayName("registered numbers are split on separators but keep their inner spaces")
    void splitsTheRegister() {
        assertThat(Plates.registered("MH 12 AB 1234, mh14cd5678; KA-01-X-9/ x"))
                .containsExactly("MH 12 AB 1234", "mh14cd5678", "KA-01-X-9");
        assertThat(Plates.registered(null)).isEmpty();
        assertThat(Plates.registered("  ")).isEmpty();
    }

    @Test
    @DisplayName("spacing, dashes and case do not matter")
    void exact() {
        assertThat(Plates.compare("mh-12 ab 1234", "MH 12 AB 1234")).isEqualTo(Plates.Match.EXACT);
        assertThat(Plates.compare("22BH1234AA", "22 BH 1234 AA")).isEqualTo(Plates.Match.EXACT);
    }

    @Test
    @DisplayName("letters the camera reads as digits still match, and so does a scan with the IND strip")
    void close() {
        assertThat(Plates.compare("MH12A81234", "MH12AB1234")).isEqualTo(Plates.Match.CLOSE);
        assertThat(Plates.compare("MH12AB0I23", "MH12ABOI23")).isEqualTo(Plates.Match.CLOSE);
        assertThat(Plates.compare("INDMH12AB1234", "MH12AB1234")).isEqualTo(Plates.Match.CLOSE);
    }

    @Test
    @DisplayName("the last digits alone find the vehicle; a different plate does not")
    void partialAndMisses() {
        assertThat(Plates.compare("1234", "MH12AB1234")).isEqualTo(Plates.Match.PARTIAL);
        assertThat(Plates.compare("AB1234", "MH12AB1234")).isEqualTo(Plates.Match.PARTIAL);
        assertThat(Plates.compare("1235", "MH12AB1234")).isNull();
        assertThat(Plates.compare("MH12AB1235", "MH12AB1234")).isNull();
        assertThat(Plates.compare("123", "MH12AB1234")).as("too short to ask").isNull();
    }

    @Test
    @DisplayName("strength orders EXACT before CLOSE before PARTIAL")
    void ordering() {
        assertThat(Plates.Match.EXACT).isLessThan(Plates.Match.CLOSE);
        assertThat(Plates.Match.CLOSE).isLessThan(Plates.Match.PARTIAL);
        assertThat(Plates.Match.CLOSE.code()).isEqualTo("close");
    }
}
