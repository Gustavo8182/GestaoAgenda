package br.com.agendaplatform.clients.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneNormalizerTest {

    @Test
    void normalizesDifferentFormatsToTheSameValue() {
        assertThat(PhoneNormalizer.normalize("(21) 99999-9999")).isEqualTo("21999999999");
        assertThat(PhoneNormalizer.normalize("21999999999")).isEqualTo("21999999999");
        assertThat(PhoneNormalizer.normalize("+55 21 99999-9999")).isEqualTo("21999999999");
    }

    @Test
    void doesNotStripDddFiftyFiveWhenThereIsNoCountryCode() {
        assertThat(PhoneNormalizer.normalize("55 9999-8888")).isEqualTo("5599998888");
        assertThat(PhoneNormalizer.normalize("55 99999-8888")).isEqualTo("55999998888");
    }

    @Test
    void treatsBlankOrNullAsEmpty() {
        assertThat(PhoneNormalizer.normalize("")).isEmpty();
        assertThat(PhoneNormalizer.normalize(null)).isEmpty();
    }
}
