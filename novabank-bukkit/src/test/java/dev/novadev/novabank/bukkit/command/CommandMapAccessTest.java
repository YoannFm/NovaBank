package dev.novadev.novabank.bukkit.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.bukkit.testing.RecordingLogger;
import java.lang.reflect.Proxy;
import java.util.List;
import org.bukkit.Server;
import org.bukkit.command.CommandMap;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class CommandMapAccessTest {

    /** Ce que l'implementation du serveur expose (Paper : API ; Spigot : CraftServer). */
    public interface CommandMapHolder {
        CommandMap getCommandMap();
    }

    private final RecordingLogger log = new RecordingLogger();
    private final SimpleCommandMap map = new SimpleCommandMap(null);

    @Test
    void le_commandmap_est_obtenu_par_la_methode_publique_de_l_implementation() {
        Server server = (Server) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Server.class, CommandMapHolder.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getCommandMap")) {
                        return map;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        assertThat(CommandMapAccess.commandMap(server)).isSameAs(map);
    }

    @Test
    void un_serveur_sans_commandmap_est_signale() {
        Server server = (Server) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Server.class},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });

        assertThatThrownBy(() -> CommandMapAccess.commandMap(server)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CommandMap inaccessible");
    }

    @Test
    void inscription_puis_desinscription_complete_par_le_champ_spigot() {
        RoutedCommand command = new RoutedCommand("novabank", List.of("nb"), "Administration", "novabank.admin",
                plugin(), new CommandRouter() {
                    @Override
                    public void execute(CommandActor actor, String label, String[] args) {
                        // Non utilise.
                    }

                    @Override
                    public List<String> complete(CommandActor actor, String[] args) {
                        return List.of();
                    }
                });

        RegisteredCommand registered = RegisteredCommand.register(map, "novabank", command, log.logger());
        assertThat(map.getCommand("novabank")).isSameAs(command);
        assertThat(map.getCommand("novabank:novabank")).isSameAs(command);
        assertThat(map.getCommand("nb")).isSameAs(command);
        assertThat(command.isRegistered()).isTrue();
        assertThat(command.getPermission()).isEqualTo("novabank.admin");
        assertThat(command.getUsage()).isEqualTo("/novabank");

        registered.close();

        assertThat(map.getCommand("novabank")).isNull();
        assertThat(map.getCommand("novabank:novabank")).isNull();
        assertThat(map.getCommand("nb")).isNull();
        assertThat(command.isRegistered()).isFalse();
        assertThat(map.getCommand("version")).isNotNull();
    }

    private Plugin plugin() {
        return (Plugin) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Plugin.class},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
