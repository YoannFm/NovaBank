package dev.novadev.novabank.sync.redis;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Le bus Redis ne depend que du JDK, de l'API et du coeur : ni Bukkit, ni client Redis tiers.
 */
@AnalyzeClasses(packages = "dev.novadev.novabank.sync.redis", importOptions = ImportOption.DoNotIncludeTests.class)
class RedisArchitectureTest {

    @ArchTest
    static final ArchRule ONLY_JDK_API_AND_CORE = classes()
            .that().resideInAPackage("dev.novadev.novabank.sync.redis..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java.io..", "java.lang..", "java.net..", "java.nio..", "java.time..", "java.util..",
                    "javax.net..", "dev.novadev.novabank.api..", "dev.novadev.novabank.core..",
                    "dev.novadev.novabank.sync.redis..")
            .because("client RESP sans dependance, charge dans le plugin sans Bukkit");
}
