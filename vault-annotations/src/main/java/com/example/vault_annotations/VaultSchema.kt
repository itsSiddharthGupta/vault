package com.example.vault_annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class VaultSchema(
    val name: String
)