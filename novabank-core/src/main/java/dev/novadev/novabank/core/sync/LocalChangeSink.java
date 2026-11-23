package dev.novadev.novabank.core.sync;

/**
 * Receveur des changements enregistres par CE serveur, appele apres le commit du stockage depuis
 * le thread qui a ecrit (executeur IO). Ne doit ni bloquer ni lever d'exception.
 */
public interface LocalChangeSink {

    /**
     * @param change banque modifiee et version atteinte
     */
    void localChange(BankChange change);

    /**
     * Operation enregistree, avec l'etat de la banque et l'entree de journal ecrites (par defaut : rien).
     *
     * @param bank   etat enregistre de la banque
     * @param record entree de journal enregistree
     */
    default void applied(dev.novadev.novabank.core.bank.Bank bank,
                         dev.novadev.novabank.core.journal.OperationRecord record) {
    }

    /**
     * Banque creee par ce serveur (par defaut : rien).
     *
     * @param bank banque neuve
     */
    default void bankCreated(dev.novadev.novabank.core.bank.Bank bank) {
    }

    /**
     * Consigne l'echec d'une notification (l'operation, elle, est enregistree).
     *
     * @param failure erreur survenue
     */
    void noteAnnounceFailure(RuntimeException failure);

    /**
     * @param sinks receveurs a appeler dans l'ordre ; un echec de l'un n'empeche pas les suivants
     * @return un receveur unique
     */
    static LocalChangeSink all(LocalChangeSink... sinks) {
        LocalChangeSink[] copy = sinks.clone();
        return new LocalChangeSink() {
            @Override
            public void localChange(BankChange change) {
                for (LocalChangeSink sink : copy) {
                    try {
                        sink.localChange(change);
                    } catch (RuntimeException failure) {
                        sink.noteAnnounceFailure(failure);
                    }
                }
            }

            @Override
            public void applied(dev.novadev.novabank.core.bank.Bank bank,
                                dev.novadev.novabank.core.journal.OperationRecord record) {
                for (LocalChangeSink sink : copy) {
                    try {
                        sink.applied(bank, record);
                    } catch (RuntimeException failure) {
                        sink.noteAnnounceFailure(failure);
                    }
                }
            }

            @Override
            public void bankCreated(dev.novadev.novabank.core.bank.Bank bank) {
                for (LocalChangeSink sink : copy) {
                    try {
                        sink.bankCreated(bank);
                    } catch (RuntimeException failure) {
                        sink.noteAnnounceFailure(failure);
                    }
                }
            }

            @Override
            public void noteAnnounceFailure(RuntimeException failure) {
                for (LocalChangeSink sink : copy) {
                    sink.noteAnnounceFailure(failure);
                }
            }
        };
    }
}
