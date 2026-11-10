package com.relayops.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Module boundary rules.
 *
 * <p>Empty-module rules use {@code allowEmptyShould(true)} so the skeleton can satisfy them
 * before each module is filled in. When a module grows, the rule starts guarding it automatically.</p>
 */
@AnalyzeClasses(packages = "com.relayops", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    private static final String[] BUSINESS_MODULES = {
            "com.relayops.identity..",
            "com.relayops.upstream..",
            "com.relayops.monitoring..",
            "com.relayops.notification..",
            "com.relayops.operations..",
            "com.relayops.scheduling.."
    };

    private static final String[] AI_MODULES = {
            "com.relayops.agent..",
            "com.relayops.assistant..",
            "com.relayops.copilot.."
    };

    @ArchTest
    static final ArchRule shared_must_not_depend_on_business_modules =
            noClasses()
                    .that().resideInAPackage("com.relayops.shared..")
                    .should().dependOnClassesThat().resideInAnyPackage(BUSINESS_MODULES)
                    .allowEmptyShould(true);

    /** Business code must never reach into the AI modules; the dependency points the other way. */
    @ArchTest
    static final ArchRule business_modules_must_not_depend_on_ai_modules =
            noClasses()
                    .that().resideInAnyPackage(BUSINESS_MODULES)
                    .should().dependOnClassesThat().resideInAnyPackage(AI_MODULES)
                    .allowEmptyShould(true);

    /** The agent kernel knows nothing about business data or about the two capability hosts. */
    @ArchTest
    static final ArchRule agent_kernel_must_stay_pure =
            noClasses()
                    .that().resideInAPackage("com.relayops.agent..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.relayops.identity..",
                            "com.relayops.upstream..",
                            "com.relayops.monitoring..",
                            "com.relayops.notification..",
                            "com.relayops.operations..",
                            "com.relayops.scheduling..",
                            "com.relayops.assistant..",
                            "com.relayops.copilot.."
                    )
                    .allowEmptyShould(true);

    /** Channel host must not reach into the platform capability pack. */
    @ArchTest
    static final ArchRule assistant_must_not_depend_on_copilot =
            noClasses()
                    .that().resideInAPackage("com.relayops.assistant..")
                    .should().dependOnClassesThat().resideInAPackage("com.relayops.copilot..")
                    .allowEmptyShould(true);

    /** Platform capability pack must not reach into the channel host. */
    @ArchTest
    static final ArchRule copilot_must_not_depend_on_assistant =
            noClasses()
                    .that().resideInAPackage("com.relayops.copilot..")
                    .should().dependOnClassesThat().resideInAPackage("com.relayops.assistant..")
                    .allowEmptyShould(true);

    /** copilot may only use other modules through their contracts. */
    @ArchTest
    static final ArchRule copilot_must_only_use_module_contracts =
            noClasses()
                    .that().resideInAPackage("com.relayops.copilot..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.relayops.identity.domain..",
                            "com.relayops.identity.application..",
                            "com.relayops.identity.adapter..",
                            "com.relayops.upstream.domain..",
                            "com.relayops.upstream.application..",
                            "com.relayops.upstream.adapter..",
                            "com.relayops.monitoring.domain..",
                            "com.relayops.monitoring.application..",
                            "com.relayops.monitoring.adapter..",
                            "com.relayops.operations.domain..",
                            "com.relayops.operations.application..",
                            "com.relayops.operations.adapter..",
                            "com.relayops.notification.domain..",
                            "com.relayops.notification.application..",
                            "com.relayops.notification.adapter.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule top_level_modules_must_be_free_of_cycles =
            slices().matching("com.relayops.(*)..").should().beFreeOfCycles();
}
