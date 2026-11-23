package dev.novadev.novabank.bukkit.item;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.inventory.ItemStack;

/**
 * Liaisons par REFLEXION avec les plugins d'items custom (ItemsAdder, Nexo, Oraxen) : NovaBank ne
 * les compile pas (aucune dependance, aucun depot a configurer, ItemsAdder etant payant) et
 * les charge par nom de classe si le plugin est present. Une API absente ou modifiee donne
 * {@link Optional#empty()} (le plugin n'est alors pas pris en charge, journal a l'appelant), jamais une
 * exception.
 *
 * <p>Correspondance des cles (D12) : ItemsAdder {@code pack:ruby_block} est {@code itemsadder:pack/ruby_block}
 * (le premier ':' devient '/', car ':' est interdit dans l'identifiant d'une cle) ; Nexo et Oraxen utilisent
 * leur identifiant tel quel.
 *
 * <p>Signatures VERIFIEES par javap le 2026-10-29 sur ItemsAdder 4.0.18, Oraxen 1.220.1, CraftEngine 26.9.2, MMOItems 6.9.5 et Nexo
 * 1.29.1 (artefact Maven public). Rappel des appels utilises :
 * <ul>
 *   <li>ItemsAdder : {@code dev.lone.itemsadder.api.CustomStack} : {@code static byItemStack(ItemStack)},
 *       {@code static getInstance(String)}, {@code getNamespacedID()}, {@code getItemStack()} ;</li>
 *   <li>Nexo : {@code com.nexomc.nexo.api.NexoItems} : {@code idFromItem(ItemStack)}, {@code itemFromId(String)}
 *       (ItemBuilder, {@code build()}) ;</li>
 *   <li>Oraxen : {@code io.th0rgal.oraxen.api.OraxenItems} : {@code getIdByItem(ItemStack)},
 *       {@code getItemById(String)} (ItemBuilder, {@code build()}).</li>
 * </ul>
 */
public final class CustomItemPlugins {

    /** Plugin, namespace et classe de l'evenement de fin de chargement des items. */
    public record Spec(String pluginName, String namespace, String loadedEventClass) {
    }

    /** ItemsAdder. */
    public static final Spec ITEMSADDER = new Spec("ItemsAdder", "itemsadder",
            "dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent");
    /** Nexo. */
    public static final Spec NEXO = new Spec("Nexo", "nexo", "com.nexomc.nexo.api.events.NexoItemsLoadedEvent");
    /** Oraxen. */
    public static final Spec ORAXEN = new Spec("Oraxen", "oraxen",
            "io.th0rgal.oraxen.api.events.OraxenItemsLoadedEvent");

    /** CraftEngine. */
    public static final Spec CRAFTENGINE = new Spec("CraftEngine", "craftengine", "");
    /** MMOItems. */
    public static final Spec MMOITEMS = new Spec("MMOItems", "mmoitems", "");

    private CustomItemPlugins() {
    }

    /**
     * @param loader chargeur de classes des plugins (celui du serveur)
     * @return le fournisseur ItemsAdder, vide si son API n'est pas trouvee
     */
    public static Optional<ItemProvider> itemsAdder(ClassLoader loader) {
        return load(loader, "dev.lone.itemsadder.api.CustomStack").flatMap(stack -> {
            Optional<Method> byItem = method(stack, "byItemStack", ItemStack.class);
            Optional<Method> byId = method(stack, "getInstance", String.class);
            Optional<Method> namespaced = method(stack, "getNamespacedID");
            Optional<Method> itemStack = method(stack, "getItemStack");
            if (byItem.isEmpty() || byId.isEmpty() || namespaced.isEmpty() || itemStack.isEmpty()) {
                return Optional.<ItemProvider>empty();
            }
            Function<ItemStack, String> idOf = item -> {
                Object custom = invoke(byItem.get(), null, item);
                return custom == null ? null : (String) invoke(namespaced.get(), custom);
            };
            Function<String, ItemStack> build = id -> {
                Object custom = invoke(byId.get(), null, id);
                return custom == null ? null : (ItemStack) invoke(itemStack.get(), custom);
            };
            return Optional.of(new MappedItemProvider(ITEMSADDER.namespace(), idOf, build,
                    CustomItemPlugins::slashToColon, CustomItemPlugins::colonToSlash));
        });
    }

    /**
     * @param loader chargeur de classes des plugins
     * @return le fournisseur Nexo, vide si son API n'est pas trouvee
     */
    public static Optional<ItemProvider> nexo(ClassLoader loader) {
        return builderBased(loader, NEXO, "com.nexomc.nexo.api.NexoItems", "idFromItem", "itemFromId");
    }

    /**
     * @param loader chargeur de classes des plugins
     * @return le fournisseur Oraxen, vide si son API n'est pas trouvee
     */
    public static Optional<ItemProvider> oraxen(ClassLoader loader) {
        return builderBased(loader, ORAXEN, "io.th0rgal.oraxen.api.OraxenItems", "getIdByItem", "getItemById");
    }

    /**
     * CraftEngine : cle {@code craftengine:espace/chemin} = identifiant {@code espace:chemin}. Signatures VERIFIEES avec
     * CraftEngine 26.9.2 (javap) : {@code CraftEngineItems.getCustomItemId(ItemStack)} (Key), {@code byId(String)}
     * (BukkitItemDefinition), {@code BukkitItemDefinition.buildBukkitItem(ItemBuildContext)}, {@code ItemBuildContext.empty()}.
     *
     * @param loader chargeur de classes du plugin
     * @return le fournisseur, vide si l'API n'est pas trouvee
     */
    public static Optional<ItemProvider> craftEngine(ClassLoader loader) {
        Optional<Class<?>> items = load(loader, "net.momirealms.craftengine.bukkit.api.CraftEngineItems");
        Optional<Class<?>> context = load(loader, "net.momirealms.craftengine.core.item.ItemBuildContext");
        if (items.isEmpty() || context.isEmpty()) {
            return Optional.empty();
        }
        Optional<Method> idOf = method(items.get(), "getCustomItemId", ItemStack.class);
        Optional<Method> byId = method(items.get(), "byId", String.class);
        Optional<Method> empty = method(context.get(), "empty");
        if (idOf.isEmpty() || byId.isEmpty() || empty.isEmpty()) {
            return Optional.empty();
        }
        Function<ItemStack, String> id = stack -> {
            Object found = invoke(idOf.get(), null, stack);
            return found == null ? null : found.toString();
        };
        Function<String, ItemStack> build = pluginId -> {
            Object definition = invoke(byId.get(), null, pluginId);
            Optional<Method> built = definition == null ? Optional.empty()
                    : method(definition.getClass(), "buildBukkitItem", context.get());
            return built.isEmpty() ? null : (ItemStack) invoke(built.get(), definition, invoke(empty.get(), null));
        };
        return Optional.of(new MappedItemProvider(CRAFTENGINE.namespace(), id, build,
                CustomItemPlugins::slashToColon, CustomItemPlugins::colonToSlash));
    }

    /**
     * MMOItems : cle {@code mmoitems:type/identifiant} (minuscules) = type et identifiant MMOItems (majuscules).
     * Signatures supposees, non verifiees : {@code MMOItems.getTypeName(ItemStack)}, {@code MMOItems.getID(ItemStack)},
     * champ statique {@code MMOItems.plugin} et {@code getItem(String, String)}.
     *
     * @param loader chargeur de classes du plugin
     * @return le fournisseur, vide si l'API n'est pas trouvee
     */
    public static Optional<ItemProvider> mmoItems(ClassLoader loader) {
        return load(loader, "net.Indyuce.mmoitems.MMOItems").flatMap(api -> {
            Optional<Method> typeOf = method(api, "getTypeName", ItemStack.class);
            Optional<Method> idOf = method(api, "getID", ItemStack.class);
            Optional<Method> getItem = method(api, "getItem", String.class, String.class);
            if (typeOf.isEmpty() || idOf.isEmpty() || getItem.isEmpty()) {
                return Optional.<ItemProvider>empty();
            }
            Function<ItemStack, String> id = stack -> {
                Object type = invoke(typeOf.get(), null, stack);
                Object name = invoke(idOf.get(), null, stack);
                return type == null || name == null || type.toString().isEmpty() ? null : type + "/" + name;
            };
            Function<String, ItemStack> build = pluginId -> {
                int cut = pluginId.indexOf('/');
                if (cut <= 0) {
                    return null;
                }
                try {
                    Object plugin = api.getField("plugin").get(null);
                    return (ItemStack) getItem.get().invoke(plugin, pluginId.substring(0, cut), pluginId.substring(cut + 1));
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    return null;
                }
            };
            return Optional.of(new MappedItemProvider(MMOITEMS.namespace(), id, build,
                    novaId -> novaId.toUpperCase(java.util.Locale.ROOT), pluginId -> pluginId.toLowerCase(java.util.Locale.ROOT)));
        });
    }

    private static Optional<ItemProvider> builderBased(ClassLoader loader, Spec spec, String apiClass, String idMethod,
                                                       String itemMethod) {
        return load(loader, apiClass).flatMap(api -> {
            Optional<Method> idOf = method(api, idMethod, ItemStack.class);
            Optional<Method> item = method(api, itemMethod, String.class);
            if (idOf.isEmpty() || item.isEmpty()) {
                return Optional.<ItemProvider>empty();
            }
            Function<ItemStack, String> id = stack -> (String) invoke(idOf.get(), null, stack);
            Function<String, ItemStack> build = pluginId -> {
                Object builder = invoke(item.get(), null, pluginId);
                if (builder == null) {
                    return null;
                }
                Optional<Method> built = method(builder.getClass(), "build");
                return built.isEmpty() ? null : (ItemStack) invoke(built.get(), builder);
            };
            return Optional.of(new MappedItemProvider(spec.namespace(), id, build, Function.identity(),
                    Function.identity()));
        });
    }

    static String slashToColon(String id) {
        int slash = id.indexOf('/');
        return slash < 0 ? id : id.substring(0, slash) + ':' + id.substring(slash + 1);
    }

    static String colonToSlash(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(0, colon) + '/' + id.substring(colon + 1);
    }

    private static Optional<Class<?>> load(ClassLoader loader, String name) {
        try {
            return Optional.of(Class.forName(name, false, loader));
        } catch (ClassNotFoundException | LinkageError absent) {
            return Optional.empty();
        }
    }

    private static Optional<Method> method(Class<?> type, String name, Class<?>... parameters) {
        try {
            Method method = type.getMethod(name, parameters);
            method.setAccessible(true);
            return Optional.of(method);
        } catch (NoSuchMethodException | RuntimeException absent) {
            return Optional.empty();
        }
    }

    /**
     * Appelle une methode statique, ou d'instance (objet Kotlin : champ {@code INSTANCE}).
     *
     * @return le resultat, ou {@code null}
     */
    private static Object invoke(Method method, Object target, Object... arguments) {
        try {
            Object receiver = target;
            if (receiver == null && !Modifier.isStatic(method.getModifiers())) {
                Field instance = method.getDeclaringClass().getField("INSTANCE");
                receiver = instance.get(null);
            }
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchFieldException | RuntimeException failure) {
            return null;
        }
    }
}
