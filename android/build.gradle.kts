// Root build.gradle.kts for the Android subproject.
buildscript {
	dependencies {
		// No buildscript classpath here; plugin is provided via settings.pluginManagement
	}
}

plugins {
	id("com.android.application") version "9.0.1" apply false
	id("com.android.library") version "9.0.1" apply false
	id("org.jetbrains.kotlin.android") version "2.2.10" apply false
	id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
	id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
	id("com.google.dagger.hilt.android") version "2.51.1" apply false
}

// repositories are managed in settings.gradle.kts; do not declare them here to
// avoid conflicts with `repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS`.
