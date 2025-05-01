package com.example.vault_annotations

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class VaultItem(
    val key: String = "",
    val defaultValue: String,
)
