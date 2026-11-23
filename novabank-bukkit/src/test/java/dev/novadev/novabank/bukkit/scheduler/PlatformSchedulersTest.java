package dev.novadev.novabank.bukkit.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.bukkit.platform.ServerPlatform;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class PlatformSchedulersTest {

    private final List<String> requested = new CopyOnWriteArrayList<>();
    private final ClassLoader recording = new ClassLoader(getClass().getClassLoader()) {
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            requested.add(name);
            return super.loadClass(name, resolve);
        }
    };

    @Test
    void hors_folia_planificateur_bukkit_sans_charger_l_implementation_folia() {
        for (ServerPlatform platform : List.of(ServerPlatform.PAPER, ServerPlatform.SPIGOT, ServerPlatform.BUKKIT)) {
            assertThat(PlatformSchedulers.forPlatform(platform, recording)).isNotNull();
        }

        assertThat(requested).noneMatch(name -> name.contains("folia"));
    }

    @Test
    void sous_folia_l_implementation_est_chargee_par_son_nom() {
        SchedulerFactory factory = PlatformSchedulers.forPlatform(ServerPlatform.FOLIA,
                StubSchedulerFactory.class.getName(), recording);

        assertThat(factory).isInstanceOf(StubSchedulerFactory.class);
        assertThat(requested).contains(StubSchedulerFactory.class.getName());
    }

    @Test
    void le_nom_de_l_implementation_folia_est_celui_du_module_folia() {
        assertThat(PlatformSchedulers.FOLIA_FACTORY)
                .isEqualTo("dev.novadev.novabank.platform.folia.FoliaSchedulerFactory");
    }

    @Test
    void implementation_folia_absente_ou_invalide_refusee_avec_un_message_clair() {
        assertThatThrownBy(() -> PlatformSchedulers.forPlatform(ServerPlatform.FOLIA, "absent.Factory", recording))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("absent.Factory");
        assertThatThrownBy(() -> PlatformSchedulers.forPlatform(ServerPlatform.FOLIA, String.class.getName(),
                recording))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("java.lang.String");
    }

    /** Implementation chargee par son nom dans les tests. */
    public static final class StubSchedulerFactory implements SchedulerFactory {

        @Override
        public PluginScheduler create(Plugin plugin, IoExecutor io, Logger logger) {
            throw new UnsupportedOperationException("non utilise");
        }
    }
}
