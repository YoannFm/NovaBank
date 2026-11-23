package dev.novadev.novabank.bukkit.integration;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.bukkit.command.CommandRouter;
import dev.novadev.novabank.config.model.CommandLabel;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

/**
 * Integration poussee d'un plugin hote, au-dela du fournisseur d'entites : commande de banque montee dans la sienne
 * ({@code /is bank}), droits decides par ses privileges ou flags. Un pont n'est monte que si l'edition correspondante
 * est installee (produit NovaBank pour cet hote) et que l'hote est active ; tout echec est signale, jamais fatal.
 */
public interface HostBridge {

    /**
     * Monte la commande et les droits dans l'hote. Appele sur le thread principal, hote active.
     *
     * @param context ce que le moteur met a disposition
     * @return le montage
     * @throws ReflectiveOperationException si l'API de l'hote n'est pas celle attendue
     */
    Mount mount(Context context) throws ReflectiveOperationException;

    /** Droit decide par l'hote pour un joueur, une entite et une operation ; vide = regle {@code banks.roles}. */
    @FunctionalInterface
    interface Rights {

        /** Aucun droit decide par l'hote. */
        Rights NONE = (playerId, entity, type) -> Optional.empty();

        /**
         * @param playerId joueur (thread du joueur)
         * @param entity   entite de l'hote
         * @param type     depot ou retrait
         * @return la decision, ou vide
         */
        Optional<Boolean> allows(UUID playerId, EntityRef entity, OperationType type);
    }

    /** Enregistrement d'une commande propre (hote sans sous-commande : {@code /landbank}). */
    @FunctionalInterface
    interface TopLevelCommands {

        /**
         * @param label  nom et alias
         * @param router logique de la commande
         * @return le retrait de la commande
         */
        AutoCloseable register(CommandLabel label, CommandRouter router);
    }

    /**
     * Ce que le moteur donne a un pont.
     *
     * @param host       plugin hote (active)
     * @param owner      plugin proprietaire des ecouteurs et taches de NovaBank
     * @param entityType type de banque fourni par l'hote ({@code island}...)
     * @param label      nom et alias de la commande de banque ({@code commands.hosts.<edition>})
     * @param commands   commande de banque limitee a {@code entityType}, a monter
     * @param topLevel   enregistrement d'une commande propre
     * @param description description de la commande de banque, dans la langue du serveur (aide de l'hote)
     * @param logger     journal de NovaBank
     */
    record Context(Plugin host, Plugin owner, String entityType, CommandLabel label, CommandRouter commands,
                   TopLevelCommands topLevel, String description, Logger logger) {

        /** Verifie la presence des champs. */
        public Context {
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(entityType, "entityType");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(commands, "commands");
            Objects.requireNonNull(topLevel, "topLevel");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(logger, "logger");
        }
    }

    /**
     * Resultat d'un montage.
     *
     * @param unmount retrait (commande, privileges...) a l'arret de l'hote ou de NovaBank
     * @param rights  droits decides par l'hote
     * @param summary description pour la console et le diagnostic ({@code /is bank, privileges ...})
     */
    record Mount(AutoCloseable unmount, Rights rights, String summary) {

        /** Verifie la presence des champs. */
        public Mount {
            Objects.requireNonNull(unmount, "unmount");
            Objects.requireNonNull(rights, "rights");
            Objects.requireNonNull(summary, "summary");
        }
    }
}
