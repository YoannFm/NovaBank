package dev.novadev.novabank.bukkit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReflectTest {

    /** Comme DefaultDomain de WorldGuard : contains surcharge par type d'argument. */
    public static final class Domain {
        private final UUID member = UUID.fromString("00000000-0000-0000-0000-000000000001");

        public boolean contains(UUID id) {
            return member.equals(id);
        }

        public boolean contains(String name) {
            return "Steve".equals(name);
        }

        public String echo(Object value) {
            return String.valueOf(value);
        }
    }

    @Test
    void choisit_la_surcharge_selon_le_type_et_garde_le_resultat_en_cache() {
        Domain domain = new Domain();
        UUID member = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertThat(Reflect.call(domain, "contains", member)).contains(true);
        assertThat(Reflect.call(domain, "contains", "Steve")).contains(true);
        assertThat(Reflect.call(domain, "contains", UUID.randomUUID())).contains(false);
        assertThat(Reflect.call(domain, "contains", member)).as("second appel, methode en cache").contains(true);
    }

    /** Comme StateFlag de WorldGuard : constructeur (String, boolean). */
    public static final class Flag {
        private final String name;
        private final boolean byDefault;

        public Flag(String name, boolean byDefault) {
            this.name = name;
            this.byDefault = byDefault;
        }

        public String describe() {
            return name + "=" + byDefault;
        }
    }

    @Test
    void construction_avec_un_booleen_primitif() {
        assertThat(Reflect.construct(Flag.class, "novabank-withdraw", false))
                .flatMap(flag -> Reflect.call(flag, "describe")).contains("novabank-withdraw=false");
        assertThat(Reflect.construct(Flag.class, "seul")).as("aucun constructeur a un argument").isEmpty();
    }

    @Test
    void methode_absente_ou_argument_nul() {
        Domain domain = new Domain();

        assertThat(Reflect.call(domain, "absente")).isEmpty();
        assertThat(Reflect.call(domain, "contains", 42)).as("aucune surcharge pour un entier").isEmpty();
        assertThat(Reflect.call(domain, "echo", (Object) null)).contains("null");
    }
}
