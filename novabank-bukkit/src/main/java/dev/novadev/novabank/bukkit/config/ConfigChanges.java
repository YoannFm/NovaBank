package dev.novadev.novabank.bukkit.config;

import dev.novadev.novabank.core.application.TaskScheduler;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Chemin unique des modifications de configuration partagees (GUI, items, {@code config set}) et de celles recues d'un
 * autre serveur : ecriture sauvegardee et validee ({@link ConfigDeployer}, hors du thread principal), rechargement local,
 * puis, si le deploiement Redis est actif, diffusion aux autres serveurs. Sans Redis, tout reste local.
 */
public final class ConfigChanges {

    /**
     * @param write     resultat de l'ecriture
     * @param reload    rechargement fait si le fichier a change
     * @param published vrai si les autres serveurs ont ete prevenus
     */
    public record Result(ConfigDeployer.Outcome write, Optional<ReloadOutcome> reload, boolean published) {
    }

    /** Prefixe de la commande de controle qui porte une configuration. */
    public static final String COMMAND = "config:";

    private final ConfigDeployer deployer;
    private final TaskScheduler scheduler;
    private final Supplier<ReloadOutcome> reloader;
    private final Predicate<String> broadcaster;
    private final BooleanSupplier deployEnabled;
    private final Logger logger;

    /**
     * @param deployer      ecriture validee
     * @param scheduler     planificateur
     * @param reloader      rechargement local (thread principal), sans diffusion
     * @param broadcaster   envoi d'une commande de controle aux autres serveurs ; faux si le bus est indisponible
     * @param deployEnabled deploiement actif (Redis actif et {@code redis.deploy-config})
     * @param logger        journal
     */
    public ConfigChanges(ConfigDeployer deployer, TaskScheduler scheduler, Supplier<ReloadOutcome> reloader,
                         Predicate<String> broadcaster, BooleanSupplier deployEnabled, Logger logger) {
        this.deployer = Objects.requireNonNull(deployer, "deployer");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.reloader = Objects.requireNonNull(reloader, "reloader");
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
        this.deployEnabled = Objects.requireNonNull(deployEnabled, "deployEnabled");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * @param blocks sections partagees a remplacer
     * @param reason motif de la sauvegarde
     * @param done   resultat, rendu sur le thread principal
     */
    public void apply(Map<String, String> blocks, String reason, Consumer<Result> done) {
        try {
            scheduler.runAsync(() -> {
                ConfigDeployer.Outcome write = deployer.apply(blocks, reason);
                scheduler.runGlobal(() -> done.accept(finish(write, blocks.keySet())));
            });
        } catch (RejectedExecutionException saturated) {
            done.accept(new Result(new ConfigDeployer.Outcome(false, false,
                    java.util.List.of("serveur occupe, reessayez")), Optional.empty(), false));
        }
    }

    /**
     * Modification faite en jeu sur ce serveur, par transformation du texte de {@code config.yml} (menu des parametres) :
     * ecriture validee et sauvegardee, rechargement, puis diffusion des seules sections partagees parmi {@code sections}.
     *
     * @param change   transformation du texte actuel du fichier
     * @param sections sections de premier niveau touchees (seules les partagees sont diffusees)
     * @param reason   motif de la sauvegarde
     * @param done     resultat, rendu sur le thread principal
     */
    public void applyEdit(UnaryOperator<String> change, Collection<String> sections, String reason, Consumer<Result> done) {
        try {
            scheduler.runAsync(() -> {
                ConfigDeployer.Outcome write = deployer.edit(change, reason);
                scheduler.runGlobal(() -> done.accept(finish(write, sections)));
            });
        } catch (RejectedExecutionException saturated) {
            done.accept(new Result(new ConfigDeployer.Outcome(false, false,
                    java.util.List.of("serveur occupe, reessayez")), Optional.empty(), false));
        }
    }

    /** @return {@code true} si le deploiement est actif (Redis actif et {@code redis.deploy-config}) */
    public boolean canPush() {
        return deployEnabled.getAsBoolean();
    }

    /**
     * Renvoie aux autres serveurs toutes les sections partagees du fichier actuel (rattrapage d'un serveur qui etait eteint
     * ou isole lors d'une modification).
     *
     * @return {@code true} si les autres serveurs ont ete prevenus
     */
    public boolean push() {
        if (!deployEnabled.getAsBoolean()) {
            return false;
        }
        Optional<String> payload = deployer.payloadFor(ConfigPayload.SHARED);
        return payload.isPresent() && broadcaster.test(COMMAND + payload.get());
    }

    /**
     * Configuration envoyee par un autre serveur (appele depuis un thread du bus).
     *
     * @param payload charge utile
     */
    public void receive(String payload) {
        if (!deployEnabled.getAsBoolean()) {
            return;
        }
        try {
            scheduler.runAsync(() -> {
                ConfigDeployer.Outcome write = deployer.receive(payload);
                if (!write.applied()) {
                    logger.warning(ConsoleText.tr("Configuration recue d'un autre serveur refusee : {0}",
                            String.join("; ", write.errors())));
                    return;
                }
                if (write.changed()) {
                    scheduler.runGlobal(() -> {
                        ReloadOutcome outcome = reloader.get();
                        logger.info(outcome.applied() ? ConsoleText.tr("Configuration recue d'un autre serveur : sauvegarde faite, appliquee")
                    : ConsoleText.tr("Configuration recue d'un autre serveur : sauvegarde faite, rechargement refuse (configuration precedente conservee)"));
                    });
                }
            });
        } catch (RejectedExecutionException saturated) {
            logger.log(Level.WARNING,
                    ConsoleText.tr("Configuration recue d'un autre serveur ignoree : serveur occupe"));
        }
    }

    private Result finish(ConfigDeployer.Outcome write, Collection<String> sections) {
        if (!write.applied() || !write.changed()) {
            return new Result(write, Optional.empty(), false);
        }
        ReloadOutcome outcome = reloader.get();
        boolean published = false;
        if (outcome.applied() && deployEnabled.getAsBoolean()) {
            Optional<String> payload = deployer.payloadFor(sections);
            published = payload.isPresent() && broadcaster.test(COMMAND + payload.get());
            if (!published) {
                logger.warning(
                        ConsoleText.tr("Configuration modifiee mais non diffusee aux autres serveurs (Redis indisponible)"));
            }
        }
        return new Result(write, Optional.of(outcome), published);
    }
}
