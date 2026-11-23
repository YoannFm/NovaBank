package dev.novadev.novabank.api.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntityRefTest {

    @Test
    void le_type_est_normalise_en_minuscules_et_l_identifiant_conserve() {
        EntityRef ref = new EntityRef("Island", "Abc-42");

        assertThat(ref.type()).isEqualTo(EntityRef.ISLAND);
        assertThat(ref.id()).isEqualTo("Abc-42");
        assertThat(ref).isEqualTo(new EntityRef("ISLAND", "Abc-42"));
        assertThat(ref.toString()).isEqualTo("island:Abc-42");
    }

    @Test
    void un_type_tiers_est_accepte() {
        assertThat(new EntityRef("husktowns.town", "7").type()).isEqualTo("husktowns.town");
    }

    @Test
    void la_reference_joueur_utilise_son_uuid() {
        UUID player = UUID.randomUUID();

        assertThat(EntityRef.player(player)).isEqualTo(new EntityRef(EntityRef.PLAYER, player.toString()));
    }

    @Test
    void les_references_invalides_sont_refusees() {
        assertThatThrownBy(() -> new EntityRef("", "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntityRef("my type", "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntityRef("player", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntityRef(null, "1")).isInstanceOf(NullPointerException.class);
    }
}
