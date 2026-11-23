package dev.novadev.novabank.core.access;

import dev.novadev.novabank.api.entity.BankRole;
import dev.novadev.novabank.api.operation.OperationType;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Roles autorises a deposer et a retirer dans les banques d'un type d'entite.
 *
 * @param deposit  roles autorises a deposer
 * @param withdraw roles autorises a retirer
 */
public record RoleRights(Set<BankRole> deposit, Set<BankRole> withdraw) {

    /** Droits par defaut : tout membre depose, seuls proprietaire et gestionnaires retirent. */
    public static final RoleRights DEFAULT = new RoleRights(
            EnumSet.allOf(BankRole.class), EnumSet.of(BankRole.OWNER, BankRole.ADMIN));

    /**
     * Fige les ensembles.
     *
     * @throws NullPointerException si un ensemble est nul
     */
    public RoleRights {
        deposit = Set.copyOf(Objects.requireNonNull(deposit, "deposit"));
        withdraw = Set.copyOf(Objects.requireNonNull(withdraw, "withdraw"));
    }

    /**
     * Indique si un role peut realiser une operation.
     *
     * @param type operation (depot ou retrait ; un reset n'est jamais autorise par role)
     * @param role role du joueur dans l'entite
     * @return {@code true} si l'operation est autorisee
     */
    public boolean allows(OperationType type, BankRole role) {
        Objects.requireNonNull(role, "role");
        return switch (Objects.requireNonNull(type, "type")) {
            case DEPOSIT -> deposit.contains(role);
            case WITHDRAW -> withdraw.contains(role);
            case RESET -> false;
        };
    }
}
