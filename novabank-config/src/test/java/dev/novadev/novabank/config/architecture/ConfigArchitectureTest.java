package dev.novadev.novabank.config.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Le module de configuration reste en Java pur : le chargement YAML et la disponibilite
 * des items sont fournis par la plateforme. L'efficacite de cette forme de regle est
 * prouvee par {@code CoreArchitectureTest} (fixtures fautives).
 */
@AnalyzeClasses(packages = "dev.novadev.novabank.config", importOptions = ImportOption.DoNotIncludeTests.class)
class ConfigArchitectureTest {

    @ArchTest
    static final ArchRule CONFIG_IS_PLATFORM_INDEPENDENT = noClasses()
            .that().resideInAPackage("dev.novadev.novabank.config..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.bukkit..", "io.papermc..", "org.spigotmc..", "net.md_5..", "org.yaml..")
            .because("la configuration doit rester testable en Java pur ; le YAML est lu par la plateforme");
}
