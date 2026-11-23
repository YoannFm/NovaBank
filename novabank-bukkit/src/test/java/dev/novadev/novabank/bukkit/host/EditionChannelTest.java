package dev.novadev.novabank.bukkit.host;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

/** Annonces entre JAR NovaBank : editions installees et moteur en marche. */
class EditionChannelTest {

    private final ServicesManager services = FakeServices.create();

    @Test
    void editions_et_moteur_annonces_puis_retires() throws Exception {
        Supplier<String> unrelated = () -> "autre plugin";
        services.register(Supplier.class, unrelated, null, ServicePriority.Normal);
        AutoCloseable ssb = EditionChannel.announceEdition(services, null, "superiorskyblock2", "1.1.0");
        AutoCloseable towny = EditionChannel.announceEdition(services, null, "towny", "1.1.0");

        assertThat(EditionChannel.editions(services)).containsExactly("superiorskyblock2", "towny");
        assertThat(EditionChannel.engineRunning(services)).isFalse();

        AutoCloseable engine = EditionChannel.announceEngine(services, null, "1.1.0");
        assertThat(EditionChannel.engineRunning(services)).isTrue();

        towny.close();
        engine.close();
        assertThat(EditionChannel.editions(services)).containsExactly("superiorskyblock2");
        assertThat(EditionChannel.engineRunning(services)).isFalse();
        ssb.close();
        assertThat(EditionChannel.editions(services)).isEmpty();
    }

    @Test
    void une_annonce_d_un_autre_jar_se_reconnait_a_son_texte() {
        Supplier<String> otherJar = new Supplier<>() {
            @Override
            public String get() {
                return toString();
            }

            @Override
            public String toString() {
                return "novabank:edition:lands:1.1.0";
            }
        };
        assertThat(EditionChannel.isAnnouncement(otherJar)).isTrue();
        assertThat(EditionChannel.isAnnouncement((Supplier<String>) () -> "x")).isFalse();
        assertThat(EditionChannel.isAnnouncement("novabank:edition:lands:1.1.0")).as("pas un Supplier").isFalse();
    }

    @Test
    void le_plugin_autonome_couvre_les_fournisseurs_de_tous_les_hotes_mais_pas_leurs_ponts() {
        assertThat(HostIntegrations.providerLicensed(Set.of("standalone"), "towny")).isTrue();
        assertThat(HostIntegrations.providerLicensed(Set.of("superiorskyblock2"), "superiorskyblock2")).isTrue();
        assertThat(HostIntegrations.providerLicensed(Set.of("superiorskyblock2"), "towny")).isFalse();
    }
}
