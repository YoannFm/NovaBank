package dev.novadev.novabank.sync.redis;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.core.sync.BankChange;
import dev.novadev.novabank.core.sync.SyncEvent;
import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Format texte des messages du canal : {@code 1|origine|banque|version|epoch|dateMillis}.
 * Tout message mal forme est ignore : le canal Redis n'est pas une frontiere de confiance.
 */
final class SyncPayloads {

    static final Pattern SERVER_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    /** Nom de commande, suivi pour {@code config} d'une charge utile en base64url (au plus 800 000 caracteres, sous la limite RESP de 1 Mio). */
    static final Pattern COMMAND = Pattern.compile("[a-z-]{1,32}(:[A-Za-z0-9_-]{1,800000})?");

    private static final String VERSION = "1";
    private static final int FIELDS = 6;

    private SyncPayloads() {
    }

    /**
     * @param event evenement
     * @return le message
     * @throws IllegalArgumentException si l'identifiant du serveur contient un caractere interdit
     */
    static String encode(SyncEvent event) {
        if (!SERVER_ID.matcher(event.originServer()).matches()) {
            throw new IllegalArgumentException(
                    ConsoleText.tr("Identifiant de serveur invalide pour la synchronisation"));
        }
        BankChange change = event.change();
        return String.join("|", VERSION, event.originServer(), change.bankId().toString(),
                Long.toString(change.version()), Long.toString(change.resetEpoch()),
                Long.toString(change.updatedAt().toEpochMilli()));
    }

    /**
     * @param payload message recu
     * @return l'evenement, ou vide si le message est invalide
     */
    static Optional<SyncEvent> decode(String payload) {
        String[] fields = payload.split("\\|", -1);
        if (fields.length != FIELDS || !VERSION.equals(fields[0]) || !SERVER_ID.matcher(fields[1]).matches()) {
            return Optional.empty();
        }
        try {
            BankChange change = new BankChange(new BankId(UUID.fromString(fields[2])), Long.parseLong(fields[3]),
                    Long.parseLong(fields[4]), Instant.ofEpochMilli(Long.parseLong(fields[5])));
            return Optional.of(new SyncEvent(fields[1], change));
        } catch (IllegalArgumentException | java.time.DateTimeException invalid) {
            return Optional.empty();
        }
    }

    /**
     * @param origin  serveur emetteur
     * @param command commande de controle
     * @return le message {@code 1|origine|commande}
     * @throws IllegalArgumentException si l'origine ou la commande est invalide
     */
    static String encodeControl(String origin, String command) {
        if (!SERVER_ID.matcher(origin).matches() || !COMMAND.matcher(command).matches()) {
            throw new IllegalArgumentException(ConsoleText.tr("Commande de controle invalide"));
        }
        return String.join("|", VERSION, origin, command);
    }

    /**
     * @param payload message recu sur le canal de controle
     * @return {origine, commande}, ou vide si le message est invalide
     */
    static Optional<String[]> decodeControl(String payload) {
        String[] fields = payload.split("\\|", -1);
        if (fields.length != 3 || !VERSION.equals(fields[0]) || !SERVER_ID.matcher(fields[1]).matches()
                || !COMMAND.matcher(fields[2]).matches()) {
            return Optional.empty();
        }
        return Optional.of(new String[] {fields[1], fields[2]});
    }
}
