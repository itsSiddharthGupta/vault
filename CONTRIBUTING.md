# Contributing to Vault

Thank you for your interest in contributing to Vault! This document provides guidelines and instructions for contributing to this project.

## Development Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/vault.git
   cd vault
   ```

2. Open the project in Android Studio or IntelliJ IDEA.

3. Build the project:
   ```bash
   ./gradlew build
   ```

## Project Structure

The project consists of the following modules:

- **vault-annotations**: Contains annotation classes used to define preference schemas
  - `VaultSchema`: Annotates data classes to mark them for processor
  - `VaultItem`: Annotates properties within the data class

- **vault-processor**: Contains the KSP processor that generates code
  - `VaultProcessor`: Main processor that handles code generation
  - `VaultProcessorProvider`: Provider class for the KSP API

## Testing Changes

For testing your changes, you can use the example app or create tests:

1. Make changes to the annotations or processor
2. Build the project: `./gradlew build`
3. Run tests: `./gradlew test`

For a real-world test, you can publish the artifacts to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

Then, in your test app, include:

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation("com.example:vault-annotations:1.0.0-SNAPSHOT")
    ksp("com.example:vault-processor:1.0.0-SNAPSHOT")
}
```

## Code Style

This project follows Kotlin's official coding conventions. The codebase is formatted with ktlint.

- Run code style check: `./gradlew ktlintCheck`
- Run code style auto-format: `./gradlew ktlintFormat`

## Pull Request Process

1. Fork the repository
2. Create a new branch for your feature or bugfix
3. Make your changes
4. Run tests to ensure your changes don't break existing functionality
5. Submit a pull request

Please provide a clear description of the problem you're solving and how your solution works.

## Adding Support for New Types

If you want to add support for additional preference types:

1. Add the new type to the `supportedTypes` map in `VaultProcessor.kt`
2. Implement proper default value parsing
3. Add unit tests for the new type
4. Update documentation to mention the new supported type

## License

By contributing to this project, you agree that your contributions will be licensed under the project's license. 