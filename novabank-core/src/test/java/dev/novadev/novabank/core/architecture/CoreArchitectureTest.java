package dev.novadev.novabank.core.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import dev.novadev.novabank.core.architecture.fixture.PlatformDependentFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Garantit que le coeur reste independant de la plateforme serveur (decision D3).
 *
 * <p>La regle est appliquee aux classes de production du module (les classes de
 * test sont exclues). Le test parametre l'applique ensuite a une fixture
 * volontairement fautive pour prouver qu'elle detecte bien chaque famille de
 * packages interdite, et qu'elle n'est donc pas vide de sens.
 */
@AnalyzeClasses(packages = "dev.novadev.novabank.core", importOptions = ImportOption.DoNotIncludeTests.class)
class CoreArchitectureTest {

    @ArchTest
    static final ArchRule CORE_IS_PLATFORM_INDEPENDENT = noClasses()
            .that().resideInAPackage("dev.novadev.novabank.core..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.bukkit..", "io.papermc..", "org.spigotmc..", "net.md_5..")
            .because("le coeur doit rester testable en Java pur, sans Bukkit/Spigot/Paper/BungeeCord");

    @ParameterizedTest
    @ValueSource(classes = {
            org.bukkit.novabanktest.BukkitProbe.class,
            io.papermc.novabanktest.PaperProbe.class,
            org.spigotmc.novabanktest.SpigotProbe.class,
            net.md_5.novabanktest.BungeeProbe.class
    })
    void la_regle_detecte_chaque_package_interdit(Class<?> forbiddenType) {
        EvaluationResult result = CORE_IS_PLATFORM_INDEPENDENT
                .evaluate(new ClassFileImporter().importClasses(PlatformDependentFixture.class));

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anySatisfy(detail -> assertThat(detail).contains(forbiddenType.getName()));
    }
}
