package dev.novadev.novabank.storage.mongo.archfixture;

import io.papermc.novabanktest.PaperProbe;
import net.md_5.novabanktest.BungeeProbe;
import org.bukkit.novabanktest.BukkitProbe;
import org.spigotmc.novabanktest.SpigotProbe;

/**
 * Classe de test qui viole volontairement la regle d'architecture du module MongoDB.
 */
public final class ForbiddenDependenciesFixture {

    BukkitProbe bukkit;
    PaperProbe paper;
    SpigotProbe spigot;
    BungeeProbe bungee;
    java.sql.Connection jdbc;
}
