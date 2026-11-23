package dev.novadev.novabank.core.architecture.fixture;

import io.papermc.novabanktest.PaperProbe;
import net.md_5.novabanktest.BungeeProbe;
import org.bukkit.novabanktest.BukkitProbe;
import org.spigotmc.novabanktest.SpigotProbe;

/**
 * Classe volontairement fautive : elle depend d'un type de chaque famille de
 * packages interdite dans le coeur. Sert uniquement a prouver que
 * {@code CoreArchitectureTest} detecte ces violations.
 */
public final class PlatformDependentFixture {

    BukkitProbe bukkit;
    PaperProbe paper;
    SpigotProbe spigot;
    BungeeProbe bungee;
}
