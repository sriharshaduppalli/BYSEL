// Root build.gradle.kts for the Android subproject.
buildscript {
	dependencies {
		// No buildscript classpath here; plugin is provided via settings.pluginManagement
	}
}

plugins {
	id("com.android.application") version "8.4.0" apply false
	id("com.android.library") version "8.4.0" apply false
	id("org.jetbrains.kotlin.android") version "1.9.23" apply false
	id("org.jetbrains.kotlin.kapt") version "1.9.23" apply false
	id("com.google.dagger.hilt.android") version "2.51.1" apply false
}

// repositories are managed in settings.gradle.kts; do not declare them here to
// avoid conflicts with `repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS`.
