package dev.novadev.novabank.core.ownership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.entity.BankRole;
import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.entity.OwnershipProvider;
import dev.novadev.novabank.core.selection.BankSelector;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class OwnershipRegistryTest {

    private static final UUID PLAYER = UUID.randomUUID();

    private final OwnershipRegistry registry = new OwnershipRegistry();

    @Test
    void le_joueur_est_proprietaire_de_sa_banque_personnelle() {
        registry.register(new PlayerOwnershipProvider(id -> Optional.of("Yo")));

        assertThat(registry.memberships(PLAYER, List.of("player")))
                .containsExactly(new EntityMembership(EntityRef.player(PLAYER), BankRole.OWNER));
        assertThat(registry.displayName(EntityRef.player(PLAYER))).isEqualTo("Yo");
        assertThat(registry.displayName(new EntityRef("player", "pas-un-uuid"))).isEqualTo("pas-un-uuid");
        assertThat(registry.displayName(new EntityRef("town", "Paris"))).isEqualTo("Paris");
    }

    @Test
    void types_non_actives_ignores_et_doublon_refuse() {
        registry.register(new PlayerOwnershipProvider(id -> Optional.empty()));

        assertThat(registry.memberships(PLAYER, List.of("island"))).isEmpty();
        assertThatThrownBy(() -> registry.register(new PlayerOwnershipProvider(id -> Optional.empty())))
                .isInstanceOf(IllegalStateException.class);
        registry.unregister("player");
        assertThat(registry.memberships(PLAYER, List.of("player"))).isEmpty();
    }

    @Test
    void un_fournisseur_en_echec_ou_hors_type_est_ecarte() {
        registry.register(new PlayerOwnershipProvider(id -> Optional.empty()));
        registry.register(provider("island", () -> {
            throw new IllegalStateException("plugin tiers en panne");
        }));
        registry.register(provider("faction", () -> List.of(new EntityMembership(new EntityRef("town", "x"),
                BankRole.MEMBER))));

        assertThat(registry.memberships(PLAYER, List.of("island", "faction", "player")))
                .extracting(membership -> membership.entity().type()).containsExactly("player");
        assertThat(registry.displayName(new EntityRef("island", "42"))).isEqualTo("42");
    }

    @Test
    void identifiant_de_banque_deterministe_par_entite() {
        EntityRef island = new EntityRef("island", "42");

        assertThat(BankId.forOwner(island)).isEqualTo(BankId.forOwner(new EntityRef("ISLAND", "42")));
        assertThat(BankId.forOwner(island)).isNotEqualTo(BankId.forOwner(new EntityRef("faction", "42")));
        assertThat(BankId.forOwner(island).value().version()).isEqualTo(3);
    }

    @Test
    void selection_par_choix_puis_priorite_puis_premiere_appartenance() {
        EntityMembership personal = new EntityMembership(EntityRef.player(PLAYER), BankRole.OWNER);
        EntityMembership island = new EntityMembership(new EntityRef("island", "1"), BankRole.MEMBER);
        List<EntityMembership> both = List.of(personal, island);

        assertThat(BankSelector.select(both, Optional.empty(), List.of("island", "player"))).contains(island);
        assertThat(BankSelector.select(both, Optional.of(EntityRef.player(PLAYER)), List.of("island"))).contains(personal);
        assertThat(BankSelector.select(both, Optional.of(new EntityRef("island", "2")), List.of("player")))
                .contains(personal);
        assertThat(BankSelector.select(List.of(island), Optional.empty(), List.of("player"))).contains(island);
        assertThat(BankSelector.select(List.of(), Optional.empty(), List.of("player"))).isEmpty();
    }

    private static OwnershipProvider provider(String type, Supplier<List<EntityMembership>> result) {
        return new OwnershipProvider() {
            @Override
            public String entityType() {
                return type;
            }

            @Override
            public List<EntityMembership> resolve(UUID playerId) {
                return result.get();
            }

            @Override
            public Optional<String> displayName(EntityRef entity) {
                throw new IllegalStateException("nom indisponible");
            }
        };
    }
}
