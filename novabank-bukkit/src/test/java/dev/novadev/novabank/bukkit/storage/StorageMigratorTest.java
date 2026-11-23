package dev.novadev.novabank.bukkit.storage;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.bukkit.config.BukkitItemAvailability;
import dev.novadev.novabank.bukkit.config.YamlTrees;
import dev.novadev.novabank.bukkit.lifecycle.Lifecycle;
import dev.novadev.novabank.bukkit.lifecycle.StartupException;
import dev.novadev.novabank.bukkit.testing.TestConfigs;
import dev.novadev.novabank.config.ConfigLoadResult;
import dev.novadev.novabank.config.NovaBankConfigLoader;
import dev.novadev.novabank.config.model.StorageSettings;
import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.core.application.FlowDrain;
import dev.novadev.novabank.core.migration.ContentSummary;
import dev.novadev.novabank.core.storage.FreezableBackend;
import dev.novadev.novabank.core.testing.ContentFixture;
import dev.novadev.novabank.core.testing.InMemoryStorageBackend;
import dev.novadev.novabank.core.testing.ManualScheduler;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Orchestration de la migration : maintenance, copie verifiee, maintien de la maintenance apres
 * succes, levee apres echec sans modification de la source, refus prealables.
 */
class StorageMigratorTest {

    private final InMemoryStorageBackend sourceBackend = new InMemoryStorageBackend();
    private final InMemoryStorageBackend targetBackend = new InMemoryStorageBackend();
    private final FreezableBackend writes = new FreezableBackend(sourceBackend);
    private final FlowDrain drain = new FlowDrain();
    private final ManualScheduler scheduler = new ManualScheduler();
    private final List<StorageSettings> opened = new ArrayList<>();
    private final StorageMigrator migrator = new StorageMigrator(
            new ActiveStorage(StorageType.SQLITE, sourceBackend, new StorageDriver("sqlite", "1", "test"), "source.db"),
            writes, drain, this::openTarget, scheduler, () -> Duration.ofSeconds(1), Clock.systemUTC(),
            Logger.getAnonymousLogger());

    StorageMigratorTest() {
        ContentFixture.populate(sourceBackend);
    }

    private ActiveStorage openTarget(StorageSettings settings, Lifecycle resources) {
        opened.add(settings);
        return new ActiveStorage(settings.type(), targetBackend, new StorageDriver("json", "1", "test"), "cible");
    }

    @Test
    void succes_copie_verifiee_maintenance_maintenue_et_seconde_migration_refusee() throws Exception {
        List<StorageMigrator.Outcome> outcomes = new ArrayList<>();

        assertThat(migrator.start(StorageType.JSON, settings(), outcomes::add)).isTrue();
        assertThat(drain.isPaused()).isTrue();
        scheduler.runAll();

        assertThat(outcomes).singleElement().satisfies(outcome -> {
            assertThat(outcome.success()).isTrue();
            assertThat(outcome.detail()).contains("copiees et verifiees");
        });
        assertThat(opened).singleElement().satisfies(target -> assertThat(target.type()).isEqualTo(StorageType.JSON));
        ContentFixture.assertSameContent(sourceBackend.content(), targetBackend.content());
        assertThat(drain.isPaused()).as("maintenance jusqu'au redemarrage").isTrue();
        assertThat(writes.isFrozen()).isTrue();
        assertThat(migrator.start(StorageType.YAML, settings(), outcomes::add)).isFalse();
    }

    @Test
    void echec_cible_non_vide_source_inchangee_et_maintenance_levee() throws Exception {
        targetBackend.activeBanks().save(UUID.randomUUID(), new EntityRef("island", "occupee"));
        ContentSummary before = ContentSummary.of(sourceBackend.content());
        List<StorageMigrator.Outcome> outcomes = new ArrayList<>();

        migrator.start(StorageType.JSON, settings(), outcomes::add);
        scheduler.runAll();

        assertThat(outcomes).singleElement().satisfies(outcome -> {
            assertThat(outcome.success()).isFalse();
            assertThat(outcome.detail()).contains("n'est pas vide");
        });
        assertThat(before.differences(ContentSummary.of(sourceBackend.content()))).isEmpty();
        assertThat(drain.isPaused()).isFalse();
        assertThat(writes.isFrozen()).isFalse();
        assertThat(drain.tryStart()).isTrue();
    }

    @Test
    void echec_a_l_ouverture_de_la_cible() {
        StorageMigrator failing = new StorageMigrator(new ActiveStorage(StorageType.SQLITE, sourceBackend,
                new StorageDriver("sqlite", "1", "test"), "source.db"), writes, drain, (settings, resources) -> {
                    throw new StartupException("serveur injoignable");
                }, scheduler, () -> Duration.ofSeconds(1), Clock.systemUTC(), Logger.getAnonymousLogger());
        List<StorageMigrator.Outcome> outcomes = new ArrayList<>();

        failing.start(StorageType.MARIADB, settings(), outcomes::add);
        scheduler.runAll();

        assertThat(outcomes).singleElement().satisfies(outcome ->
                assertThat(outcome.detail()).contains("cible indisponible : serveur injoignable"));
        assertThat(drain.isPaused()).isFalse();
    }

    @Test
    void un_flux_qui_ne_se_termine_pas_fait_echouer_la_migration_sans_rien_copier() {
        assertThat(drain.tryStart()).isTrue();
        List<StorageMigrator.Outcome> outcomes = new ArrayList<>();

        migrator.start(StorageType.JSON, settings(), outcomes::add);
        scheduler.runAll();

        assertThat(outcomes).singleElement().satisfies(outcome ->
                assertThat(outcome.detail()).contains("1 operation(s) joueur toujours en cours"));
        assertThat(opened).isEmpty();
        assertThat(targetBackend.content().isEmpty()).isTrue();
    }

    @Test
    void refus_prealables() throws Exception {
        assertThat(migrator.refusal(StorageType.SQLITE, settings(), false)).contains(StorageMigrator.Refusal.SAME_TYPE);
        assertThat(migrator.refusal(StorageType.JSON, settings(), true)).contains(StorageMigrator.Refusal.REDIS_ENABLED);
        assertThat(migrator.refusal(StorageType.MONGODB, settings(), true)).isEmpty();
        assertThat(migrator.refusal(StorageType.MARIADB, settings(), false)).isEmpty();
        StorageSettings noUser = settings("username: novabank", "username: \"\"");
        assertThat(migrator.refusal(StorageType.MYSQL, noUser, false))
                .contains(StorageMigrator.Refusal.SQL_USERNAME_MISSING);
    }

    private static StorageSettings settings() {
        return settings("type: SQLITE", "type: SQLITE");
    }

    private static StorageSettings settings(String target, String value) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(TestConfigs.replaceOnce(TestConfigs.defaultYaml(), target, value));
            ConfigLoadResult result = new NovaBankConfigLoader(new BukkitItemAvailability(id -> true, name -> true))
                    .load(YamlTrees.toMap(yaml));
            return result.config().orElseThrow(() -> new AssertionError(result.report().issues().toString())).storage();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
