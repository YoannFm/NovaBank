package dev.novadev.novabank.bukkit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimpleClansTagsTest {

    @Test
    void identifiantSansCouleurEnMinuscules() {
        assertThat(SimpleClansTags.id("&cABC")).isEqualTo("abc");
        assertThat(SimpleClansTags.id("Abc")).isEqualTo("abc");
    }
}
