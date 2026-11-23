package dev.novadev.novabank.bukkit.host;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

/** Services Bukkit en memoire : seuls register, unregister et getRegistrations servent aux canaux NovaBank. */
final class FakeServices {

    private FakeServices() {
    }

    static ServicesManager create() {
        List<Object[]> registered = new ArrayList<>();
        return (ServicesManager) Proxy.newProxyInstance(FakeServices.class.getClassLoader(),
                new Class<?>[] {ServicesManager.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "register" -> registered.add(new Object[] {args[0], args[1]}) ? null : null;
                    case "unregister" -> registered.removeIf(entry -> entry[0] == args[0] && entry[1] == args[1]);
                    case "getRegistrations" -> registered.stream().filter(entry -> entry[0] == args[0])
                            .map(entry -> provider((Class<?>) entry[0], entry[1])).toList();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RegisteredServiceProvider<?> provider(Class<?> type, Object provider) {
        return new RegisteredServiceProvider(type, provider, ServicePriority.Lowest, null);
    }
}
