package dev.novadev.novabank.storage.file;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Le stockage fichier ne depend que du JDK, de Gson, de SnakeYAML (fournis par le serveur), de
 * l'API et du coeur : jamais de Bukkit, de JDBC ni d'un autre backend.
 */
@AnalyzeClasses(packages = "dev.novadev.novabank.storage.file", importOptions = ImportOption.DoNotIncludeTests.class)
class FileArchitectureTest {

    @ArchTest
    static final ArchRule ONLY_JDK_FORMATS_API_AND_CORE = classes()
            .that().resideInAPackage("dev.novadev.novabank.storage.file..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java.io..", "java.lang..", "java.math..", "java.nio..", "java.security..",
                    "java.time..", "java.util..", "com.google.gson..", "org.yaml.snakeyaml..",
                    "dev.novadev.novabank.api..", "dev.novadev.novabank.core..", "dev.novadev.novabank.storage.file..")
            .because("module charge dans le plugin, sans Bukkit, et dont les formats sont fournis par le serveur");
}
