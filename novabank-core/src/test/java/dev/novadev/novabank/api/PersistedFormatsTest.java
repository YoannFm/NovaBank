package dev.novadev.novabank.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Formats ecrits en base et dans config.yml : les changer rendrait introuvables les banques et les stocks deja
 * enregistres sur les serveurs. Les valeurs attendues ont ete calculees hors de Java (MD5 de
 * {@code novabank:<type>:<id>}, UUID de version 3) : ce test ne doit JAMAIS etre adapte au code, c'est le code qui doit
 * le respecter.
 */
class PersistedFormatsTest {

    @Test
    void l_identifiant_d_une_banque_ne_change_jamais() {
        assertThat(BankId.forOwner(EntityRef.player(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"))))
                .isEqualTo(new BankId(UUID.fromString("cce2b3de-d90b-3e71-9238-91cacbe2cb9a")));
        assertThat(BankId.forOwner(new EntityRef("island", "ile-1")))
                .isEqualTo(new BankId(UUID.fromString("36c93532-dfad-34fc-b40d-bd4d7081a548")));
        assertThat(BankId.forOwner(new EntityRef("clan", "abc")))
                .isEqualTo(new BankId(UUID.fromString("267aa2df-066d-30eb-a803-24ddb4e9e54e")));
        assertThat(BankId.forOwner(new EntityRef("region", "world:spawn")))
                .isEqualTo(new BankId(UUID.fromString("a39a2c55-2931-36d7-8c9f-d9fa6c47bfb3")));
    }

    @Test
    void le_type_d_entite_est_lu_sans_tenir_compte_de_la_casse() {
        assertThat(BankId.forOwner(new EntityRef("ISLAND", "ile-1"))).isEqualTo(BankId.forOwner(new EntityRef("island", "ile-1")));
    }

    @Test
    void la_cle_d_un_item_s_ecrit_et_se_relit_a_l_identique() {
        assertThat(ItemKey.minecraft("stone").toString()).isEqualTo("minecraft:stone");
        assertThat(new ItemKey("itemsadder", "pack/ruby_block").toString()).isEqualTo("itemsadder:pack/ruby_block");
        for (String text : new String[] {"minecraft:stone", "itemsadder:pack/ruby_block", "nexo:a.b-c_d"}) {
            assertThat(ItemKey.parse(text).toString()).isEqualTo(text);
        }
    }

    @Test
    void la_forme_texte_des_identifiants_est_l_uuid_seul() {
        UUID uuid = UUID.fromString("cce2b3de-d90b-3e71-9238-91cacbe2cb9a");
        assertThat(new BankId(uuid).toString()).isEqualTo("cce2b3de-d90b-3e71-9238-91cacbe2cb9a");
        assertThat(new OperationId(uuid).toString()).isEqualTo("cce2b3de-d90b-3e71-9238-91cacbe2cb9a");
    }
}
