package com.kars.vault_annotations

/**
 * Marks a property in a [@VaultSchema][VaultSchema]-annotated data class as a preference item.
 *
 * Properties annotated with @VaultItem will have corresponding getters, setters, and Flow
 * properties generated in the preferences wrapper class. The KSP processor will generate
 * type-safe accessors for the property.
 *
 * Supported types:
 * - String
 * - Int
 * - Boolean
 * - Float
 * - Long
 * - Double
 * - Set<String>
 *
 * Example usage:
 * ```kotlin
 * @VaultSchema(name = "User")
 * data class UserPreferences(
 *     // Uses property name as key, with empty default value
 *     @VaultItem(defaultValue = "")
 *     val username: String,
 *     
 *     // Uses custom key name
 *     @VaultItem(key = "user_age", defaultValue = "0")
 *     val age: Int,
 *     
 *     // Boolean preference with false default
 *     @VaultItem(defaultValue = "false")
 *     val isPremium: Boolean,
 *     
 *     // Set<String> with empty set default
 *     @VaultItem(defaultValue = "emptySet()")
 *     val tags: Set<String>
 * )
 * ```
 *
 * @param key The preference key to use in DataStore. If not provided, the property name is used.
 * @param defaultValue The default value as a string. Must be convertible to the property type.
 *                    For Set<String>, use "emptySet()" or a comma-separated list in curly braces 
 *                    like: "{\"value1\", \"value2\"}"
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class VaultItem(
    val key: String = "",
    val defaultValue: String,
)
