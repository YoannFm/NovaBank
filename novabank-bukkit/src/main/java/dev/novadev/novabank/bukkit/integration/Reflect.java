package dev.novadev.novabank.bukkit.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Appels reflexifs surs : une API absente ou modifiee donne {@link Optional#empty()}, jamais une
 * exception. Les methodes sont cherchees parmi les methodes publiques, y compris celles d'une
 * interface implementee par un objet non public.
 */
public final class Reflect {

    /** Methode trouvee pour une classe, un nom et des types d'arguments ({@code null} : argument nul). */
    private record Key(Class<?> type, String name, List<Class<?>> argumentTypes) {
    }

    // Les integrations appellent les memes methodes a chaque operation (WorldGuard : pour chaque region) ;
    // getMethods() recopie toute la liste a chaque appel, d'ou ce cache. Vide a l'arret du plugin (chargeur de NovaBank).
    private static final Map<Key, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    private Reflect() {
    }

    /**
     * @param loader chargeur de classes du plugin hote
     * @param name   nom complet de la classe
     * @return la classe, vide si elle est absente
     */
    public static Optional<Class<?>> type(ClassLoader loader, String name) {
        try {
            return Optional.of(Class.forName(name, false, loader));
        } catch (ClassNotFoundException | LinkageError absent) {
            return Optional.empty();
        }
    }

    /**
     * @param target    objet, ou {@code null} pour une methode statique de {@code owner}
     * @param owner     classe declarant la methode (pour une methode statique)
     * @param name      nom de la methode
     * @param arguments arguments (les types sont deduits de leur classe, interfaces comprises)
     * @return le resultat, vide si la methode est absente, echoue ou rend {@code null}
     */
    public static Optional<Object> call(Object target, Class<?> owner, String name, Object... arguments) {
        Class<?> type = target != null ? target.getClass() : owner;
        List<Class<?>> argumentTypes = Arrays.stream(arguments).<Class<?>>map(a -> a == null ? null : a.getClass()).toList();
        Optional<Method> method = METHODS.computeIfAbsent(new Key(type, name, argumentTypes), key -> find(type, name, arguments));
        if (method.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(method.get().invoke(target, arguments));
        } catch (InvocationTargetException | IllegalAccessException | RuntimeException failure) {
            return Optional.empty();
        }
    }

    /**
     * @param type      classe a instancier
     * @param arguments arguments du constructeur public (types deduits de leur classe ; booleens : {@code boolean})
     * @return l'instance, vide si aucun constructeur ne convient ou s'il echoue
     */
    public static Optional<Object> construct(Class<?> type, Object... arguments) {
        for (java.lang.reflect.Constructor<?> constructor : type.getConstructors()) {
            if (constructor.getParameterCount() == arguments.length
                    && accepts(constructor.getParameterTypes(), arguments)) {
                try {
                    return Optional.of(constructor.newInstance(arguments));
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Method> find(Class<?> type, String name, Object[] arguments) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arguments.length && accepts(method, arguments)) {
                return Optional.of(publicVersion(method));
            }
        }
        return Optional.empty();
    }

    /**
     * @param target    objet
     * @param name      nom de la methode
     * @param arguments arguments
     * @return le resultat, vide si la methode est absente, echoue ou rend {@code null}
     */
    public static Optional<Object> call(Object target, String name, Object... arguments) {
        return call(target, target.getClass(), name, arguments);
    }

    /**
     * Une methode declaree par une classe non publique (implementation interne d'un plugin) ne peut pas etre appelee par
     * reflexion : on cherche la meme methode sur une interface ou une classe mere publique.
     */
    private static Method publicVersion(Method method) {
        if (Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
            return method;
        }
        java.util.ArrayDeque<Class<?>> pending = new java.util.ArrayDeque<>();
        pending.add(method.getDeclaringClass());
        while (!pending.isEmpty()) {
            Class<?> type = pending.poll();
            if (Modifier.isPublic(type.getModifiers())) {
                try {
                    return type.getMethod(method.getName(), method.getParameterTypes());
                } catch (NoSuchMethodException absent) {
                    // Pas declaree ici : on continue la recherche.
                }
            }
            if (type.getSuperclass() != null) {
                pending.add(type.getSuperclass());
            }
            pending.addAll(java.util.List.of(type.getInterfaces()));
        }
        return method;
    }

    private static boolean accepts(Method method, Object[] arguments) {
        return accepts(method.getParameterTypes(), arguments);
    }

    private static boolean accepts(Class<?>[] parameters, Object[] arguments) {
        for (int i = 0; i < parameters.length; i++) {
            if (arguments[i] != null && !wrap(parameters[i]).isInstance(arguments[i])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        return type == int.class ? Integer.class : type == long.class ? Long.class
                : type == boolean.class ? Boolean.class : type == double.class ? Double.class : Object.class;
    }
}
