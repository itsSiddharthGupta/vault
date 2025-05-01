package com.example.vault.examples

import com.example.vault_annotations.VaultSchema
import com.example.vault_annotations.VaultItem

/**
 * Example of a data class annotated with [VaultSchema] and [VaultItem] annotations
 * for use with the Vault DataStore preference generator.
 * 
 * This will generate a UserPrefs class with methods to access these preferences.
 */
@VaultSchema(name = "User")
data class UserPreferences(
    /**
     * The user's display name.
     * Uses the property name as the preference key.
     */
    @VaultItem(defaultValue = "")
    val username: String,

    /**
     * The user's age.
     * Uses a custom preference key "user_age".
     */
    @VaultItem(key = "user_age", defaultValue = "0")
    val age: Int,
    
    /**
     * Whether the user has completed onboarding.
     */
    @VaultItem(defaultValue = "false")
    val hasCompletedOnboarding: Boolean,
    
    /**
     * User notification preferences.
     * Demonstrates using a Boolean with a custom key.
     */
    @VaultItem(key = "notifications_enabled", defaultValue = "true")
    val notificationsEnabled: Boolean,
    
    /**
     * User's preferred theme value (could be an index or enum value).
     */
    @VaultItem(defaultValue = "0")
    val themePreference: Int,
    
    /**
     * Custom app settings like a volume level.
     */
    @VaultItem(defaultValue = "0.5")
    val volumeLevel: Float,
    
    /**
     * Example of using a Set<String> for storing multiple values.
     * The default is an empty set.
     */
    @VaultItem(defaultValue = "emptySet()")
    val enabledFeatures: Set<String>,
    
    /**
     * Example with a non-empty default Set<String>.
     */
    @VaultItem(defaultValue = "{\"basic\", \"standard\"}")
    val subscribedNewsletters: Set<String>
)

/**
 * Example of how to use the generated preferences class in an Activity or Fragment.
 * 
 * This code is for demonstration purposes only and won't compile without
 * the actual generated code and Android dependencies.
 */
/*
class MyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Read values as Flow and update UI when they change
        lifecycleScope.launch {
            user.usernameFlow.collect { username ->
                usernameTextView.text = username
            }
            
            user.hasCompletedOnboardingFlow.collect { hasCompleted ->
                if (!hasCompleted) {
                    showOnboarding()
                }
            }
        }
        
        // Button to update username
        updateButton.setOnClickListener {
            val newUsername = usernameEditText.text.toString()
            lifecycleScope.launch {
                user.setUsername(newUsername)
            }
        }
        
        // Toggle notifications
        notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                user.setNotificationsEnabled(isChecked)
            }
        }
        
        // Read all preferences at once
        lifecycleScope.launch {
            val allPrefs = user.getPreferences()
            // Use all preferences...
            
            // Update multiple preferences in a single operation
            user.updatePreferences { currentPrefs ->
                currentPrefs.copy(
                    age = currentPrefs.age + 1,
                    enabledFeatures = currentPrefs.enabledFeatures + "new_feature"
                )
            }
        }
    }
}
*/ 