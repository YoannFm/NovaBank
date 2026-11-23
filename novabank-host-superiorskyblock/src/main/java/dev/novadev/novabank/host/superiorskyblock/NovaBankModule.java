package dev.novadev.novabank.host.superiorskyblock;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.commands.SuperiorCommand;
import com.bgsoftware.superiorskyblock.api.island.IslandPrivilege;
import com.bgsoftware.superiorskyblock.api.modules.PluginModule;
import dev.novadev.novabank.bukkit.host.EmbeddedHost;
import dev.novadev.novabank.bukkit.host.NovaBankLauncher;
import dev.novadev.novabank.core.text.ConsoleText;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * NovaBank pour SuperiorSkyblock2 : module a deposer dans {@code plugins/SuperiorSkyblock2/modules/},
 * configuration et donnees dans {@code modules/NovaBank/}. Cette classe doit rester la premiere du JAR
 * (SuperiorSkyblock2 cherche le module classe par classe et s'arrete a la premiere classe illisible). La commande {@code /is bank} et les privileges sont montes par le moteur ; ici, seulement le
 * demarrage et l'enregistrement precoce des privileges.
 */
public final class NovaBankModule extends PluginModule {

    private NovaBankLauncher launcher;

    /** Module « NovaBank » de YoannFM. */
    public NovaBankModule() {
        super("NovaBank", "YoannFM");
    }

    /**
     * Privileges enregistres des le chargement du module, avant la lecture des iles : leurs reglages sauvegardes
     * sont ainsi relus.
     */
    @Override
    protected void onPluginInit(SuperiorSkyblock plugin) {
        for (String name : new String[] {"NOVABANK_DEPOSIT", "NOVABANK_WITHDRAW"}) {
            try {
                IslandPrivilege.getByName(name);
            } catch (NullPointerException unknown) {
                IslandPrivilege.register(name);
            }
        }
    }

    @Override
    public void onEnable(SuperiorSkyblock plugin) {
        Plugin owner = Bukkit.getPluginManager().getPlugin("SuperiorSkyblock2");
        launcher = NovaBankLauncher.start(EmbeddedHost.of(owner, getModuleFolder().toPath(), getLogger(),
                this::getResource, getClass().getClassLoader()), false);
        if (launcher == null) {
            getLogger().severe(ConsoleText.tr("NovaBank n'a pas demarre : voir les messages ci-dessus."));
        }
    }

    @Override
    public void onReload(SuperiorSkyblock plugin) {
        // NovaBank a son propre rechargement : /novabank reload.
    }

    @Override
    public void onDisable(SuperiorSkyblock plugin) {
        if (launcher != null) {
            launcher.close();
            launcher = null;
        }
    }

    @Override
    public Listener[] getModuleListeners(SuperiorSkyblock plugin) {
        return new Listener[0];
    }

    @Override
    public SuperiorCommand[] getSuperiorCommands(SuperiorSkyblock plugin) {
        return new SuperiorCommand[0];
    }

    @Override
    public SuperiorCommand[] getSuperiorAdminCommands(SuperiorSkyblock plugin) {
        return new SuperiorCommand[0];
    }
}
