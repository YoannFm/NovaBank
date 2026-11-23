package dev.novadev.novabank.libraries.archfixture;

import com.mysql.novabanktest.MySqlProbe;
import io.papermc.novabanktest.PaperProbe;
import net.md_5.novabanktest.BungeeProbe;
import org.bukkit.novabanktest.BukkitProbe;
import org.mariadb.novabanktest.MariaDbProbe;
import org.spigotmc.novabanktest.SpigotProbe;

/**
 * Classe volontairement fautive : prouve que la regle d'architecture du module detecte
 * chaque dependance interdite.
 */
public final class ForbiddenDependenciesFixture {

    BukkitProbe bukkit;
    PaperProbe paper;
    SpigotProbe spigot;
    BungeeProbe bungee;
    org.sqlite.JDBC sqliteDriver;
    MariaDbProbe mariadb;
    MySqlProbe mysql;
}
