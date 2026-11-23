package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.core.application.BankAdministration;
import dev.novadev.novabank.core.application.HistoryPage;
import dev.novadev.novabank.core.journal.OperationRecord;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Consultation, reset (avec confirmation) et historique de la banque personnelle d'un
 * joueur. Les acces a la base passent par {@link BankAdministration} (executeur IO) ; les
 * reponses arrivent dans le contexte global.
 */
public final class BankAdminCommands implements BankAdminActions {

    private final BankAdministration administration;
    private final PlayerLookup players;
    private final PendingConfirmations<UUID> confirmations;
    private final Supplier<Language> language;
    private final Supplier<TransferMessages> messages;
    private final Function<ItemKey, String> itemNames;

    /**
     * @param administration operations d'administration
     * @param players        recherche des joueurs
     * @param confirmations  demandes de reset en attente
     * @param language       langue en vigueur
     * @param messages       textes des banques
     * @param itemNames      nom affiche d'un item
     */
    public BankAdminCommands(BankAdministration administration, PlayerLookup players,
                             PendingConfirmations<UUID> confirmations, Supplier<Language> language,
                             Supplier<TransferMessages> messages, Function<ItemKey, String> itemNames) {
        this.administration = Objects.requireNonNull(administration, "administration");
        this.players = Objects.requireNonNull(players, "players");
        this.confirmations = Objects.requireNonNull(confirmations, "confirmations");
        this.language = Objects.requireNonNull(language, "language");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.itemNames = Objects.requireNonNull(itemNames, "itemNames");
    }

    @Override
    public void bank(CommandActor actor, String label, String player) {
        withPlayer(actor, player, found -> administration.view(EntityRef.player(found.id()), view -> {
            Language text = language.get();
            if (view.isEmpty()) {
                actor.send(text.chat("general.storage-error"));
                return;
            }
            actor.send(text.chat("admin.bank.header", "player", found.name(), "bank", view.get().owner()));
            if (view.get().exists()) {
                messages.get().snapshotLines(view.get()).forEach(actor::send);
            } else {
                actor.send(text.text("admin.bank.missing"));
            }
        }));
    }

    @Override
    public void reset(CommandActor actor, String label, String player, boolean confirmed) {
        withPlayer(actor, player, found -> {
            Language text = language.get();
            if (!confirmed) {
                confirmations.request(actor.name(), found.id());
                actor.send(text.chat("admin.reset.confirm", "player", found.name(), "label", label, "seconds",
                        PendingConfirmations.WINDOW.toSeconds()));
                return;
            }
            if (!confirmations.confirm(actor.name(), found.id())) {
                actor.send(text.chat("admin.reset.no-request", "player", found.name(), "label", label));
                return;
            }
            administration.reset(EntityRef.player(found.id()), actor.name(), result -> {
                Language now = language.get();
                if (result.isEmpty()) {
                    actor.send(now.chat("general.storage-error"));
                } else if (result.get().isEffective()) {
                    actor.send(now.chat("admin.reset.done", "player", found.name(), "value",
                            now.value(result.get().valueDelta().negate())));
                } else if (result.get().status() == OperationStatus.UNKNOWN_BANK) {
                    actor.send(now.chat("admin.reset.missing", "player", found.name()));
                } else {
                    actor.send(now.chat("admin.reset.failed", "reason", result.get().status()));
                }
            });
        });
    }

    @Override
    public void history(CommandActor actor, String label, String player, int page) {
        withPlayer(actor, player, found -> administration.history(EntityRef.player(found.id()), page, result -> {
            Language text = language.get();
            if (result.isEmpty()) {
                actor.send(text.chat("general.storage-error"));
                return;
            }
            HistoryPage history = result.get();
            actor.send(text.chat("admin.history.header", "player", found.name(), "page", history.page()));
            if (history.entries().isEmpty()) {
                actor.send(text.text("admin.history.empty"));
            }
            history.entries().forEach(entry -> actor.send(line(text, entry)));
            if (history.hasNext()) {
                actor.send(text.text("admin.history.next", "label", label, "player", found.name(), "next",
                        history.page() + 1));
            }
        }));
    }

    private String line(Language text, OperationRecord entry) {
        return text.text("admin.history.line",
                "date", text.date(entry.timestamp()),
                "type", text.text("admin.history.type." + entry.type().name().toLowerCase(Locale.ROOT)),
                "item", entry.itemKey() == null ? "-" : itemNames.apply(entry.itemKey()),
                "quantity", text.quantity(entry.quantity()),
                "value", text.value(entry.valueDelta()),
                "state", text.text("admin.history.state." + entry.state().name().toLowerCase(Locale.ROOT)));
    }

    private void withPlayer(CommandActor actor, String name, Consumer<PlayerLookup.KnownPlayer> action) {
        players.find(name, found -> {
            if (found.isEmpty()) {
                actor.send(language.get().chat("general.unknown-player", "player", name));
            } else {
                action.accept(found.get());
            }
        });
    }
}
