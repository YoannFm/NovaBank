package dev.novadev.novabank.libraries.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import dev.novadev.novabank.libraries.archfixture.ForbiddenDependenciesFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Le module ne depend ni de la plateforme serveur ni d'une classe de pilote : les pilotes
 * ne sont connus que par leur nom et charges a l'execution (decision D6).
 */
@AnalyzeClasses(packages = "dev.novadev.novabank.libraries", importOptions = ImportOption.DoNotIncludeTests.class)
class LibrariesArchitectureTest {

    @ArchTest
    static final ArchRule NO_PLATFORM_NOR_DRIVER_DEPENDENCY = noClasses()
            .that().resideInAPackage("dev.novadev.novabank.libraries..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.bukkit..", "io.papermc..", "org.spigotmc..", "net.md_5..",
                    "org.sqlite..", "org.mariadb..", "com.mysql..")
            .because("les pilotes sont charges par nom dans un chargeur isole, sans Bukkit");

    @ParameterizedTest
    @ValueSource(strings = {"org.bukkit.", "io.papermc.", "org.spigotmc.", "net.md_5.", "org.sqlite.", "org.mariadb.",
        "com.mysql."})
    void la_regle_detecte_chaque_dependance_interdite(String forbiddenPackage) {
        EvaluationResult result = NO_PLATFORM_NOR_DRIVER_DEPENDENCY
                .evaluate(new ClassFileImporter().importClasses(ForbiddenDependenciesFixture.class));

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anySatisfy(detail -> assertThat(detail).contains(forbiddenPackage));
    }
}
