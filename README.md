# Vault

A Kotlin Symbol Processor (KSP) for generating type-safe DataStore preference wrappers.

## Overview

Vault simplifies working with Android's DataStore preferences by generating type-safe accessors from annotated data classes. It eliminates boilerplate code and provides a clean API for managing preferences.

## Features

- Type-safe access to DataStore preferences
- Generated extension properties for easy access from Context
- Support for multiple preference types: `Int`, `String`, `Boolean`, `Float`, `Long`, `Double`, and `Set<String>`
- Flow properties for reactive data access
- Suspend functions for coroutine-based operations
- Support for default values

## Installation

Add the dependencies to your project:

```kotlin
// In your root build.gradle.kts
plugins {
    id("com.google.devtools.ksp") version "1.8.21-1.0.11" // Use appropriate version
}

// In your app/module build.gradle.kts
dependencies {
    // DataStore dependencies
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Vault
    implementation(project(":vault-annotations"))
    ksp(project(":vault-processor"))
}
```

## Usage

### 1. Define your data class with `@VaultSchema` and `@VaultItem` annotations

```kotlin
import com.example.vault_annotations.VaultSchema
import com.example.vault_annotations.VaultItem

@VaultSchema(name = "User")
data class UserPreferences(
    @VaultItem(defaultValue = "")
    val username: String,
    
    @VaultItem(defaultValue = "0")
    val age: Int,
    
    @VaultItem(key = "is_logged_in", defaultValue = "false")
    val isLoggedIn: Boolean,
    
    @VaultItem(defaultValue = "emptySet()")
    val favoriteTopics: Set<String>
)
```

### 2. After compilation, use the generated class

```kotlin
class MyActivity : AppCompatActivity() {
    // Access via Context extension property
    private val userPrefs by userPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Read values as Flow
        lifecycleScope.launch {
            userPrefs.usernameFlow.collect { username ->
                // Update UI
            }
        }
        
        // Set values with suspend functions
        lifecycleScope.launch {
            userPrefs.setUsername("JohnDoe")
            userPrefs.setAge(25)
        }
        
        // Read/update the entire data class
        lifecycleScope.launch {
            // Get current preferences
            val currentPrefs = userPrefs.getPreferences()
            
            // Update all preferences at once
            userPrefs.updatePreferences { prefs ->
                prefs.copy(username = "NewName", age = 30)
            }
        }
    }
}
```

## Annotations

### `@VaultSchema`

Applied to a data class to indicate that it represents a schema for preferences.

Parameters:
- `name`: String - The name of the preferences. Used to generate the class name and DataStore name.

### `@VaultItem`

Applied to data class properties to include them in the generated preferences.

Parameters:
- `key`: String (optional) - The preference key. If not provided, the property name is used.
- `defaultValue`: String (required) - The default value as a string. Must be convertible to the property type.

## License

See the [LICENSE](LICENSE) file for details.
