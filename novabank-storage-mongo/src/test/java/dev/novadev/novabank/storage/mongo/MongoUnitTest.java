package dev.novadev.novabank.storage.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.WriteConcern;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.bank.StockLine;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.journal.OperationState;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.storage.BackendParameters;
import dev.novadev.novabank.core.testing.TestBankIds;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * Tests sans serveur : reglages, conversions de documents, nature du serveur, serveur injoignable.
 */
class MongoUnitTest {

    private static final String SECRET_URI = "mongodb://novabank:Tres-Secret-42@127.0.0.1:9/?authSource=admin";
    private static final Instant AT = Instant.parse("2026-10-27T10:11:12.987654321Z");

    @Test
    void les_reglages_sont_lus_et_valides() {
        MongoConnectionSettings settings = MongoConnectionSettings.from(parameters(SECRET_URI, "novabank"));

        assertThat(settings.database()).isEqualTo("novabank");
        assertThat(settings.connectTimeoutMs()).isEqualTo(500);
        assertThat(settings.describe()).isEqualTo("127.0.0.1:9/novabank").doesNotContain("Secret");
        assertThat(settings.toString()).doesNotContain("Secret");
    }

    @Test
    void une_uri_invalide_est_refusee_sans_etre_recopiee() {
        assertThatThrownBy(() -> MongoConnectionSettings.from(parameters("http://user:Tres-Secret-42@h", "novabank")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URI MongoDB invalide").hasMessageNotContaining("Secret");
        assertThatThrownBy(() -> MongoConnectionSettings.from(parameters(SECRET_URI, "nova.bank")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void un_serveur_injoignable_donne_une_storage_exception_sans_secret() {
        long start = System.nanoTime();
        assertThatThrownBy(() -> new MongoBackendFactory().open(parameters(SECRET_URI, "novabank")))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("127.0.0.1:9/novabank")
                .hasMessageNotContaining("Tres-Secret-42");
        assertThat((System.nanoTime() - start) / 1_000_000L).as("delai de selection respecte").isLessThan(10_000L);
    }

    @Test
    void les_masques_couvrent_uri_et_mot_de_passe() {
        MongoFailures failures = new MongoFailures(MongoConnectionSettings.from(parameters(SECRET_URI, "novabank")));

        assertThat(failures.mask("a " + SECRET_URI + " b Tres-Secret-42")).isEqualTo("a **** b ****");
    }

    @Test
    void banque_et_journal_sont_convertis_a_l_identique() {
        BankDocuments documents = new BankDocuments(Clock.systemUTC());
        Bank bank = new Bank(TestBankIds.random(), new EntityRef("faction", "rouge"), ValuationMode.DERIVED, 7L, 2L,
                Map.of(ItemKey.minecraft("stone"), new StockLine(10L, 4L, new BigDecimal("0.0001")),
                        ItemKey.parse("nexo:ruby_block"), new StockLine(0L, 3L, BigDecimal.ZERO)));
        assertThat(documents.fromDocument(documents.toDocument(bank))).isEqualTo(bank);

        OperationRecord reserved = OperationRecord.reservation(OperationId.random(), bank.id(), ItemKey.minecraft("stone"),
                5L, 2L, new BigDecimal("-1.5"), AT, UUID.randomUUID(), 2L);
        OperationRecord settled = reserved.settle(OperationState.COMMITTED, 3L, 1L, new BigDecimal("0.25"));
        OperationRecord reset = OperationRecord.reset(OperationId.random(), bank.id(), 10L, 6L, new BigDecimal("-9"), AT);
        for (OperationRecord record : new OperationRecord[] {reserved, settled, reset}) {
            assertThat(OperationDocuments.fromDocument(OperationDocuments.toDocument(record, 1L))).isEqualTo(record);
        }
    }

    @Test
    void les_transactions_exigent_un_replica_set_ou_un_routeur_avec_sessions() {
        assertThat(new ServerTopology("8.3.13", "", false, true).supportsTransactions()).isFalse();
        assertThat(new ServerTopology("8.3.13", "rs0", false, true).supportsTransactions()).isTrue();
        assertThat(new ServerTopology("8.3.13", "", true, true).supportsTransactions()).isTrue();
        assertThat(new ServerTopology("8.3.13", "rs0", false, false).supportsTransactions()).isFalse();
        assertThat(new ServerTopology("8.3.13", "rs0", false, true).describe()).contains("replica set rs0")
                .contains("transactions multi-documents");
        assertThat(new ServerTopology("8.3.13", "", false, true).describe()).contains("serveur autonome")
                .contains("sans transaction");
        assertThat(new ServerTopology("8.3.13", "", false, true).durableWriteConcern())
                .as("autonome : w 1 journalise (w majority n'y attend pas le journal)").isEqualTo(WriteConcern.JOURNALED);
        assertThat(new ServerTopology("8.3.13", "rs0", false, true).durableWriteConcern())
                .isEqualTo(WriteConcern.MAJORITY.withJournal(true));
    }

    private static BackendParameters parameters(String uri, String database) {
        return BackendParameters.builder(Clock.systemUTC(), Logger.getAnonymousLogger())
                .putSecret(MongoConnectionSettings.URI, uri)
                .put(MongoConnectionSettings.DATABASE, database)
                .put(MongoConnectionSettings.CONNECT_TIMEOUT_MS, 500)
                .put(MongoConnectionSettings.SERVER_SELECTION_TIMEOUT_MS, 1_000)
                .put(MongoConnectionSettings.SOCKET_TIMEOUT_MS, 1_000)
                .build();
    }
}
