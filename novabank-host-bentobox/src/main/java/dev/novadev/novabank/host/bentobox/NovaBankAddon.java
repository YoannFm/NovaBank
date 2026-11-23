package dev.novadev.novabank.host.bentobox;

import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.config.model.CommandLabel;
import dev.novadev.novabank.bukkit.host.EmbeddedHost;
import dev.novadev.novabank.bukkit.host.HostLabels;
import dev.novadev.novabank.bukkit.host.NovaBankLauncher;
import dev.novadev.novabank.bukkit.host.RemoteBridges;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Material;
import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.flags.Flag;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.managers.RanksManager;

/**
 * NovaBank pour BentoBox : addon a deposer dans {@code plugins/BentoBox/addons/}. Demarre le moteur (ou rejoint celui
 * deja en marche), ajoute {@code /<mode> <nom>} a chaque mode de jeu (BSkyBlock {@code /is bank}, AOneBlock
 * {@code /ob bank}...) et les flags {@code NOVABANK_DEPOSIT} et {@code NOVABANK_WITHDRAW}, reglables par rang dans
 * les parametres de l'ile. Les commandes passent par le moteur, ou qu'il tourne ({@link RemoteBridges}).
 */
public final class NovaBankAddon extends Addon {

    static final String EDITION = "bentobox";

    private NovaBankLauncher launcher;
    private Flag deposit;
    private Flag withdraw;
    private final List<AutoCloseable> mounted = new ArrayList<>();

    @Override
    public void onLoad() {
        // Flags enregistres tot : les reglages sauvegardes des iles sont relus.
        deposit = new Flag.Builder("NOVABANK_DEPOSIT", Material.CHEST).type(Flag.Type.PROTECTION)
                .defaultRank(RanksManager.MEMBER_RANK).addon(this).build();
        withdraw = new Flag.Builder("NOVABANK_WITHDRAW", Material.HOPPER).type(Flag.Type.PROTECTION)
                .defaultRank(RanksManager.SUB_OWNER_RANK).addon(this).build();
        registerFlag(deposit);
        registerFlag(withdraw);
    }

    @Override
    public void onEnable() {
        launcher = NovaBankLauncher.start(EmbeddedHost.of(getPlugin(), getLogger(), this::getResource,
                getClass().getClassLoader()), false);
        if (launcher == null) {
            logError(ConsoleText.tr("NovaBank n'a pas demarre : voir les messages ci-dessus."));
            setState(State.DISABLED);
            return;
        }
        mounted.add(RemoteBridges.publishRights(getServer().getServicesManager(), getPlugin(), EDITION,
                this::allows));
        CommandLabel configured = HostLabels.read(getPlugin(), EDITION);
        List<String> names = new ArrayList<>(List.of(configured.name()));
        names.addAll(configured.aliases());
        String[] label = names.toArray(String[]::new);
        for (GameModeAddon mode : getPlugin().getAddonsManager().getGameModeAddons()) {
            mode.getPlayerCommand().ifPresent(parent -> {
                if (parent.getSubCommands().containsKey(label[0])) {
                    logWarning(
                            ConsoleText.tr("/{0} {1} existe deja (addon Bank ?) : changez commands.hosts.bentobox.name dans plugins/NovaBank/config.yml.",
                                    parent.getLabel(), label[0]));
                } else {
                    mounted.add(BankSubcommand.mount(this, parent, label));
                }
            });
        }
    }

    @Override
    public void onDisable() {
        mounted.forEach(unmount -> {
            try {
                unmount.close();
            } catch (Exception failure) {
                logWarning(ConsoleText.tr("Retrait de NovaBank incomplet : {0}", failure));
            }
        });
        mounted.clear();
        if (launcher != null) {
            launcher.close();
            launcher = null;
        }
    }

    /** Requete {@code "<joueur>;<ile>;DEPOSIT|WITHDRAW"} : decision du flag de l'ile pour ce joueur. */
    private Optional<Boolean> allows(String query) {
        String[] parts = query.split(";", 3);
        if (parts.length != 3) {
            return Optional.empty();
        }
        Flag flag = OperationType.DEPOSIT.name().equals(parts[2]) ? deposit : withdraw;
        User user = User.getInstance(UUID.fromString(parts[0]));
        return BentoBox.getInstance().getIslands().getIslandById(parts[1])
                .map(island -> island.isAllowed(user, flag));
    }

    /** Sous-commande montee sous la commande joueur d'un mode de jeu. */
    static final class BankSubcommand extends CompositeCommand {

        private BankSubcommand(Addon addon, CompositeCommand parent, String label, String... aliases) {
            super(addon, parent, label, aliases);
        }

        static AutoCloseable mount(Addon addon, CompositeCommand parent, String[] label) {
            String[] aliases = java.util.Arrays.copyOfRange(label, 1, label.length);
            BankSubcommand command = new BankSubcommand(addon, parent, label[0], aliases);
            return () -> {
                parent.getSubCommands().values().remove(command);
                parent.getSubCommandAliases().values().removeIf(found -> found == command);
            };
        }

        @Override
        public void setup() {
            setOnlyPlayer(true);
            setDescription("novabank.commands.bank.description");
            setParametersHelp("[deposit|withdraw|info|top|open|help]");
        }

        @Override
        public boolean execute(User user, String label, List<String> args) {
            var commands = RemoteBridges.commands(getPlugin().getServer().getServicesManager(), EDITION);
            if (commands.isEmpty()) {
                user.getSender().sendMessage(HostLabels.chat(getPlugin(), "general.not-ready", "NovaBank..."));
                return false;
            }
            return commands.get().onCommand(user.getSender(), this, getTopLabel() + " " + label,
                    args.toArray(String[]::new));
        }

        @Override
        public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
            return RemoteBridges.commands(getPlugin().getServer().getServicesManager(), EDITION)
                    .map(commands -> commands.onTabComplete(user.getSender(), this, alias, args.toArray(String[]::new)));
        }
    }
}
