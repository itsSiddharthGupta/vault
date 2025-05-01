package com.kars.vault_processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Provider for the Vault Kotlin Symbol Processor.
 *
 * This class serves as the entry point for the KSP (Kotlin Symbol Processing) API.
 * When KSP runs as part of the build process, it uses this provider to create instances
 * of [VaultProcessor] which handles the actual code generation.
 *
 * The processor generates type-safe DataStore preference wrappers for data classes
 * annotated with [@VaultSchema][com.kars.vault_annotations.VaultSchema].
 */
class VaultProcessorProvider: SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return VaultProcessor(environment.codeGenerator, environment.logger)
    }
}