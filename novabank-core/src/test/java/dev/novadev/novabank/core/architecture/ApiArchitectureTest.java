package dev.novadev.novabank.core.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import dev.novadev.novabank.api.archfixture.CoreDependentFixture;
import org.junit.jupiter.api.Test;

/**
 * Garantit que l'API publique ne depend jamais du coeur.
 *
 * <p>Maven l'interdit deja (pas de dependance api vers core, cycle impossible) ; la
 * regle double cette garantie au niveau du bytecode (un acces par reflexion via un
 * nom de classe en texte n'est pas detectable ainsi). Les classes de l'API sont importees depuis le classpath de test du coeur ;
 * ArchUnit echoue si aucune classe n'est trouvee, et un second test applique la regle
 * a une fixture fautive pour prouver qu'elle detecte une violation.
 */
@AnalyzeClasses(packages = "dev.novadev.novabank.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ApiArchitectureTest {

    @ArchTest
    static final ArchRule API_DOES_NOT_DEPEND_ON_CORE = noClasses()
            .that().resideInAPackage("dev.novadev.novabank.api..")
            .should().dependOnClassesThat().resideInAPackage("dev.novadev.novabank.core..")
            .because("l'API publique doit rester utilisable sans le coeur, qui est obfusque");

    @Test
    void la_regle_detecte_une_dependance_vers_le_coeur() {
        EvaluationResult result = API_DOES_NOT_DEPEND_ON_CORE
                .evaluate(new ClassFileImporter().importClasses(CoreDependentFixture.class));

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anySatisfy(detail -> assertThat(detail).contains("dev.novadev.novabank.core.money.Money"));
    }
}
