package dev.novadev.novabank.core.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConsoleTextTest {

    @AfterEach
    void backToFrench() {
        ConsoleText.language("fr");
    }

    @Test
    void francais_par_defaut_avec_parametres_et_apostrophes() {
        assertThat(ConsoleText.tr("Integration : {0} prise en charge (type de banque '{1}')", "Towny", "town"))
                .isEqualTo("Integration : Towny prise en charge (type de banque 'town')");
    }

    @Test
    void anglais_pour_toute_autre_langue() {
        ConsoleText.language("en");
        assertThat(ConsoleText.tr("Integration : {0} prise en charge (type de banque '{1}')", "Towny", "town"))
                .isEqualTo("Integration: Towny supported (bank type 'town')");
        ConsoleText.language("de");
        assertThat(ConsoleText.tr("chemin inconnu")).isEqualTo("unknown path");
    }

    @Test
    void texte_sans_traduction_reste_en_francais() {
        ConsoleText.language("en");
        assertThat(ConsoleText.tr("phrase absente du catalogue {0}", 1)).isEqualTo("phrase absente du catalogue 1");
    }
}
