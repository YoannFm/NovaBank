package dev.novadev.novabank.core.testing;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Journal ordonne des evenements des doublures (inventaire, base, sauvegarde), pour
 * verifier l'ordre des etapes d'un flux.
 */
public final class EventLog {

    private final List<String> events = new CopyOnWriteArrayList<>();

    /**
     * @param event evenement
     */
    public void add(String event) {
        events.add(event);
    }

    /**
     * @return les evenements dans l'ordre
     */
    public List<String> events() {
        return List.copyOf(events);
    }

    /**
     * @param prefix debut de l'evenement
     * @return position de la premiere occurrence, -1 si absente
     */
    public int indexOf(String prefix) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }
}
