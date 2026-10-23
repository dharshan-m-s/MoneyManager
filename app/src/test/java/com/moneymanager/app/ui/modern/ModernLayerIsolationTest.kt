package com.moneymanager.app.ui.modern

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural guard for the second appearance: the Modern tree must stay self-contained so the
 * Default UI can be deleted later without breaking it. The only shared code allowed is business
 * logic (ViewModels, repositories, models) and navigation metadata — never Default visuals.
 */
class ModernLayerIsolationTest {

    private val moduleRoot: File = listOf(File("src/main/java"), File("app/src/main/java"))
        .firstOrNull { it.exists() }
        ?: error("cannot locate main source root from ${File(".").absolutePath}")

    private val modernRoot = File(moduleRoot, "com/moneymanager/app/ui/modern")

    /** Only these Default packages are non-visual infrastructure. */
    private val allowedSharedPrefixes = listOf(
        "com.moneymanager.app.ui.settings.SettingsEntryPoint",
        "com.moneymanager.app.ui.settings.BackupEntryPoint",
        "com.moneymanager.app.ui.home.HomePreferences",
        "com.moneymanager.app.ui.navigation.Destination"
    )

    @Test
    fun modernUiNeverImportsDefaultVisuals() {
        val offenders = mutableListOf<String>()
        modernRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines()
                    .filter { it.startsWith("import com.moneymanager.app.ui.") }
                    .forEach { line ->
                        val target = line.removePrefix("import ").trim()
                        val allowed = allowedSharedPrefixes.any { target.startsWith(it) }
                        // Business/model imports that live outside ui/ are not matched above.
                        val defaultVisual = target.startsWith("com.moneymanager.app.ui.components.") ||
                            target.startsWith("com.moneymanager.app.ui.theme.")
                        if (defaultVisual && !allowed) {
                            offenders += "${file.relativeTo(moduleRoot)}: $line"
                        }
                    }
            }
        assertTrue(
            "Modern UI must not depend on Default visual classes:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun modernTreeIsSubstantialAndPresent() {
        val files = modernRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("expected the modern UI tree to exist", files.size > 25)
        assertTrue(
            "expected a self-contained modern theme",
            File(modernRoot, "theme/ModernTheme.kt").exists()
        )
        assertTrue(
            "expected a self-contained modern component layer",
            File(modernRoot, "components/ModernCore.kt").exists()
        )
    }
}
