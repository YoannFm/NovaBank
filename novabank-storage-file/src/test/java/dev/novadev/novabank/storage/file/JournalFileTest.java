package dev.novadev.novabank.storage.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.storage.ActiveBankChoice;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Journal d'ecriture : derniere ligne tronquee ignoree et signalee, corruption au milieu refusee,
 * sequence controlee.
 */
class JournalFileTest {

    @TempDir
    Path directory;

    @Test
    void une_derniere_ligne_tronquee_est_ignoree_conservee_a_part_et_signalee() throws IOException {
        UUID kept = UUID.randomUUID();
        Path journal;
        try (FileStorage storage = FileTestSupport.open(directory, FileFormat.JSON, Long.MAX_VALUE)) {
            storage.activeBanks().save(kept, new EntityRef(EntityRef.ISLAND, "gardee"));
            journal = storage.journalFile();
            String torn = JournalFile.line(2, Commit.of(new ActiveBankChoice(UUID.randomUUID(),
                    new EntityRef(EntityRef.ISLAND, "perdue"))));
            Files.writeString(journal, torn.substring(0, torn.length() / 2), StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);
            copyWithoutLock(directory, directory.resolve("copie"));
        }
        StringBuilder warnings = new StringBuilder();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.append(record.getMessage()).append('\n');
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        FileTestSupport.LOGGER.addHandler(capture);
        try (FileStorage reopened = FileTestSupport.open(directory.resolve("copie"), FileFormat.JSON)) {
            assertThat(reopened.activeBanks().find(kept)).isPresent();
            reopened.activeBanks().save(UUID.randomUUID(), new EntityRef(EntityRef.ISLAND, "apres"));
        } finally {
            FileTestSupport.LOGGER.removeHandler(capture);
        }
        assertThat(warnings.toString()).contains("derniere ligne du journal tronquee").contains("conserve(s) dans");
        try (var files = Files.list(directory.resolve("copie"))) {
            assertThat(files.map(file -> file.getFileName().toString()))
                    .anyMatch(name -> name.startsWith("novabank.json.journal.torn-"));
        }
        try (FileStorage again = FileTestSupport.open(directory.resolve("copie"), FileFormat.JSON)) {
            assertThat(again.content().isEmpty()).isFalse();
        }
    }

    @Test
    void une_derniere_ligne_au_crc_faux_est_traitee_comme_tronquee() throws IOException {
        Path data = directory.resolve("crc");
        Files.createDirectories(data);
        String good = JournalFile.line(1, Commit.of(new ActiveBankChoice(UUID.randomUUID(),
                new EntityRef(EntityRef.ISLAND, "a"))));
        String bad = JournalFile.line(2, Commit.of(new ActiveBankChoice(UUID.randomUUID(),
                new EntityRef(EntityRef.ISLAND, "b")))).replace("\"b\"", "\"c\"");
        Files.writeString(data.resolve("novabank.json.journal"), good + bad, StandardCharsets.UTF_8);

        try (FileStorage storage = FileTestSupport.open(data, FileFormat.JSON)) {
            assertThat(storage.content().isEmpty()).isFalse();
            assertThat(storage.journalFile()).isEmptyFile();
        }
    }

    @Test
    void une_ligne_invalide_suivie_d_autres_lignes_est_une_corruption_et_rien_n_est_modifie() throws IOException {
        Path data = directory.resolve("corrompu");
        Files.createDirectories(data);
        String first = JournalFile.line(1, Commit.of(new ActiveBankChoice(UUID.randomUUID(),
                new EntityRef(EntityRef.ISLAND, "a"))));
        String second = JournalFile.line(2, Commit.of(new ActiveBankChoice(UUID.randomUUID(),
                new EntityRef(EntityRef.ISLAND, "b"))));
        String content = first.replace("\"a\"", "\"z\"") + second;
        Path journal = data.resolve("novabank.yml.journal");
        Files.writeString(journal, content, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> FileTestSupport.open(data, FileFormat.YAML))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("ligne 1 invalide suivie d'autres lignes")
                .hasMessageContaining("aucune donnee modifiee");
        assertThat(Files.readString(journal, StandardCharsets.UTF_8)).isEqualTo(content);
    }

    @Test
    void une_sequence_non_continue_est_une_corruption() throws IOException {
        Path data = directory.resolve("trou");
        Files.createDirectories(data);
        Files.writeString(data.resolve("novabank.json.journal"),
                JournalFile.line(1, Commit.of(new ActiveBankChoice(UUID.randomUUID(), new EntityRef("a", "1"))))
                        + JournalFile.line(3, Commit.of(new ActiveBankChoice(UUID.randomUUID(), new EntityRef("a", "2")))),
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> FileTestSupport.open(data, FileFormat.JSON)).isInstanceOf(StorageException.class)
                .hasMessageContaining("non continue");
    }

    @Test
    void un_instantane_d_une_version_de_format_future_est_refuse() throws IOException {
        Path data = directory.resolve("futur");
        Files.createDirectories(data);
        Files.writeString(data.resolve("novabank.json"),
                "{\"format\": \"novabank-file\", \"format-version\": \"2\", \"sequence\": \"0\"}");

        assertThatThrownBy(() -> FileTestSupport.open(data, FileFormat.JSON)).isInstanceOf(StorageException.class)
                .hasMessageContaining("mettez NovaBank a jour");
    }

    @Test
    void les_lignes_deja_incluses_dans_l_instantane_ne_sont_pas_rejouees_deux_fois() throws IOException {
        Path data = directory.resolve("double");
        UUID player = UUID.randomUUID();
        try (FileStorage storage = FileTestSupport.open(data, FileFormat.YAML, Long.MAX_VALUE)) {
            storage.activeBanks().save(player, new EntityRef("a", "1"));
            storage.activeBanks().save(player, new EntityRef("a", "2"));
        }
        // Arret brutal simule entre l'instantane et la remise a zero du journal : on remet les
        // anciennes lignes (sequences 1 et 2, deja incluses dans l'instantane de sequence 2).
        Files.writeString(data.resolve("novabank.yml.journal"),
                JournalFile.line(1, Commit.of(new ActiveBankChoice(player, new EntityRef("a", "1"))))
                        + JournalFile.line(2, Commit.of(new ActiveBankChoice(player, new EntityRef("a", "2")))),
                StandardCharsets.UTF_8);

        try (FileStorage storage = FileTestSupport.open(data, FileFormat.YAML)) {
            assertThat(storage.activeBanks().find(player)).contains(new EntityRef("a", "2"));
            storage.activeBanks().save(player, new EntityRef("a", "3"));
        }
        try (FileStorage storage = FileTestSupport.open(data, FileFormat.YAML)) {
            assertThat(storage.activeBanks().find(player)).contains(new EntityRef("a", "3"));
        }
    }

    private static void copyWithoutLock(Path from, Path to) throws IOException {
        Files.createDirectories(to);
        try (var files = Files.list(from)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (!file.getFileName().toString().endsWith(".lock")) {
                    Files.copy(file, to.resolve(file.getFileName()));
                }
            }
        }
    }
}
