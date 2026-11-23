package dev.novadev.novabank.bukkit.integration;

import dev.novadev.novabank.api.entity.BankRole;
import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.entity.OwnershipProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Clans de SimpleClans (type {@code clan}, identifiant = tag du clan en minuscules). Par reflexion, d'apres l'API
 * documentee ; signatures VERIFIEES avec SimpleClans 2.19.2 (javap) : {@code SimpleClans.getInstance()},
 * {@code getClanManager()}, {@code ClanManager.getClanPlayer(UUID)} et {@code getClan(String)},
 * {@code ClanPlayer.getClan()}, {@code ClanPlayer.isLeader()}, {@code ClanPlayer.isTrusted()}, {@code Clan.getTag()},
 * {@code Clan.getName()}.
 *
 * <p>Attention : le tag d'un clan peut etre renomme ; la banque suivrait alors un autre clan portant l'ancien tag
 * (limite connue, a lever avec un identifiant stable si SimpleClans en fournit un).
 */
public final class SimpleClansOwnership implements OwnershipProvider {

    /** Type d'entite des clans. */
    public static final String CLAN = "clan";

    private final Object clans;

    private SimpleClansOwnership(Object clans) {
        this.clans = clans;
    }

    /**
     * @param loader chargeur de classes du plugin
     * @return le fournisseur, vide si l'API n'est pas trouvee
     */
    public static Optional<OwnershipProvider> create(ClassLoader loader) {
        return Reflect.type(loader, "net.sacredlabyrinth.phaed.simpleclans.SimpleClans")
                .flatMap(type -> Reflect.call(null, type, "getInstance"))
                .flatMap(plugin -> Reflect.call(plugin, "getClanManager")).map(SimpleClansOwnership::new);
    }

    @Override
    public String entityType() {
        return CLAN;
    }

    @Override
    public List<EntityMembership> resolve(UUID playerId) {
        Optional<Object> member = Reflect.call(clans, "getClanPlayer", playerId);
        Optional<String> tag = member.flatMap(found -> Reflect.call(found, "getClan"))
                .flatMap(clan -> Reflect.call(clan, "getTag")).map(text -> text.toString().toLowerCase(java.util.Locale.ROOT));
        if (tag.isEmpty()) {
            return List.of();
        }
        boolean leader = member.flatMap(found -> Reflect.call(found, "isLeader")).map(Boolean.TRUE::equals).orElse(false);
        boolean trusted = member.flatMap(found -> Reflect.call(found, "isTrusted")).map(Boolean.TRUE::equals).orElse(false);
        BankRole role = leader ? BankRole.OWNER : trusted ? BankRole.ADMIN : BankRole.MEMBER;
        return List.of(new EntityMembership(new EntityRef(CLAN, tag.get()), role));
    }

    @Override
    public Optional<String> displayName(EntityRef entity) {
        return Reflect.call(clans, "getClan", entity.id()).flatMap(clan -> Reflect.call(clan, "getName"))
                .map(Object::toString);
    }
}
