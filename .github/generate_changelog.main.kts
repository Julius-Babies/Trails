#!/usr/bin/env kotlin
@file:DependsOn("com.kgit2:kommand-jvm:2.3.0")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

import com.kgit2.kommand.process.Command
import com.kgit2.kommand.process.Stdio
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

val releaseName = args.firstOrNull()?.takeIf { it.isNotBlank() }
    ?: System.getenv("RELEASE_NAME")?.takeIf { it.isNotBlank() }
    ?: error("No release name. Pass it as the first argument or set RELEASE_NAME (e.g. v20260731_1812).")

fun run(program: String, vararg arguments: String): String? = Command(program)
    .args(*arguments)
    .stdout(Stdio.Pipe)
    .output()
    .stdout
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

val repoRoot = File(
    run("git", "rev-parse", "--show-toplevel")
        ?: error("Not inside a git repository.")
)

// The first release has no predecessor, so fall back to the full history.
val latestRelease = run("gh", "release", "view", "--json", "tagName", "--jq", ".tagName")
val range = latestRelease?.let { "$it..HEAD" } ?: "HEAD"

val issuesSinceLastRelease = run("git", "log", range, "--pretty=format:%s")
    .orEmpty()
    .lines()
    .mapNotNull { commitMessage -> Regex("#(\\d+)").find(commitMessage)?.groupValues?.get(1)?.toIntOrNull() }
    .distinct()
    .sorted()

data class IssueChangelog(
    val issue: Int,
    val title: String,
    val description: String?,
    val localized: Map<String, Localization>,
) {
    data class Localization(
        val title: String?,
        val description: String?,
    )

    /** A localization only overrides the fields it actually provides. */
    fun titleFor(language: String?): String = language?.let { localized[it]?.title } ?: title

    fun descriptionFor(language: String?): String? = language?.let { localized[it]?.description } ?: description
}

val json = Json { ignoreUnknownKeys = true }

val changelogs = issuesSinceLastRelease.mapNotNull { issue ->
    val file = File(repoRoot, "docs/changelog/issues/$issue/changelog.json")
    if (!file.exists()) {
        System.err.println("warning: no changelog for issue #$issue, skipping (${file.relativeTo(repoRoot)})")
        return@mapNotNull null
    }

    val root = json.parseToJsonElement(file.readText()).jsonObject
    val title = root["title"]?.jsonPrimitive?.contentOrNull
        ?: error("${file.relativeTo(repoRoot)} has no \"title\".")

    IssueChangelog(
        issue = issue,
        title = title,
        description = root["description"]?.jsonPrimitive?.contentOrNull,
        localized = root["localized"]?.jsonObject.orEmpty().mapValues { (_, localization) ->
            IssueChangelog.Localization(
                title = localization.jsonObject["title"]?.jsonPrimitive?.contentOrNull,
                description = localization.jsonObject["description"]?.jsonPrimitive?.contentOrNull,
            )
        },
    )
}

fun render(language: String?) = buildString {
    appendLine("# $releaseName")
    changelogs.forEach { changelog ->
        appendLine()
        appendLine("## ${changelog.titleFor(language)}")
        changelog.descriptionFor(language)?.let {
            appendLine()
            appendLine(it)
        }
    }
}

if (changelogs.isEmpty()) {
    System.err.println("No changelog entries for $releaseName, nothing to write.")
} else {
    val outputDirectory = File(repoRoot, "docs/changelog/releases/$releaseName")
    outputDirectory.mkdirs()

    // null is the default file, every language found in any entry gets its own.
    val languages = listOf(null) + changelogs.flatMap { it.localized.keys }.distinct().sorted()

    languages.forEach { language ->
        val file = File(outputDirectory, if (language == null) "CHANGELOG.md" else "CHANGELOG.$language.md")
        file.writeText(render(language))
        println("wrote ${file.relativeTo(repoRoot)}")
    }
}
