package dev.novadev.novabank.bukkit.integration;

/**
 * Pont d'un hote sans sous-commande possible (Lands, GriefPrevention, WorldGuard) : commande de banque propre
 * ({@code /landbank}, nom de {@code commands.hosts.<edition>}). Les droits viennent de l'extension de l'edition s'il y en
 * a une (flags Lands, WorldGuard), sinon de la regle banks.roles.
 */
final class TopLevelBridge implements HostBridge {

    @Override
    public Mount mount(Context context) {
        AutoCloseable command = context.topLevel().register(context.label(), context.commands());
        return new Mount(command, Rights.NONE, "/" + context.label().name());
    }
}
