package dev.novadev.novabank.platform.folia;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import dev.novadev.novabank.bukkit.scheduler.PlatformSchedulers;
import org.junit.jupiter.api.Test;

/**
 * Separation du module Folia : seul ce module utilise l'API des planificateurs par region,
 * et rien hors de lui ne le reference statiquement (il est charge par son nom).
 */
class FoliaArchitectureTest {

    private static final ImportOption MAIN_ONLY = new ImportOption.DoNotIncludeTests();

    private static final JavaClasses OTHER_MODULES = new ClassFileImporter().withImportOption(MAIN_ONLY)
            .importPackages("dev.novadev.novabank.bukkit", "dev.novadev.novabank.core", "dev.novadev.novabank.config",
                    "dev.novadev.novabank.storage", "dev.novadev.novabank.libraries", "dev.novadev.novabank.api");

    private static final JavaClasses FOLIA_MODULE = new ClassFileImporter().withImportOption(MAIN_ONLY)
            .importPackages("dev.novadev.novabank.platform.folia");

    static final ArchRule FOLIA_API_CONFINED = noClasses()
            .that().resideOutsideOfPackage("dev.novadev.novabank.platform.folia..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("io.papermc.paper.threadedregions..", "dev.novadev.novabank.platform.folia..")
            .because("l'API Folia n'existe pas sous Spigot/Paper 1.17-1.19 : seul le module Folia y touche");

    @Test
    void les_autres_modules_ne_touchent_ni_l_api_folia_ni_le_module_folia() {
        assertThat(OTHER_MODULES.stream().map(type -> type.getPackageName()).distinct())
                .anySatisfy(name -> assertThat(name).startsWith("dev.novadev.novabank.bukkit"))
                .anySatisfy(name -> assertThat(name).startsWith("dev.novadev.novabank.core"));
        FOLIA_API_CONFINED.check(OTHER_MODULES);
    }

    @Test
    void la_regle_detecte_l_usage_de_l_api_folia() {
        assertThat(FOLIA_API_CONFINED.evaluate(new ClassFileImporter().withImportOption(MAIN_ONLY)
                .importPackages("dev.novadev.novabank")).hasViolation()).isFalse();
        assertThat(noClasses().should().dependOnClassesThat()
                .resideInAPackage("io.papermc.paper.threadedregions..")
                .evaluate(FOLIA_MODULE).hasViolation()).isTrue();
    }

    @Test
    void le_nom_charge_par_novabank_bukkit_est_bien_la_fabrique_folia() {
        assertThat(FOLIA_MODULE.contain(PlatformSchedulers.FOLIA_FACTORY)).isTrue();
        assertThat(FoliaSchedulerFactory.class.getName()).isEqualTo(PlatformSchedulers.FOLIA_FACTORY);
    }
}
