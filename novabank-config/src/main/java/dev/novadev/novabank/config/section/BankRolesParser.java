package dev.novadev.novabank.config.section;

import dev.novadev.novabank.api.entity.BankRole;
import dev.novadev.novabank.config.tree.ConfigNode;
import dev.novadev.novabank.config.tree.ConfigTrees;
import dev.novadev.novabank.core.access.RoleRights;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sous-section {@code banks.roles} : pour chaque type d'entite active, roles autorises a
 * deposer ({@code deposit}) et a retirer ({@code withdraw}). Un type absent prend
 * {@link RoleRights#DEFAULT} ; un type non active est ignore avec un avertissement ; un
 * role inconnu est une erreur. Une liste vide interdit l'operation a tous les roles.
 */
final class BankRolesParser {

    private static final String DEPOSIT = "deposit";
    private static final String WITHDRAW = "withdraw";

    private BankRolesParser() {
    }

    /**
     * @param banks       section {@code banks}
     * @param entityTypes types actives
     * @return les droits de chaque type active, dans l'ordre des types
     */
    static Map<String, RoleRights> parse(ConfigNode banks, List<String> entityTypes) {
        ConfigNode node = banks.section("roles");
        node.keys().stream()
                .filter(type -> BanksSectionParser.normalizedType(type).filter(entityTypes::contains).isEmpty())
                .forEach(type -> node.ignore(type,
                        ConsoleText.tr("type d'entite non active dans banks.entity-types : droits ignores")));
        Map<String, RoleRights> rights = new LinkedHashMap<>();
        for (String type : entityTypes) {
            ConfigNode section = node.section(type);
            rights.put(type, new RoleRights(
                    roles(section, DEPOSIT, RoleRights.DEFAULT.deposit()),
                    roles(section, WITHDRAW, RoleRights.DEFAULT.withdraw())));
        }
        return Collections.unmodifiableMap(rights);
    }

    private static Set<BankRole> roles(ConfigNode section, String key, Set<BankRole> defaults) {
        if (!section.has(key)) {
            section.stringList(key, List.of());
            return defaults;
        }
        List<String> names = section.stringList(key, List.of());
        Set<BankRole> roles = EnumSet.noneOf(BankRole.class);
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i).toUpperCase(Locale.ROOT);
            try {
                roles.add(BankRole.valueOf(name));
            } catch (IllegalArgumentException unknown) {
                section.error(ConfigTrees.element(key, i),
                        ConsoleText.tr("role inconnu '{0}' (roles possibles : owner, admin, member)",
                                names.get(i)));
            }
        }
        return roles;
    }
}
