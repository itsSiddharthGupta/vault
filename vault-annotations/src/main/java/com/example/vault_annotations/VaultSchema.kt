package com.example.vault_annotations

/**
 * Marks a data class as a schema for generating type-safe DataStore preference wrappers.
 *
 * When applied to a data class, the processor will generate a companion class that provides
 * type-safe access to DataStore preferences. The generated class will include:
 * - A DataStore instance tied to the schema name
 * - A Context extension property for easy access
 * - Flow properties for each preference
 * - Suspend functions for getting and setting preferences
 * - Functions for accessing and updating the entire data class
 *
 * Example usage:
 * ```kotlin
 * @VaultSchema(name = "User")
 * data class UserPreferences(
 *     @VaultItem(defaultValue = "")
 *     val username: String,
 *     
 *     @VaultItem(defaultValue = "0")
 *     val age: Int
 * )
 * ```
 *
 * This will generate a `UserPrefs` class with methods to access `username` and `age` preferences.
 *
 * @param name The name of the preferences schema. Used to generate the class name and DataStore name.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class VaultSchema(
    val name: String
)