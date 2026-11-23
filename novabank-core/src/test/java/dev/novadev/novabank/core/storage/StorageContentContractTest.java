package dev.novadev.novabank.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import dev.novadev.novabank.core.ledger.BankLedger;
import dev.novadev.novabank.core.migration.ContentSummary;
import dev.novadev.novabank.core.migration.MigrationException;
import dev.novadev.novabank.core.migration.MigrationReport;
import dev.novadev.novabank.core.migration.StorageMigration;
import dev.novadev.novabank.core.testing.ContentFixture;
import dev.novadev.novabank.core.testing.ContentInvariants;
import dev.novadev.novabank.core.testing.MutablePriceCatalog;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contrat {@link StorageContent} commun a tous les backends : lecture et ecriture en bloc a
 * l'identique, refus d'une cible non vide, copie utilisable ensuite par le service metier.
 *
 * <p>Chaque backend fournit des backends vides et independants via {@link #newBackend()} (fermes
 * par la classe concrete).
 */
public abstract class StorageContentContractTest {

    /**
     * @return un backend vide, independant des precedents
     */
    protected abstract StorageBackend newBackend();

    @Test
    void un_backend_neuf_est_vide_et_ne_l_est_plus_apres_un_choix_de_banque_active() {
        StorageBackend backend = newBackend();
        assertThat(backend.content().isEmpty()).isTrue();

        backend.activeBanks().save(UUID.randomUUID(), new EntityRef(EntityRef.ISLAND, "ile"));

        assertThat(backend.content().isEmpty()).isFalse();
    }

    @Test
    void la_migration_copie_tout_a_l_identique_et_verifie_la_copie() {
        StorageBackend source = newBackend();
        ContentFixture.populate(source);
        StorageBackend target = newBackend();
        List<String> progress = new ArrayList<>();

        MigrationReport report = new StorageMigration(progress::add, Clock.systemUTC())
                .migrate(source.content(), target.content());

        ContentFixture.assertSameContent(source.content(), target.content());
        assertThat(ContentInvariants.violations(target.content())).isEmpty();
        assertThat(ContentSummary.of(source.content()).differences(ContentSummary.of(target.content()))).isEmpty();
        assertThat(report.copied().banks()).isEqualTo(2L);
        assertThat(report.copied().deliveries()).isEqualTo(3L);
        assertThat(report.copied().activeBanks()).isEqualTo(2L);
        assertThat(progress).anyMatch(line -> line.contains("operations copiees au total"));
    }

    @Test
    void une_cible_non_vide_est_refusee_sans_aucune_ecriture() {
        StorageBackend source = newBackend();
        ContentFixture.populate(source);
        StorageBackend target = newBackend();
        target.activeBanks().save(UUID.randomUUID(), new EntityRef(EntityRef.ISLAND, "deja-la"));
        ContentSummary before = ContentSummary.of(target.content());

        assertThatThrownBy(() -> new StorageMigration(line -> { }, Clock.systemUTC())
                .migrate(source.content(), target.content()))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("n'est pas vide");

        assertThat(before.differences(ContentSummary.of(target.content()))).isEmpty();
    }

    @Test
    void la_copie_reste_utilisable_par_le_service_metier() {
        StorageBackend source = newBackend();
        OperationId reserved = ContentFixture.populate(source);
        StorageBackend target = newBackend();
        new StorageMigration(line -> { }, Clock.systemUTC()).migrate(source.content(), target.content());
        MutablePriceCatalog catalog = new MutablePriceCatalog().price(ContentFixture.STONE, "1.2345")
                .price(ContentFixture.RUBY, "12.3456");
        BankLedger ledger = new BankLedger(target.banks(), catalog, target.deliveries(),
                UndeliveredPolicy.PENDING_DELIVERY, Clock.systemUTC());
        long versionBefore = target.banks().findOperation(reserved).flatMap(record ->
                target.banks().findBank(record.bankId())).orElseThrow().version();

        assertThat(target.banks().reservedWithdrawals()).extracting(record -> record.operationId())
                .containsExactly(reserved);
        assertThat(ledger.confirmWithdrawal(reserved, 7L).status()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(ledger.confirmWithdrawal(reserved, 7L).status()).isEqualTo(OperationStatus.DUPLICATE_OPERATION);
        long versionAfter = target.banks().findOperation(reserved).flatMap(record ->
                target.banks().findBank(record.bankId())).orElseThrow().version();
        assertThat(versionAfter).isEqualTo(versionBefore + 1);
        assertThat(target.banks().reservedWithdrawals()).isEmpty();
        assertThat(ContentInvariants.violations(target.content())).isEmpty();
    }
}
