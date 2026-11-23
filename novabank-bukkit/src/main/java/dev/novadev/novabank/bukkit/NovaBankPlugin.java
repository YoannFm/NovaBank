package dev.novadev.novabank.bukkit;

import dev.novadev.novabank.bukkit.host.NovaBankLauncher;
import dev.novadev.novabank.bukkit.lifecycle.PluginHost;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Point d'entree Bukkit, volontairement sans logique : plugin autonome et extensions sous forme de plugin (Towny,
 * Lands...), qui ne different que par leur {@code plugin.yml} et leur edition. Tout est delegue a
 * {@link NovaBankLauncher} (un seul moteur par serveur).
 */
public final class NovaBankPlugin extends JavaPlugin {

    private NovaBankLauncher launcher;

    @Override
    public void onLoad() {
        NovaBankLauncher.load(PluginHost.of(this));
    }

    @Override
    public void onEnable() {
        launcher = NovaBankLauncher.start(PluginHost.of(this), true);
        if (launcher == null) {
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (launcher != null) {
            launcher.close();
            launcher = null;
        }
    }
}
