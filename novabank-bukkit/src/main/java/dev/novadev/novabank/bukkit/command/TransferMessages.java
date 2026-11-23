package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.core.application.BankSnapshot;
import dev.novadev.novabank.core.application.BankView;
import dev.novadev.novabank.core.application.DeliveryReport;
import dev.novadev.novabank.core.application.QueryStatus;
import dev.novadev.novabank.core.application.SelectionResult;
import dev.novadev.novabank.core.application.TransferResult;
import dev.novadev.novabank.core.application.TransferStatus;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Textes des resultats des operations des joueurs, dans la langue en vigueur. Sans
 * dependance serveur.
 */
public final class TransferMessages {

    private final Language language;
    private final Function<ItemKey, String> itemNames;
    private final Function<EntityRef, String> bankNames;
    private final UndeliveredPolicy policy;
    private final long maxQuantity;

    /**
     * @param language    langue
     * @param itemNames   nom affiche d'un item
     * @param bankNames   nom affiche d'une banque (entite)
     * @param policy      politique des items non remis appliquee par la banque
     * @param maxQuantity quantite maximale par operation
     */
    public TransferMessages(Language language, Function<ItemKey, String> itemNames,
                            Function<EntityRef, String> bankNames, UndeliveredPolicy policy, long maxQuantity) {
        this.language = Objects.requireNonNull(language, "language");
        this.itemNames = Objects.requireNonNull(itemNames, "itemNames");
        this.bankNames = Objects.requireNonNull(bankNames, "bankNames");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.maxQuantity = maxQuantity;
    }

    /**
     * @param results resultats d'un depot
     * @return les lignes a envoyer
     */
    public List<String> deposit(List<TransferResult> results) {
        List<String> lines = new ArrayList<>();
        for (TransferResult result : results) {
            if (result.status() == TransferStatus.SUCCESS) {
                lines.add(language.chat("bank.deposit.success", "quantity", language.quantity(result.quantity()),
                        "item", item(result), "bank", bank(result), "value", language.value(result.valueDelta()),
                        "total", total(result)));
            } else {
                lines.add(refusal(result));
            }
            if (result.status() != TransferStatus.SUCCESS && result.undelivered() > 0) {
                lines.add(language.chat("bank.deposit.pending", "quantity", language.quantity(result.undelivered()),
                        "item", item(result)));
            }
        }
        return lines;
    }

    /**
     * @param result resultat d'un retrait
     * @return les lignes a envoyer
     */
    public List<String> withdraw(TransferResult result) {
        if (result.status() == TransferStatus.CONFIRMATION_PENDING) {
            return List.of(language.chat("bank.withdraw.confirmation-pending", "quantity",
                    language.quantity(result.quantity()), "item", item(result)));
        }
        if (result.status() != TransferStatus.SUCCESS) {
            return List.of(refusal(result));
        }
        List<String> lines = new ArrayList<>();
        if (result.quantity() > 0) {
            lines.add(language.chat("bank.withdraw.success", "quantity", language.quantity(result.quantity()),
                    "item", item(result), "bank", bank(result), "value", language.value(result.valueDelta()),
                    "total", total(result)));
        }
        if (result.undelivered() > 0) {
            String key = policy == UndeliveredPolicy.PENDING_DELIVERY ? "bank.withdraw.pending" : "bank.withdraw.returned";
            lines.add(language.chat(key, "quantity", language.quantity(result.undelivered()), "item", item(result)));
        }
        return lines;
    }

    /**
     * @param view  banque active et appartenances
     * @param label commande saisie
     * @return les lignes a envoyer
     */
    public List<String> info(BankView view, String label) {
        if (view.status() != QueryStatus.OK) {
            return List.of(language.chat(view.status() == QueryStatus.NO_BANK ? "bank.info.no-bank"
                    : view.status() == QueryStatus.NO_PERMISSION ? "general.no-permission" : "general.storage-error"));
        }
        EntityMembership active = view.active().orElseThrow();
        BankSnapshot snapshot = view.snapshot().orElseThrow();
        List<String> lines = new ArrayList<>();
        lines.add(language.chat("bank.info.header", "bank", bankNames.apply(active.entity()), "type",
                active.entity().type()));
        lines.addAll(snapshotLines(snapshot));
        String others = view.memberships().stream()
                .filter(membership -> !membership.equals(active))
                .map(membership -> bankNames.apply(membership.entity()) + " (" + membership.entity().type() + ")")
                .collect(Collectors.joining(", "));
        if (!others.isEmpty()) {
            lines.add(language.text("bank.info.others", "banks", others, "label", label));
        }
        return lines;
    }

    /**
     * Valeur et stock d'une banque (consultation joueur et administrateur).
     *
     * @param snapshot etat de la banque
     * @return les lignes
     */
    public List<String> snapshotLines(BankSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        lines.add(language.text("bank.info.value", "value", language.value(snapshot.value())));
        if (snapshot.stock().isEmpty()) {
            lines.add(language.text("bank.info.stock-empty"));
            return lines;
        }
        lines.add(language.text("bank.info.stock-header"));
        for (Map.Entry<ItemKey, Long> line : snapshot.stock().entrySet()) {
            lines.add(language.text("bank.info.stock-line", "item", itemNames.apply(line.getKey()), "quantity",
                    language.quantity(line.getValue())));
        }
        return lines;
    }

    /**
     * @param result    resultat du choix
     * @param requested type demande
     * @param types     types actives
     * @return la ligne a envoyer
     */
    public String select(SelectionResult result, String requested, List<String> types) {
        return switch (result.status()) {
            case OK -> language.chat("bank.select.success", "bank",
                    bankNames.apply(result.selected().orElseThrow().entity()), "type", requested);
            case UNKNOWN_TYPE -> language.chat("bank.select.unknown-type", "type", requested, "types",
                    String.join(", ", types));
            case NOT_MEMBER -> language.chat("bank.select.not-member", "type", requested);
            case NO_PERMISSION -> language.chat("general.no-permission");
            default -> language.chat("general.storage-error");
        };
    }

    /**
     * @param report remise des livraisons en attente
     * @return les lignes a envoyer (vide s'il n'y avait rien a remettre)
     */
    public List<String> delivery(DeliveryReport report) {
        List<String> lines = new ArrayList<>();
        if (report.delivered() > 0) {
            lines.add(language.chat("bank.delivery.delivered", "quantity", language.quantity(report.delivered())));
        }
        if (report.remaining() > 0) {
            lines.add(language.chat("bank.delivery.remaining", "quantity", language.quantity(report.remaining())));
        }
        return lines;
    }

    private String refusal(TransferResult result) {
        String key = "bank.refusal." + result.status().name().toLowerCase(Locale.ROOT).replace('_', '-');
        long seconds = Math.max(1L, (result.retryAfter().toMillis() + 999L) / 1000L);
        return language.chat(key, "item", item(result), "bank", bank(result), "max", language.quantity(maxQuantity),
                "seconds", seconds, "reason", result.ledgerStatus().map(Enum::name).orElse("?"));
    }

    private String item(TransferResult result) {
        return result.item().map(itemNames).orElse("?");
    }

    private String bank(TransferResult result) {
        return result.bank().map(bankNames).orElse("?");
    }

    private String total(TransferResult result) {
        return result.bankValue().map(language::value).orElse("?");
    }
}
