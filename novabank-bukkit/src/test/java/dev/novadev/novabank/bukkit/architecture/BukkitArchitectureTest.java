package dev.novadev.novabank.bukkit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import dev.novadev.novabank.bukkit.NovaBankPlugin;
import dev.novadev.novabank.bukkit.command.CommandMapAccess;
import dev.novadev.novabank.bukkit.item.CustomItemPlugins;
import dev.novadev.novabank.bukkit.lifecycle.PluginHost;
import dev.novadev.novabank.bukkit.config.YamlFileUpdater;
import dev.novadev.novabank.bukkit.scheduler.PlatformSchedulers;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

/**
 * Regles d'architecture transverses, verifiees ici car ce module voit tous les autres.
 */
class BukkitArchitectureTest {

    /** Classes de production : ni dossiers de tests, ni test-jar de core (sondes volontairement fautives). */
    private static final ImportOption MAIN_ONLY = location -> new ImportOption.DoNotIncludeTests().includes(location)
            && !location.contains("-tests.jar");

    private static final JavaClasses PLATFORM_FREE_MODULES = new ClassFileImporter().withImportOption(MAIN_ONLY)
            .importPackages("dev.novadev.novabank.core", "dev.novadev.novabank.config", "dev.novadev.novabank.storage",
                    "dev.novadev.novabank.libraries");

    private static final JavaClasses BUKKIT_MODULE = new ClassFileImporter().withImportOption(MAIN_ONLY)
            .importPackages("dev.novadev.novabank.bukkit");

    /** core, config, storage-sql et libraries restent sans aucune API serveur (D3). */
    static final ArchRule MODULES_WITHOUT_PLATFORM = noClasses()
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.bukkit..", "io.papermc..", "org.spigotmc..", "net.md_5..",
                    "com.destroystokyo..")
            .because("seul novabank-bukkit (et les futurs modules plateforme) depend du serveur");

    /**
     * La reflexion est confinee a l'adaptateur du CommandMap, au chargement du planificateur Folia
     * et a l'API de commentaires YAML (Bukkit 1.18.1+, absente de l'API 1.17 compilee).
     */
    static final ArchRule REFLECTION_ISOLATED = noClasses()
            .that().doNotHaveFullyQualifiedName(CommandMapAccess.class.getName())
            .and().doNotHaveFullyQualifiedName(PlatformSchedulers.class.getName())
            .and().doNotHaveFullyQualifiedName(YamlFileUpdater.class.getName())
            .and().doNotHaveFullyQualifiedName(CustomItemPlugins.class.getName())
            .and().resideOutsideOfPackage("dev.novadev.novabank.bukkit.integration..")
            .should().dependOnClassesThat().resideInAPackage("java.lang.reflect..")
            .because("la reflexion doit rester dans des classes adaptateurs identifiees");

    /**
     * novabank-bukkit est compile contre Spigot 1.17.1 et charge sous Spigot/Paper : aucune
     * reference aux planificateurs regionalises de Folia/Paper, ni au module Folia (charge par
     * son nom uniquement sous Folia).
     */
    static final ArchRule NO_FOLIA_REFERENCE = noClasses()
            .should().dependOnClassesThat()
            .resideInAnyPackage("io.papermc.paper.threadedregions..", "dev.novadev.novabank.platform.folia..")
            .because("seul le module novabank-platform-folia utilise l'API Folia");

    /**
     * Aucun pilote JDBC reference a la compilation dans le code de production (D6 et licences :
     * MariaDB Connector/J LGPL, MySQL Connector/J GPL) : pilotes telecharges, charges par nom.
     */
    static final ArchRule NO_JDBC_DRIVER_REFERENCE = noClasses()
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.mariadb..", "com.mysql..", "org.sqlite..")
            .because("les pilotes JDBC sont telecharges au demarrage et charges par nom dans un chargeur isole");

    /**
     * Aucune reference au pilote MongoDB hors du module novabank-storage-mongo (D18) : le pilote est
     * telecharge et ne vit que dans le chargeur isole du module.
     */
    static final ArchRule NO_MONGODB_REFERENCE = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("com.mongodb..", "org.bson..",
                    "dev.novadev.novabank.storage.mongo..")
            .because("le backend MongoDB est un module embarque charge par nom dans un chargeur isole");

    /** Aucun etat statique modifiable : pas de singleton. */
    static final ArchRule NO_MUTABLE_STATIC_STATE = fields().that().areStatic().should().beFinal()
            .because("tout l'etat appartient a l'instance assemblee par NovaBankBootstrap");

    /** Seul le point d'entree etend JavaPlugin. */
    static final ArchRule SINGLE_ENTRY_POINT = noClasses()
            .that().doNotHaveFullyQualifiedName(NovaBankPlugin.class.getName())
            .should().beAssignableTo(JavaPlugin.class);

    /** Le moteur ne connait que PluginHost : un module SuperiorSkyblock2 ou un addon BentoBox n'est pas un JavaPlugin. */
    static final ArchRule ENGINE_WITHOUT_JAVA_PLUGIN = noClasses()
            .that().doNotHaveFullyQualifiedName(NovaBankPlugin.class.getName())
            .and().doNotHaveFullyQualifiedName(PluginHost.class.getName())
            .and().haveNameNotMatching(java.util.regex.Pattern.quote(PluginHost.class.getName()) + "\\$.*")
            .should().dependOnClassesThat().areAssignableTo(JavaPlugin.class);

    @Test
    void les_modules_sans_plateforme_ont_bien_ete_importes_et_respectent_la_regle() {
        assertThat(PLATFORM_FREE_MODULES.stream().map(type -> type.getPackageName()).distinct())
                .anySatisfy(name -> assertThat(name).startsWith("dev.novadev.novabank.core"))
                .anySatisfy(name -> assertThat(name).startsWith("dev.novadev.novabank.config"))
                .anySatisfy(name -> assertThat(name).startsWith("dev.novadev.novabank.storage.sql"))
                .anySatisfy(name -> assertThat(name).startsWith("dev.novadev.novabank.libraries"));
        MODULES_WITHOUT_PLATFORM.check(PLATFORM_FREE_MODULES);
    }

    @Test
    void aucun_module_de_production_ne_reference_un_pilote_jdbc() {
        NO_JDBC_DRIVER_REFERENCE.check(PLATFORM_FREE_MODULES);
        NO_JDBC_DRIVER_REFERENCE.check(BUKKIT_MODULE);
    }

    @Test
    void aucun_module_du_plugin_ne_reference_le_pilote_ou_le_module_mongodb() {
        NO_MONGODB_REFERENCE.check(PLATFORM_FREE_MODULES);
        NO_MONGODB_REFERENCE.check(BUKKIT_MODULE);
    }

    @Test
    void la_regle_des_pilotes_detecte_une_reference_au_pilote_mariadb() {
        JavaClasses withDriver = new ClassFileImporter().importClasses(DriverReferenceProbe.class);

        assertThat(NO_JDBC_DRIVER_REFERENCE.evaluate(withDriver).hasViolation()).isTrue();
    }

    /** Sonde volontairement fautive : reference le pilote MariaDB du classpath de test. */
    static final class DriverReferenceProbe {
        org.mariadb.jdbc.Driver driver;
    }

    @Test
    void la_regle_sans_plateforme_detecte_une_dependance_bukkit() {
        assertThat(MODULES_WITHOUT_PLATFORM.evaluate(BUKKIT_MODULE).hasViolation()).isTrue();
    }

    @Test
    void reflexion_isolee_et_aucun_etat_statique_modifiable() {
        REFLECTION_ISOLATED.check(BUKKIT_MODULE);
        NO_FOLIA_REFERENCE.check(BUKKIT_MODULE);
        NO_MUTABLE_STATIC_STATE.check(BUKKIT_MODULE);
        SINGLE_ENTRY_POINT.check(BUKKIT_MODULE);
        ENGINE_WITHOUT_JAVA_PLUGIN.check(BUKKIT_MODULE);
    }

    @Test
    void le_point_d_entree_est_mince() {
        Method[] methods = Arrays.stream(NovaBankPlugin.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .toArray(Method[]::new);
        Field[] fields = Arrays.stream(NovaBankPlugin.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .toArray(Field[]::new);

        assertThat(methods).extracting(Method::getName).containsExactlyInAnyOrder("onLoad", "onEnable", "onDisable");
        assertThat(fields).hasSize(1);
        assertThat(Modifier.isStatic(fields[0].getModifiers())).isFalse();
        assertThat(fields[0].getType().getSimpleName()).isEqualTo("NovaBankLauncher");
    }
}
