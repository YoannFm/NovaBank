package dev.novadev.novabank.core.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.storage.ActiveBankChoice;
import dev.novadev.novabank.core.storage.StorageContent;
import dev.novadev.novabank.core.storage.StoredDelivery;
import dev.novadev.novabank.core.testing.ContentFixture;
import dev.novadev.novabank.core.testing.InMemoryStorageBackend;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Echecs de la migration : ecriture interrompue, copie alteree, source jamais modifiee.
 */
class StorageMigrationTest {

    private final InMemoryStorageBackend source = new InMemoryStorageBackend();

    StorageMigrationTest() {
        ContentFixture.populate(source);
    }

    @Test
    void une_ecriture_en_echec_interrompt_la_migration_sans_toucher_a_la_source() {
        ContentSummary before = ContentSummary.of(source.content());
        InMemoryStorageBackend target = new InMemoryStorageBackend();
        StorageContent failing = new Wrapper(target.content()) {
            @Override
            public void importOperations(List<OperationRecord> records) {
                throw new StorageException("disque plein");
            }
        };

        assertThatThrownBy(() -> migration().migrate(source.content(), failing))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("disque plein")
                .hasMessageContaining("copie partielle");

        assertThat(before.differences(ContentSummary.of(source.content()))).isEmpty();
    }

    @Test
    void une_copie_alteree_est_detectee_par_la_verification() {
        InMemoryStorageBackend target = new InMemoryStorageBackend();
        StorageContent altering = new Wrapper(target.content()) {
            @Override
            public void importBanks(List<Bank> banks) {
                super.importBanks(banks.stream().map(bank -> bank.withVersion(bank.version() + 1)).toList());
            }
        };

        assertThatThrownBy(() -> migration().migrate(source.content(), altering))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("Verification en echec")
                .hasMessageContaining("2 banque(s) differente(s)");
    }

    @Test
    void une_livraison_perdue_est_detectee_par_les_comptes() {
        InMemoryStorageBackend target = new InMemoryStorageBackend();
        StorageContent lossy = new Wrapper(target.content()) {
            @Override
            public void importDeliveries(List<StoredDelivery> deliveries) {
                super.importDeliveries(deliveries.subList(1, deliveries.size()));
            }
        };

        assertThatThrownBy(() -> migration().migrate(source.content(), lossy))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("livraisons : 3 a la source, 2 dans la cible");
    }

    @Test
    void une_ecriture_sur_la_source_pendant_la_copie_est_detectee() {
        StorageContent target = new Wrapper(new InMemoryStorageBackend().content()) {
            @Override
            public void importActiveBanks(List<ActiveBankChoice> choices) {
                super.importActiveBanks(choices);
                source.activeBanks().save(UUID.randomUUID(), new EntityRef("player", "tardif"));
            }
        };

        assertThatThrownBy(() -> migration().migrate(source.content(), target))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("La source a change pendant la copie")
                .hasMessageContaining("banques actives : 2 copiees, 3 a la source maintenant");
    }

    @Test
    void une_cible_inaccessible_est_signalee_sans_copie() {
        StorageContent unreachable = new Wrapper(new InMemoryStorageBackend().content()) {
            @Override
            public boolean isEmpty() {
                throw new StorageException("connexion refusee");
            }
        };

        assertThatThrownBy(() -> migration().migrate(source.content(), unreachable))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("Cible inaccessible : connexion refusee")
                .hasMessageContaining("rien n'a ete copie");
    }

    @Test
    void le_bilan_decrit_la_copie() {
        MigrationReport report = migration().migrate(source.content(), new InMemoryStorageBackend().content());

        assertThat(report.describe()).startsWith("2 banque(s), 4 ligne(s) de stock, ")
                .contains("3 livraison(s), 2 banque(s) active(s) copiees et verifiees");
    }

    private static StorageMigration migration() {
        return new StorageMigration(line -> { }, Clock.systemUTC());
    }

    /** Delegation complete, a specialiser par test. */
    private static class Wrapper implements StorageContent {

        private final StorageContent target;

        Wrapper(StorageContent target) {
            this.target = target;
        }

        @Override
        public boolean isEmpty() {
            return target.isEmpty();
        }

        @Override
        public void exportBanks(Consumer<Bank> sink) {
            target.exportBanks(sink);
        }

        @Override
        public void exportOperations(Consumer<OperationRecord> sink) {
            target.exportOperations(sink);
        }

        @Override
        public void exportDeliveries(Consumer<StoredDelivery> sink) {
            target.exportDeliveries(sink);
        }

        @Override
        public void exportActiveBanks(Consumer<ActiveBankChoice> sink) {
            target.exportActiveBanks(sink);
        }

        @Override
        public void importBanks(List<Bank> banks) {
            target.importBanks(banks);
        }

        @Override
        public void importOperations(List<OperationRecord> records) {
            target.importOperations(records);
        }

        @Override
        public void importDeliveries(List<StoredDelivery> deliveries) {
            target.importDeliveries(deliveries);
        }

        @Override
        public void importActiveBanks(List<ActiveBankChoice> choices) {
            target.importActiveBanks(choices);
        }
    }
}
