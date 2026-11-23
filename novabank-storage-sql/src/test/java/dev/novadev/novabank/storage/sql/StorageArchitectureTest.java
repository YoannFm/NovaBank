package dev.novadev.novabank.storage.sql;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import dev.novadev.novabank.storage.sql.archfixture.ForbiddenDependenciesFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Le stockage SQL reste independant de la plateforme serveur et de tout pilote JDBC
 * precis : le pilote est fourni a l'execution (decision D6).
 */
@AnalyzeClasses(packages = "dev.novadev.novabank.storage.sql", importOptions = ImportOption.DoNotIncludeTests.class)
class StorageArchitectureTest {

    @ArchTest
    static final ArchRule STORAGE_HAS_NO_PLATFORM_NOR_DRIVER_DEPENDENCY = noClasses()
            .that().resideInAPackage("dev.novadev.novabank.storage.sql..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.bukkit..", "io.papermc..", "org.spigotmc..", "net.md_5..", "org.sqlite..",
                    "org.mariadb..", "com.mysql..")
            .because("le stockage ne depend ni de Bukkit/Spigot/Paper/BungeeCord ni d'un pilote nomme en dur");

    @ParameterizedTest
    @ValueSource(strings = {"org.bukkit.", "io.papermc.", "org.spigotmc.", "net.md_5.", "org.sqlite.", "org.mariadb.",
        "com.mysql."})
    void la_regle_detecte_chaque_dependance_interdite(String forbiddenPackage) {
        EvaluationResult result = STORAGE_HAS_NO_PLATFORM_NOR_DRIVER_DEPENDENCY
                .evaluate(new ClassFileImporter().importClasses(ForbiddenDependenciesFixture.class));

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anySatisfy(detail -> assertThat(detail).contains(forbiddenPackage));
    }
}
