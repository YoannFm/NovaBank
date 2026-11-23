package dev.novadev.novabank.bukkit.integration;

import dev.novadev.novabank.core.text.ConsoleText;

/**
 * Pont reduit aux droits par permission ({@code novabank.<type>.deposit|withdraw}), pour un hote dont la commande est
 * montee par l'extension de son edition (SimpleClans : sous-commande ACF compilee contre lui).
 */
final class PermissionBridge implements HostBridge {

    @Override
    public Mount mount(Context context) {
        String prefix = "novabank." + context.entityType();
        return new Mount(() -> { }, new PermissionRights(context.entityType(), context.host().getServer()::getPlayer),
                ConsoleText.tr("permissions {0}.deposit|withdraw", prefix));
    }
}
