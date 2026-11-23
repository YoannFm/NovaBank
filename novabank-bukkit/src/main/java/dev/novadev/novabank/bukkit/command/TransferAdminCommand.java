package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.core.application.BankAdministration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@code /novabank transfer <type:id> <type:id> [confirm]} : transfere tout le stock de la banque d'une entite vers
 * celle d'une autre (fusion ou dissolution de groupe). Les entites se designent par {@code type:identifiant}, par
 * exemple {@code island:<uuid>} ou {@code player:<uuid>}. Deux commandes : demande puis {@code confirm} dans les 30 s.
 */
public final class TransferAdminCommand {

    private final BankAdministration administration;
    private final PendingConfirmations<UUID> confirmations;
    private final Supplier<Language> language;
    private final Supplier<ValuationMode> mode;

    /**
     * @param administration administration des banques
     * @param confirmations  demandes en attente de confirmation
     * @param language       langue en vigueur
     * @param mode           mode de valeur d'une banque creee
     */
    public TransferAdminCommand(BankAdministration administration, PendingConfirmations<UUID> confirmations,
                                Supplier<Language> language, Supplier<ValuationMode> mode) {
        this.administration = Objects.requireNonNull(administration, "administration");
        this.confirmations = Objects.requireNonNull(confirmations, "confirmations");
        this.language = Objects.requireNonNull(language, "language");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    /**
     * @param actor     administrateur
     * @param label     commande saisie
     * @param fromText  entite source ({@code type:id})
     * @param toText    entite cible ({@code type:id})
     * @param confirmed {@code true} pour la seconde commande
     */
    public void transfer(CommandActor actor, String label, String fromText, String toText, boolean confirmed) {
        Language text = language.get();
        Optional<EntityRef> from = parse(fromText);
        Optional<EntityRef> to = parse(toText);
        if (from.isEmpty() || to.isEmpty()) {
            actor.send(text.chat("admin.transfer.invalid-entity", "value", from.isEmpty() ? fromText : toText));
            return;
        }
        if (from.get().equals(to.get())) {
            actor.send(text.chat("admin.transfer.same"));
            return;
        }
        UUID key = UUID.nameUUIDFromBytes((fromText + "->" + toText).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!confirmed) {
            confirmations.request(actor.name(), key);
            actor.send(text.chat("admin.transfer.confirm", "from", fromText, "to", toText, "label", label, "seconds",
                    PendingConfirmations.WINDOW.toSeconds()));
            return;
        }
        if (!confirmations.confirm(actor.name(), key)) {
            actor.send(text.chat("admin.transfer.no-request", "from", fromText, "to", toText, "label", label));
            return;
        }
        administration.transfer(from.get(), to.get(), mode.get(), actor.name(), report -> {
            Language now = language.get();
            if (report.isEmpty()) {
                actor.send(now.chat("general.storage-error"));
                return;
            }
            actor.send(now.chat("admin.transfer.done", "lines", report.get().lines(), "units", report.get().units(),
                    "id", report.get().transferId()));
            report.get().problems().forEach(problem -> actor.send(now.chat("admin.transfer.problem", "problem", problem)));
        });
    }

    private static Optional<EntityRef> parse(String text) {
        int cut = text.indexOf(':');
        if (cut <= 0 || cut == text.length() - 1) {
            return Optional.empty();
        }
        try {
            return Optional.of(new EntityRef(text.substring(0, cut), text.substring(cut + 1)));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
