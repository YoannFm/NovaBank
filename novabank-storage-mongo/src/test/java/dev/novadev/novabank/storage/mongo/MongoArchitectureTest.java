package dev.novadev.novabank.storage.mongo;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import dev.novadev.novabank.storage.mongo.archfixture.ForbiddenDependenciesFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Le module MongoDB ne depend que du JDK (hors JDBC), du pilote MongoDB, de l'API et du coeur :
 * ce sont les seules classes visibles depuis son chargeur isole (D18).
 */
@AnalyzeClasses(packages = "dev.novadev.novabank.storage.mongo", importOptions = ImportOption.DoNotIncludeTests.class)
class MongoArchitectureTest {

    @ArchTest
    static final ArchRule MODULE_SEES_ONLY_DRIVER_API_AND_CORE = classes()
            .that().resideInAPackage("dev.novadev.novabank.storage.mongo..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java.lang..", "java.math..", "java.time..", "java.util..",
                    "com.mongodb..", "org.bson..", "dev.novadev.novabank.api..", "dev.novadev.novabank.core..",
                    "dev.novadev.novabank.storage.mongo..")
            .because("le module est charge dans un chargeur isole qui ne partage que l'API et le coeur");

    @ParameterizedTest
    @ValueSource(strings = {"org.bukkit.", "io.papermc.", "org.spigotmc.", "net.md_5.", "java.sql."})
    void la_regle_detecte_chaque_dependance_interdite(String forbiddenPackage) {
        EvaluationResult result = MODULE_SEES_ONLY_DRIVER_API_AND_CORE
                .evaluate(new ClassFileImporter().importClasses(ForbiddenDependenciesFixture.class));

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anySatisfy(detail -> assertThat(detail).contains(forbiddenPackage));
    }
}
