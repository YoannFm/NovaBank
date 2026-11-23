package dev.novadev.novabank.core.application;

import static dev.novadev.novabank.core.application.ApplicationFixture.PERSONAL;
import static dev.novadev.novabank.core.application.ApplicationFixture.PLAYER;
import static dev.novadev.novabank.core.application.ApplicationFixture.STONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.operation.OperationType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BankAdministrationTest {

    private final ApplicationFixture fixture = new ApplicationFixture();

    @Test
    void consultation_d_une_banque_existante_ou_jamais_utilisee() {
        fixture.inventory.put(PLAYER, STONE, 9);
        fixture.depositOne(STONE, 9);
        List<Optional<BankSnapshot>> views = new ArrayList<>();

        fixture.administration.view(PERSONAL, views::add);
        fixture.administration.view(EntityRef.player(UUID.randomUUID()), views::add);
        fixture.scheduler.runAll();

        assertThat(views.get(0)).hasValueSatisfying(snapshot -> {
            assertThat(snapshot.exists()).isTrue();
            assertThat(snapshot.stock()).containsEntry(STONE, 9L);
        });
        assertThat(views.get(1)).hasValueSatisfying(snapshot -> assertThat(snapshot.exists()).isFalse());
    }

    @Test
    void historique_pagine_du_plus_recent_au_plus_ancien() {
        fixture.inventory.put(PLAYER, STONE, 30);
        for (int i = 1; i <= 12; i++) {
            fixture.depositOne(STONE, 1);
        }
        List<Optional<HistoryPage>> pages = new ArrayList<>();

        fixture.administration.history(PERSONAL, 1, pages::add);
        fixture.administration.history(PERSONAL, 2, pages::add);
        fixture.scheduler.runAll();

        assertThat(pages.get(0)).hasValueSatisfying(page -> {
            assertThat(page.entries()).hasSize(BankAdministration.HISTORY_PAGE_SIZE);
            assertThat(page.hasNext()).isTrue();
            assertThat(page.entries()).allMatch(record -> record.type() == OperationType.DEPOSIT);
        });
        assertThat(pages.get(1)).hasValueSatisfying(page -> {
            assertThat(page.entries()).hasSize(2);
            assertThat(page.hasNext()).isFalse();
        });
        assertThatThrownBy(() -> fixture.administration.history(PERSONAL, 0, page -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reset_efface_la_valeur_sans_toucher_au_stock() {
        fixture.inventory.put(PLAYER, STONE, 9);
        fixture.depositOne(STONE, 9);
        List<Optional<OperationResult>> results = new ArrayList<>();

        fixture.administration.reset(PERSONAL, "Admin", results::add);
        fixture.administration.reset(EntityRef.player(UUID.randomUUID()), "Admin", results::add);
        fixture.scheduler.runAll();

        assertThat(results.get(0)).hasValueSatisfying(result -> {
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.valueDelta()).isEqualByComparingTo("-9");
        });
        assertThat(results.get(1)).hasValueSatisfying(result ->
                assertThat(result.status()).isEqualTo(OperationStatus.UNKNOWN_BANK));
        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(9);
        assertThat(fixture.bank(PERSONAL).resetEpoch()).isEqualTo(1);
    }

    @Test
    void base_indisponible_reponse_vide() {
        fixture.repository.failEverything(true);
        List<Optional<BankSnapshot>> views = new ArrayList<>();

        fixture.administration.view(PERSONAL, views::add);
        fixture.scheduler.runAll();

        assertThat(views).containsExactly(Optional.empty());
    }
}
