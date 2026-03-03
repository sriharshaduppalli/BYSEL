// Root build.gradle.kts for the Android subproject.
buildscript {
	dependencies {
		// No buildscript classpath here; plugin is provided via settings.pluginManagement
	}
}

plugins {
	// keep minimal; module-level plugins configured in app/build.gradle.kts
}

// repositories are managed in settings.gradle.kts; do not declare them here to
// avoid conflicts with `repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS`.
