package com.relayops.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.relayops", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule shared_must_not_depend_on_business_modules =
            noClasses()
                    .that().resideInAPackage("com.relayops.shared..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.relayops.identity..",
                            "com.relayops.upstream..",
                            "com.relayops.monitoring..",
                            "com.relayops.notification..",
                            "com.relayops.assistant..",
                            "com.relayops.operations..",
                            "com.relayops.scheduling.."
                    );

    @ArchTest
    static final ArchRule business_modules_must_not_depend_on_assistant =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.relayops.identity..",
                            "com.relayops.upstream..",
                            "com.relayops.monitoring..",
                            "com.relayops.notification..",
                            "com.relayops.operations..",
                            "com.relayops.scheduling.."
                    )
                    .should().dependOnClassesThat().resideInAPackage("com.relayops.assistant..");

    @ArchTest
    static final ArchRule top_level_modules_must_be_free_of_cycles =
            slices().matching("com.relayops.(*)..").should().beFreeOfCycles();
}
