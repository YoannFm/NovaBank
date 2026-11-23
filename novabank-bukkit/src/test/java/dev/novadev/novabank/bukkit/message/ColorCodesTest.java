package dev.novadev.novabank.bukkit.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ColorCodesTest {

    @Test
    void codes_simples_styles_et_reinitialisation() {
        assertThat(ColorCodes.translate("&aVert &L&ogras&r fin")).isEqualTo("\u00a7aVert \u00a7l\u00a7ogras\u00a7r fin");
    }

    @Test
    void couleurs_hexadecimales() {
        assertThat(ColorCodes.translate("&#1a2B3cX")).isEqualTo("\u00a7x\u00a71\u00a7a\u00a72\u00a7b\u00a73\u00a7cX");
    }

    @Test
    void esperluettes_sans_code_conservees() {
        assertThat(ColorCodes.translate("A & B &z &#12345 &")).isEqualTo("A & B &z &#12345 &");
    }
}
