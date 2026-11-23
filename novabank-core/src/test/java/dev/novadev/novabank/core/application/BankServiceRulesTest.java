package dev.novadev.novabank.core.application;

import static dev.novadev.novabank.core.application.ApplicationFixture.ISLAND;
import static dev.novadev.novabank.core.application.ApplicationFixture.PERSONAL;
import static dev.novadev.novabank.core.application.ApplicationFixture.PLAYER;
import static dev.novadev.novabank.core.application.ApplicationFixture.STONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.entity.BankRole;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.core.access.RoleRights;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Verrou par joueur, delai, droits par role et banque active.
 */
class BankServiceRulesTest {

    private final ApplicationFixture fixture = new ApplicationFixture();

    @Test
    void une_seconde_operation_pendant_la_premiere_est_refusee() {
        fixture.inventory.put(PLAYER, STONE, 10);
        List<List<TransferResult>> first = new ArrayList<>();
        List<TransferResult> second = new ArrayList<>();

        fixture.service.deposit(PLAYER, ItemChoice.of(STONE), Amount.of(5), first::add);
        fixture.advance(Duration.ofSeconds(5));
        fixture.service.withdraw(PLAYER, STONE, Amount.of(1), second::add);
        fixture.scheduler.runAll();

        assertThat(second).extracting(TransferResult::status).containsExactly(TransferStatus.BUSY);
        assertThat(first).singleElement().satisfies(results -> assertThat(results.get(0).status())
                .isEqualTo(TransferStatus.SUCCESS));
        assertThat(fixture.withdraw(STONE, Amount.of(1)).status()).isEqualTo(TransferStatus.SUCCESS);
    }

    @Test
    void le_delai_entre_deux_operations_est_impose() {
        fixture.inventory.put(PLAYER, STONE, 10);
        fixture.deposit(ItemChoice.of(STONE), Amount.of(1));

        List<TransferResult> early = fixture.deposit(ItemChoice.of(STONE), Amount.of(1));
        fixture.advance(Duration.ofMillis(500));
        List<TransferResult> late = fixture.deposit(ItemChoice.of(STONE), Amount.of(1));

        assertThat(early).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(TransferStatus.COOLDOWN);
            assertThat(result.retryAfter()).isEqualTo(Duration.ofMillis(500));
        });
        assertThat(late).extracting(TransferResult::status).containsExactly(TransferStatus.SUCCESS);
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(8);
    }

    @Test
    void les_droits_par_role_sont_verifies_a_chaque_operation() {
        fixture.withPriority(List.of("island", EntityRef.PLAYER));
        fixture.joinIsland(ISLAND, BankRole.MEMBER);
        fixture.inventory.put(PLAYER, STONE, 10);

        assertThat(fixture.depositOne(STONE, 4).status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(fixture.withdraw(STONE, Amount.of(1)).status()).isEqualTo(TransferStatus.ROLE_NOT_ALLOWED);
        assertThat(fixture.stock(ISLAND, STONE)).isEqualTo(4);

        fixture.withRoles("island", new RoleRights(Set.of(BankRole.OWNER), EnumSet.allOf(BankRole.class)));
        assertThat(fixture.depositOne(STONE, 1).status()).isEqualTo(TransferStatus.ROLE_NOT_ALLOWED);
        assertThat(fixture.withdraw(STONE, Amount.of(1)).status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(7);
    }

    @Test
    void les_droits_decides_par_le_plugin_hote_priment_sur_les_roles_operation_par_operation() {
        fixture.withPriority(List.of("island", EntityRef.PLAYER));
        fixture.joinIsland(ISLAND, BankRole.MEMBER);
        fixture.inventory.put(PLAYER, STONE, 10);
        fixture.hostRights.put(OperationType.WITHDRAW, true);

        assertThat(fixture.depositOne(STONE, 4).status()).as("depot non decide par l'hote : regle des roles")
                .isEqualTo(TransferStatus.SUCCESS);
        assertThat(fixture.withdraw(STONE, Amount.of(1)).status()).as("membre, mais privilege de retrait de l'hote")
                .isEqualTo(TransferStatus.SUCCESS);

        fixture.hostRights.put(OperationType.DEPOSIT, false);
        assertThat(fixture.depositOne(STONE, 1).status()).as("l'hote refuse le depot")
                .isEqualTo(TransferStatus.ROLE_NOT_ALLOWED);

        fixture.hostRights.clear();
        assertThat(fixture.depositOne(STONE, 1).status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(fixture.withdraw(STONE, Amount.of(1)).status()).as("l'hote ne decide plus : regle des roles")
                .isEqualTo(TransferStatus.ROLE_NOT_ALLOWED);
    }

    @Test
    void banque_active_par_defaut_selon_la_priorite_puis_choix_du_joueur() {
        fixture.joinIsland(ISLAND, BankRole.OWNER);
        fixture.inventory.put(PLAYER, STONE, 10);

        fixture.depositOne(STONE, 1);
        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(1);

        List<SelectionResult> selected = new ArrayList<>();
        fixture.service.select(PLAYER, "island", selected::add);
        fixture.scheduler.runAll();
        assertThat(selected).singleElement().satisfies(result -> assertThat(result.status()).isEqualTo(QueryStatus.OK));

        fixture.depositOne(STONE, 2);
        assertThat(fixture.stock(ISLAND, STONE)).isEqualTo(2);
        assertThat(fixture.activeBanks.find(PLAYER)).contains(ISLAND);
    }

    @Test
    void perte_d_appartenance_la_banque_active_revient_au_choix_par_defaut() {
        fixture.joinIsland(ISLAND, BankRole.OWNER);
        fixture.activeBanks.save(PLAYER, ISLAND);
        fixture.leaveIslands();
        fixture.inventory.put(PLAYER, STONE, 3);

        TransferResult result = fixture.depositOne(STONE, 3);

        assertThat(result.bank()).contains(PERSONAL);
    }

    @Test
    void choix_d_un_type_inconnu_ou_sans_appartenance_refuse() {
        List<SelectionResult> results = new ArrayList<>();
        fixture.service.select(PLAYER, "faction", results::add);
        fixture.service.select(PLAYER, "island", results::add);
        fixture.scheduler.runAll();

        assertThat(results).extracting(SelectionResult::status)
                .containsExactly(QueryStatus.UNKNOWN_TYPE, QueryStatus.NOT_MEMBER);
    }

    @Test
    void sans_aucune_appartenance_pas_de_banque() {
        fixture.withRoles("island", RoleRights.DEFAULT);
        fixture.ownership.unregister(EntityRef.PLAYER);
        fixture.inventory.put(PLAYER, STONE, 3);

        assertThat(fixture.depositOne(STONE, 3).status()).isEqualTo(TransferStatus.NO_BANK);
        assertThat(fixture.withdraw(STONE, Amount.of(3)).status()).isEqualTo(TransferStatus.NO_BANK);
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(3);
    }

    @Test
    void consultation_de_la_banque_active() {
        fixture.inventory.put(PLAYER, STONE, 10);
        fixture.depositOne(STONE, 7);
        List<BankView> views = new ArrayList<>();

        fixture.service.info(PLAYER, views::add);
        fixture.scheduler.runAll();

        assertThat(views).singleElement().satisfies(view -> {
            assertThat(view.status()).isEqualTo(QueryStatus.OK);
            assertThat(view.active().orElseThrow().entity()).isEqualTo(PERSONAL);
            assertThat(view.snapshot().orElseThrow().stock()).containsEntry(STONE, 7L);
            assertThat(view.snapshot().orElseThrow().value()).isEqualByComparingTo("7");
        });
    }

    @Test
    void consultation_base_indisponible() {
        fixture.repository.failEverything(true);
        List<BankView> views = new ArrayList<>();

        fixture.service.info(PLAYER, views::add);
        fixture.scheduler.runAll();

        assertThat(views).extracting(BankView::status).containsExactly(QueryStatus.STORAGE_ERROR);
    }
}
