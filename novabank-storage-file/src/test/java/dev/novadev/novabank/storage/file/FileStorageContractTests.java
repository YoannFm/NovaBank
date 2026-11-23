package dev.novadev.novabank.storage.file;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.core.delivery.PendingDeliveryStore;
import dev.novadev.novabank.core.migration.StorageMigration;
import dev.novadev.novabank.core.delivery.PendingDeliveryStoreContractTest;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.repository.BankRepositoryContractTest;
import dev.novadev.novabank.core.selection.ActiveBankStore;
import dev.novadev.novabank.core.selection.ActiveBankStoreContractTest;
import dev.novadev.novabank.core.storage.StorageBackend;
import dev.novadev.novabank.core.storage.StorageContentContractTest;
import dev.novadev.novabank.core.testing.ContentFixture;
import dev.novadev.novabank.core.testing.ContentInvariants;
import dev.novadev.novabank.core.testing.InMemoryStorageBackend;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrats complets du coeur (banques, journal, livraisons, banques actives, contenu) sur les deux
 * formats, plus la relecture apres fermeture et apres un "arret brutal" (copie des fichiers
 * pendant que le stockage est ouvert : seuls le journal synchronise et l'instantane comptent).
 * Execute avec les bibliotheques de 1.17.1 puis avec les recentes.
 */
class FileStorageContractTests {

    @TempDir
    Path directory;

    private final List<FileStorage> opened = new ArrayList<>();

    @AfterEach
    void closeStorages() {
        opened.forEach(FileStorage::close);
    }

    private FileStorage open(FileFormat format) {
        FileStorage storage = FileTestSupport.open(directory.resolve(UUID.randomUUID().toString()), format);
        opened.add(storage);
        return storage;
    }

    @Nested
    class JsonBanks extends BankRepositoryContractTest {
        @Override
        protected BankRepository newRepository() {
            return open(FileFormat.JSON).banks();
        }
    }

    @Nested
    class YamlBanks extends BankRepositoryContractTest {
        @Override
        protected BankRepository newRepository() {
            return open(FileFormat.YAML).banks();
        }
    }

    @Nested
    class JsonDeliveries extends PendingDeliveryStoreContractTest {
        @Override
        protected PendingDeliveryStore newStore() {
            return open(FileFormat.JSON).deliveries();
        }
    }

    @Nested
    class YamlDeliveries extends PendingDeliveryStoreContractTest {
        @Override
        protected PendingDeliveryStore newStore() {
            return open(FileFormat.YAML).deliveries();
        }
    }

    @Nested
    class JsonActiveBanks extends ActiveBankStoreContractTest {
        @Override
        protected ActiveBankStore newStore() {
            return open(FileFormat.JSON).activeBanks();
        }
    }

    @Nested
    class YamlActiveBanks extends ActiveBankStoreContractTest {
        @Override
        protected ActiveBankStore newStore() {
            return open(FileFormat.YAML).activeBanks();
        }
    }

    @Nested
    class JsonContent extends StorageContentContractTest {
        @Override
        protected StorageBackend newBackend() {
            return open(FileFormat.JSON);
        }
    }

    @Nested
    class YamlContent extends StorageContentContractTest {
        @Override
        protected StorageBackend newBackend() {
            return open(FileFormat.YAML);
        }
    }

    @Test
    void le_contenu_est_relu_a_l_identique_apres_fermeture_en_json_et_en_yaml() {
        for (FileFormat format : FileFormat.values()) {
            Path data = directory.resolve("reopen-" + format);
            InMemoryStorageBackend expected = new InMemoryStorageBackend();
            try (FileStorage storage = FileTestSupport.open(data, format)) {
                ContentFixture.populate(storage);
                new StorageMigration(line -> { }, Clock.systemUTC())
                        .migrate(storage.content(), expected.content());
            }
            assertThat(data.resolve("novabank." + (format == FileFormat.JSON ? "json" : "yml"))).isRegularFile();
            try (FileStorage reopened = FileTestSupport.open(data, format)) {
                assertThat(reopened.journalFile()).isEmptyFile();
                ContentFixture.assertSameContent(expected.content(), reopened.content());
                assertThat(ContentInvariants.violations(reopened.content())).isEmpty();
            }
        }
    }

    @Test
    void sans_fermeture_le_journal_synchronise_suffit_a_tout_retrouver() throws IOException {
        for (FileFormat format : FileFormat.values()) {
            Path live = directory.resolve("live-" + format);
            Path crashed = directory.resolve("crashed-" + format);
            FileStorage storage = FileTestSupport.open(live, format);
            opened.add(storage);
            ContentFixture.populate(storage);
            assertThat(Files.size(storage.journalFile())).isPositive();
            Files.createDirectories(crashed);
            try (var files = Files.list(live)) {
                for (Path file : files.toList()) {
                    if (!file.getFileName().toString().endsWith(".lock")) {
                        Files.copy(file, crashed.resolve(file.getFileName()));
                    }
                }
            }

            try (FileStorage recovered = FileTestSupport.open(crashed, format)) {
                ContentFixture.assertSameContent(storage.content(), recovered.content());
            }
        }
    }
}
