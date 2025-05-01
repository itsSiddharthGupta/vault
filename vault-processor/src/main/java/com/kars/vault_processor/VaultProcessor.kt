package com.kars.vault_processor

import com.kars.vault_annotations.VaultItem
import com.kars.vault_annotations.VaultSchema
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * VaultProcessor - A KSP (Kotlin Symbol Processing) processor that generates type-safe DataStore preferences wrappers.
 *
 * This processor scans for classes annotated with @VaultSchema and generates:
 * 1. A wrapper class that provides type-safe access to DataStore preferences
 * 2. A Context extension property to easily access the wrapper
 * 3. Flow properties and suspend functions for getting/setting preferences
 * 4. Methods for accessing/updating the entire data class
 *
 * The code generation is handled via KotlinPoet, which provides a type-safe DSL for generating Kotlin code.
 */

// Define KotlinPoet ClassNames for DataStore and other types
val DATA_STORE = ClassName("androidx.datastore.core", "DataStore")
val PREFERENCES = ClassName("androidx.datastore.preferences.core", "Preferences")
val PREFERENCES_KEY = ClassName("androidx.datastore.preferences.core.Preferences", "Key")
val PREFERENCES_DATASTORE = MemberName("androidx.datastore.preferences", "preferencesDataStore") // Note: Needs Context receiver type later
val FLOW = ClassName("kotlinx.coroutines.flow", "Flow")
val CONTEXT = ClassName("android.content", "Context")

/**
 * Holds the definition of a single preference item extracted from a @VaultItem annotation.
 * Contains all the information needed to generate getter/setter methods and keys.
 */
internal data class PreferenceDefinition(
    val baseName: String, // e.g., username
    val keyName: String, // e.g., "user_name" or "username"
    val keyPropertyName: String, // e.g., USERNAME
    val typeName: TypeName, // e.g., String, Int
    val typeNameQualified: String?, // e.g., kotlin.String
    val typeInfo: PrefTypeInfo,
    val defaultValueLiteral: CodeBlock // Parsed default value as a KotlinPoet CodeBlock literal
)

/**
 * Maps Kotlin types to DataStore preference key functions and provides type conversion utilities.
 * For each supported type, we store:
 * - The DataStore key factory function name (e.g., "stringPreferencesKey")
 * - A lambda to parse default values from strings
 * - The corresponding KotlinPoet TypeName for Flow return types
 */
data class PrefTypeInfo(
    val keyFactoryFunction: String, // e.g., "intPreferencesKey"
    val defaultValueParser: (String) -> Any?, // Lambda to parse defaultValue String
    val flowType: TypeName
)

// Mapping from Kotlin fully qualified names to their DataStore preference key info
val supportedTypes = mapOf(
    Int::class.qualifiedName to PrefTypeInfo("intPreferencesKey", String::toInt, INT),
    String::class.qualifiedName to PrefTypeInfo("stringPreferencesKey", { it.removeSurrounding("\"") }, STRING), // Handle quotes
    Boolean::class.qualifiedName to PrefTypeInfo("booleanPreferencesKey", String::toBoolean, BOOLEAN),
    Float::class.qualifiedName to PrefTypeInfo("floatPreferencesKey", String::toFloat, FLOAT),
    Long::class.qualifiedName to PrefTypeInfo("longPreferencesKey", String::toLong, LONG),
    Double::class.qualifiedName to PrefTypeInfo("doublePreferencesKey", String::toDouble, DOUBLE),
    // Set<String> needs special handling
    Set::class.qualifiedName to PrefTypeInfo("stringSetPreferencesKey", { parseStringSet(it) }, SET.parameterizedBy(STRING).copy(nullable = true)) // Nullable default for Set
)

/**
 * Helper function to parse string set representations from annotation values.
 * Handles formats like: "{\"a\", \"b\"}" or "emptySet()" or "null"
 */
fun parseStringSet(value: String): Set<String>? {
    if (value.equals("null", ignoreCase = true) || value == "emptySet()") return emptySet() // Handle null/empty representations
    return value.removeSurrounding("{", "}").split(",")
        .map { it.trim().removeSurrounding("\"") }
        .filter { it.isNotEmpty() }.toSet().ifEmpty { null } // Return null if parsing results in empty set but wasn't explicitly emptySet()
}

/**
 * Main processor class that implements the KSP SymbolProcessor interface.
 * Processes @VaultSchema annotations and generates corresponding DataStore preference wrappers.
 */
class VaultProcessor(private val codeGenerator: CodeGenerator, private val logger: KSPLogger) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        logger.info("[VaultProcessor] Started round.")
        val symbols = resolver.getSymbolsWithAnnotation(VaultSchema::class.qualifiedName!!)
        val (validSymbols, invalidSymbols) = symbols.partition { it.validate() }
        validSymbols
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.CLASS && it.modifiers.contains(Modifier.DATA) }
            .forEach { classDeclaration ->
                try {
                    processSchema(classDeclaration)
                } catch (e: Exception) {
                    logger.error("[VaultProcessor] Error processing ${classDeclaration.qualifiedName?.asString()}: ${e.stackTraceToString()}", classDeclaration)
                }
            }
        logger.info("[VaultProcessor] Finished round.")
        return invalidSymbols
    }

    /**
     * Main processing function that generates a DataStore wrapper for a single @VaultSchema-annotated data class.
     *
     * KotlinPoet Generation Structure:
     * 1. Create a FileSpec builder
     * 2. Extract and validate preference definitions from the data class parameters
     * 3. Generate a DataStore property with Context receiver
     * 4. Generate the wrapper class with:
     *    - Keys object containing preference keys
     *    - Flow properties for each preference (nameFlow, ageFlow, etc.)
     *    - Suspend getter and setter methods for each preference
     *    - Whole data class Flow property and suspend methods
     *    - Helper methods like clear()
     * 5. Generate a Context extension property for easy access to the wrapper
     * 6. Write the generated code to a file
     */
    @OptIn(KspExperimental::class)
    private fun processSchema(schemaClass: KSClassDeclaration) {
        val schemaAnnotation = schemaClass.getAnnotationsByType(VaultSchema::class).first()
        val schemaName = schemaAnnotation.name
        val dataStoreFileName = schemaName.toSnakeCase()
        val generatedClassName = "${schemaName}Prefs"
        val generatedFileName = "${generatedClassName}_Generated"
        val dataClassClassName = schemaClass.toClassName()

        // Step 1: Set up the FileSpec builder with imports
        val packageName = schemaClass.packageName.asString() // alternate way is to use schemaInterface.containingFile?.packageName?.asString()
        val fileSpecBuilder = FileSpec
            .builder(packageName, generatedFileName)
            .addFileComment("Generated by VaultProcessor for ${schemaClass.simpleName.asString()}. Do Not Edit.")
            .addImport("kotlinx.coroutines.flow", "Flow", "map", "first")
            .addImport("androidx.datastore.preferences.core", "edit")

        // Step 2: Extract preference definitions from the data class constructor parameters
        val preferences = mutableListOf<PreferenceDefinition>()
        val constructorParams = schemaClass.primaryConstructor?.parameters
        if (constructorParams == null || constructorParams.isEmpty()) {
            logger.error("[VaultProcessor] Data class ${schemaClass.simpleName.asString()} must have a primary constructor with properties.", schemaClass)
            return
        }

        constructorParams.forEach { param ->
            val prefAnnotation = param.getAnnotationsByType(VaultItem::class).firstOrNull()
            if (prefAnnotation != null) {
                val propertyName = param.name?.asString()
                if (propertyName == null) {
                    logger.error("[VaultProcessor] Property in constructor of ${schemaClass.simpleName.asString()} has no name.", param)
                    return@forEach
                }
                val propertyType = param.type.resolve()
                val valueTypeQualifiedName = propertyType.declaration.qualifiedName?.asString()

                val typeInfo = supportedTypes[valueTypeQualifiedName]
                if (typeInfo != null) {
                    if (valueTypeQualifiedName == Set::class.qualifiedName) {
                        // If the type IS Set, verify its type argument IS String
                        val isStringArgument = propertyType.arguments.firstOrNull()?.type?.resolve()?.declaration?.qualifiedName?.asString() == String::class.qualifiedName
                        if (!isStringArgument) {
                            logger.error("[VaultProcessor] Unsupported type '${propertyType.toTypeName()}' for property '$propertyName'. Only Set<String> is supported, not Set of other types.", param)
                            return@forEach
                        }
                    }

                    preferences.add(createPreferenceDefinition(propertyName, prefAnnotation, propertyType, typeInfo, logger, param))
                } else {
                    // Type not found directly in our supportedTypes map
                    logger.error(
                        "[VaultProcessor] Unsupported type '${propertyType.toTypeName()}' for property '$propertyName'. Supported: Int, String, Boolean, Float, Long, Double, Set<String>.",
                        param
                    )
                    // No return here, loop continues to next parameter
                }
            }
        }

        if (preferences.isEmpty()) {
            logger.warn("[VaultProcessor] No valid @VaultItem properties found in ${schemaClass.simpleName.asString()}. Skipping generation.")
            return
        }

        // Add specific imports for each preference key type used
        val keyFunctionsUsed = preferences.map { it.typeInfo.keyFactoryFunction }.toSet()
        keyFunctionsUsed.forEach { keyFunction ->
            fileSpecBuilder.addImport("androidx.datastore.preferences.core", keyFunction)
        }

        // Step 3: Generate DataStore property with Context receiver
        val dataStorePropertyName = "${schemaName.replaceFirstChar { it.lowercase() }}DataStore"
        fileSpecBuilder.addProperty(
            PropertySpec.builder(dataStorePropertyName, DATA_STORE.parameterizedBy(PREFERENCES), KModifier.INTERNAL)
                .receiver(CONTEXT)
                .delegate(
                    CodeBlock.builder()
                        .add("%M(name = %S)", PREFERENCES_DATASTORE, dataStoreFileName)
                        .build()
                )
                .build()
        )

        // Step 4: Generate Wrapper Class
        val wrapperClassBuilder = TypeSpec.classBuilder(generatedClassName)
            .addModifiers(KModifier.PUBLIC)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("dataStore", DATA_STORE.parameterizedBy(PREFERENCES))
                    .build()
            )
            .addProperty(
                PropertySpec.builder("dataStore", DATA_STORE.parameterizedBy(PREFERENCES), KModifier.PRIVATE)
                    .initializer("dataStore")
                    .build()
            )

        // Step 4.1: Generate Keys object containing preference keys
        val keysObjectBuilder = TypeSpec.objectBuilder("Keys").addModifiers(KModifier.PRIVATE)

        preferences.forEach { pref ->
            keysObjectBuilder.addProperty(
                PropertySpec.builder(pref.keyPropertyName, PREFERENCES_KEY.parameterizedBy(pref.typeName))
                    .initializer("%N(%S)", pref.typeInfo.keyFactoryFunction, pref.keyName)
                    .build()
            )
        }

        wrapperClassBuilder.addType(keysObjectBuilder.build())

        // Step 4.2: Generate Flow properties and accessor/mutator methods for each preference
        preferences.forEach { pref ->
            val baseNameTitleCase = pref.baseName.replaceFirstChar { it.titlecase() }

            // Generate Flow property for the preference
            wrapperClassBuilder.addProperty(
                PropertySpec.builder(
                    name = "${pref.baseName}Flow",
                    type = FLOW.parameterizedBy(pref.typeName),
                ).getter(
                    FunSpec.getterBuilder()
                        .addStatement("return dataStore.data.map { prefs -> prefs[Keys.%N] ?: %L }", pref.keyPropertyName, pref.defaultValueLiteral)
                        .build()
                ).build()
            )

            // Generate suspend getter method - Returns Type directly
            wrapperClassBuilder.addFunction(
                FunSpec.builder("get${baseNameTitleCase}")
                    .addModifiers(KModifier.PUBLIC, KModifier.SUSPEND)
                    .returns(pref.typeName) // Returns CorrectType
                    .addStatement("return ${pref.baseName}Flow.first()")
                    .build()
            )

            // Generate suspend update method - Takes Type as parameter
            wrapperClassBuilder.addFunction(
                FunSpec.builder("update${baseNameTitleCase}")
                    .addModifiers(KModifier.PUBLIC, KModifier.SUSPEND)
                    .addParameter(pref.baseName, pref.typeName) // Parameter named after the property
                    .addCode(
                        CodeBlock.builder()
                            .addStatement("dataStore.edit { settings ->")
                            .addStatement("  settings[Keys.%N] = ${pref.baseName}", pref.keyPropertyName)
                            .addStatement("}")
                            .build()
                    )
                    .build()
            )
        }

        // Step 4.3: Generate Data Class Helper Methods

        // Generate update method for the entire data class
        val updateAllFunc = FunSpec.builder("update$generatedClassName")
            .addModifiers(KModifier.PUBLIC, KModifier.SUSPEND)
            .addParameter("newData", dataClassClassName)
            .addCode(
                CodeBlock.builder()
                    .addStatement("dataStore.edit { settings ->")
                    .apply {
                        preferences.forEach { pref ->
                            addStatement("  settings[Keys.%N] = newData.%N", pref.keyPropertyName, pref.baseName)
                        }
                    }
                    .addStatement("}")
                    .build()
            )
            .build()
        wrapperClassBuilder.addFunction(updateAllFunc)

        // Generate Flow property for the entire data class
        val schemaPropertyName = schemaName.replaceFirstChar { it.lowercase() }
        val getAllFlow = PropertySpec.builder("${schemaPropertyName}DataFlow", FLOW.parameterizedBy(dataClassClassName))
            .getter(
                FunSpec
                    .getterBuilder()
                    .addCode(
                        CodeBlock.builder()
                            // Using proper control flow for complex blocks
                            .beginControlFlow("return dataStore.data.map { prefs ->") 
                            .addStatement("%T(", dataClassClassName)
                            .indent()
                            .apply {
                                preferences.forEachIndexed { index, pref ->
                                    val lineEnd = if (index < preferences.size - 1) "," else ""
                                    addStatement("%N = prefs[Keys.%N] ?: %L%L", pref.baseName, pref.keyPropertyName, pref.defaultValueLiteral, lineEnd)
                                }
                            }
                            .unindent()
                            .addStatement(")")
                            .endControlFlow()
                            .build()
                    ).build()
            ).build()
        wrapperClassBuilder.addProperty(getAllFlow)

        // Generate suspend getter method for the entire data class
        val getAllOnceFunc = FunSpec.builder("get${schemaName}Data")
            .addModifiers(KModifier.PUBLIC, KModifier.SUSPEND)
            .returns(dataClassClassName)
            .addStatement("return ${schemaPropertyName}DataFlow.first()")
            .build()
        wrapperClassBuilder.addFunction(getAllOnceFunc)

        // Generate clear() Method - Clears all preferences
        wrapperClassBuilder.addFunction(
            FunSpec.builder("clear")
                .addModifiers(KModifier.PUBLIC, KModifier.SUSPEND)
                .addCode(CodeBlock.builder().addStatement("dataStore.edit { it.clear() }").build())
                .build()
        )

        // Add the generated class to the file
        fileSpecBuilder.addType(wrapperClassBuilder.build())

        // Step 5: Generate Context Extension Property for the Generated Wrapper Class
        val contextExtensionName = schemaName.replaceFirstChar { it.lowercase() }
        fileSpecBuilder.addProperty(
            PropertySpec.builder(contextExtensionName, ClassName(packageName, generatedClassName))
                .receiver(CONTEXT)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement("return %T(%N)", ClassName(packageName, generatedClassName), dataStorePropertyName)
                        .build()
                )
                .build()
        )

        // Step 6: Write the generated code to a file
        try {
            fileSpecBuilder.build().writeTo(codeGenerator, Dependencies(false, schemaClass.containingFile!!))
            logger.warn("[VaultProcessor] Generated file: ${generatedFileName}.kt in $packageName")
        } catch (e: Exception) {
            logger.error("[VaultProcessor] Error writing file: ${e.stackTraceToString()}", schemaClass)
        }
    }

    /**
     * Helper function to parse annotation and create preference definition object.
     * Handles type-specific default value parsing and formatting.
     */
    private fun createPreferenceDefinition(
        propertyName: String,
        prefAnnotation: VaultItem,
        propertyType: KSType,
        typeInfo: PrefTypeInfo,
        logger: KSPLogger,
        containingDeclaration: KSNode
    ): PreferenceDefinition {
        val baseName = propertyName // Base name is property name
        val keyName = prefAnnotation.key.takeIf { it.isNotBlank() } ?: baseName
        val keyPropertyName = baseName.uppercase() // e.g., LAUNCH_COUNT

        val defaultValueString = prefAnnotation.defaultValue

        val defaultValueLiteral: CodeBlock = try {
            // Special handling for Set<String> default value representation
            if (propertyType.declaration.qualifiedName?.asString() == Set::class.qualifiedName) {
                val parsedSet = typeInfo.defaultValueParser(defaultValueString) as? Set<*>
                if (parsedSet == null || parsedSet.isEmpty()) {
                    CodeBlock.of("emptySet()")
                } else {
                    // Build CodeBlock like: setOf("a", "b", "c")
                    val format = parsedSet.joinToString(", ") { "%S" }
                    CodeBlock.of("setOf($format)", *parsedSet.toTypedArray())
                }
            }
            // Standard types
            else {
                when (typeInfo.flowType) {
                    STRING -> CodeBlock.of("%S", typeInfo.defaultValueParser(defaultValueString)) // Use %S for String literal
                    FLOAT -> CodeBlock.of("%Lf", typeInfo.defaultValueParser(defaultValueString)) // Use %L for Long/Float literals
                    LONG -> CodeBlock.of("%LL", typeInfo.defaultValueParser(defaultValueString))
                    else -> CodeBlock.of("%L", typeInfo.defaultValueParser(defaultValueString)) // Use %L for others (Int, Boolean, Double)
                }
            }
        } catch (e: Exception) {
            logger.error("[VaultProcessor] Failed to parse defaultValue \"$defaultValueString\" for preference '$baseName'. Using default for type. Error: ${e.message}", containingDeclaration)
            // Provide a fallback default literal based on type
            when (typeInfo.flowType) {
                INT -> CodeBlock.of("0")
                STRING -> CodeBlock.of("\"\"")
                BOOLEAN -> CodeBlock.of("false")
                FLOAT -> CodeBlock.of("0.0f")
                LONG -> CodeBlock.of("0L")
                DOUBLE -> CodeBlock.of("0.0")
                SET.parameterizedBy(STRING) -> CodeBlock.of("emptySet()")
                else -> CodeBlock.of("null") // Should not happen with validation
            }
        }

        return PreferenceDefinition(
            baseName = baseName,
            keyName = keyName,
            keyPropertyName = keyPropertyName,
            typeName = typeInfo.flowType,
            typeNameQualified = propertyType.declaration.qualifiedName?.asString(),
            typeInfo = typeInfo,
            defaultValueLiteral = defaultValueLiteral
        )
    }

    /**
     * Helper extension to convert camelCase or PascalCase strings to snake_case.
     * Used for DataStore file naming.
     */
    internal fun String.toSnakeCase(): String {
        return replace(Regex("([a-z])([A-Z]+)"), "$1_$2").lowercase()
    }
}