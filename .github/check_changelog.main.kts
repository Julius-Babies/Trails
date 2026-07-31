#!/usr/bin/env kotlin
@file:DependsOn("com.kgit2:kommand-jvm:2.3.0")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

import com.kgit2.kommand.process.Command
import com.kgit2.kommand.process.Stdio
import com.kgit2.kommand.io.Output
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.system.exitProcess

/**
 * Checks that every issue a pull request closes has a changelog entry.
 * Issues typed as "Feature" must have one, every other type is optional
 * and only produces a warning.
 *
 * The pull request number comes from the environment so the workflow never
 * interpolates pull request data into the script itself. Falls back to the
 * branch name, which makes local runs work.
 */

fun execute(program: String, vararg arguments: String): Output = Command(program)
    .args(*arguments)
    .stdout(Stdio.Pipe)
    .output()

fun capture(program: String, vararg arguments: String): String? {
    val output = execute(program, *arguments)
    if (output.status != 0) return null
    return output.stdout?.trim()?.takeIf { it.isNotEmpty() }
}

val repoRoot = File(
    capture("git", "rev-parse", "--show-toplevel") ?: error("Not inside a git repository.")
)

val pullRequest = System.getenv("PR_NUMBER")?.takeIf { it.isNotBlank() }
val branch = System.getenv("BRANCH")?.takeIf { it.isNotBlank() }
    ?: capture("git", "rev-parse", "--abbrev-ref", "HEAD")

val summaryFile = System.getenv("GITHUB_STEP_SUMMARY")?.takeIf { it.isNotBlank() }?.let(::File)

// Tags the comment as ours. --edit-last currently finds it by author, so this
// only matters if another workflow ever starts commenting as the same bot.
val commentMarker = "<!-- changelog-check -->"

val findings = StringBuilder()
var warned = false
var failed = false

fun warn(message: String) {
    println("::warning::$message")
    warned = true
}

fun fail(message: String) {
    println("::error::$message")
    failed = true
}

/**
 * Writes the report to the step summary and to a single, self-updating pull
 * request comment. Log annotations alone are not enough: without a file
 * reference they only show up on the workflow run page, never in the pull
 * request itself.
 */
fun finish(headline: String): Nothing {
    val verdict = when {
        failed -> "❌ $headline"
        warned -> "⚠️ $headline"
        else -> "✅ $headline"
    }
    val report = buildString {
        appendLine(commentMarker)
        appendLine("### Changelog")
        appendLine()
        appendLine(verdict)
        if (findings.isNotEmpty()) {
            appendLine()
            append(findings)
        }
    }

    summaryFile?.appendText(report)

    if (pullRequest != null) {
        val file = File.createTempFile("changelog-check", ".md")
        try {
            file.writeText(report)
            val comment = execute(
                "gh", "pr", "comment", pullRequest,
                "--body-file", file.absolutePath,
                "--edit-last", "--create-if-none",
            )
            if (comment.status != 0) {
                println("::warning::Could not post the changelog report as a pull request comment.")
            }
        } finally {
            file.delete()
        }
    }

    exitProcess(if (failed) 1 else 0)
}

// --- which issues does this pull request close? ---------------------------
// The link is authoritative; the branch name is only a fallback for local runs
// and for pull requests that never got linked.
val linkedIssues = pullRequest
    ?.let { capture("gh", "pr", "view", it, "--json", "closingIssuesReferences", "--jq", ".closingIssuesReferences[].number") }
    ?.lines()
    ?.mapNotNull { it.trim().toIntOrNull() }
    .orEmpty()

// e.g. feat/15-add-minimal-movement -> 15, 5-editremove-shares -> 5
val issues = linkedIssues.ifEmpty {
    listOfNotNull(
        branch?.let { Regex("^([a-zA-Z]+/)?(\\d+)-").find(it)?.groupValues?.get(2)?.toIntOrNull() }
    )
}.distinct().sorted()

if (issues.isEmpty()) {
    warn("No linked issue found for this pull request (branch '$branch'), skipping the changelog check.")
    finish("This pull request closes no issue, so there is nothing to check.")
}

data class Changelog(
    val title: String?,
    val problems: List<String>,
)

val JsonElement?.isText: Boolean get() = this is JsonPrimitive && isString

/** Reads a changelog file and collects everything that is wrong with it. */
fun readChangelog(file: File): Changelog {
    val root = try {
        Json.parseToJsonElement(file.readText()).jsonObject
    } catch (exception: Exception) {
        // Keep it to one line, a GitHub annotation only shows the first one.
        val reason = exception.message?.lineSequence()?.firstOrNull()?.trim() ?: exception::class.simpleName
        return Changelog(title = null, problems = listOf("is not a valid JSON object ($reason)"))
    }

    val problems = mutableListOf<String>()

    val title = root["title"]
    if (!title.isText || (title as JsonPrimitive).content.isEmpty()) {
        problems += "\"title\" is missing or empty"
    }
    root["description"]?.let {
        if (!it.isText) problems += "\"description\" must be a string"
    }
    root["localized"]?.let { localized ->
        if (localized !is JsonObject) {
            problems += "\"localized\" must be an object"
            return@let
        }
        localized.forEach { (language, localization) ->
            if (localization !is JsonObject) {
                problems += "\"localized.$language\" must be an object"
                return@forEach
            }
            listOf("title", "description").forEach { field ->
                localization[field]?.let {
                    if (!it.isText) problems += "\"localized.$language.$field\" must be a string"
                }
            }
        }
    }

    return Changelog(
        title = (root["title"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
        problems = problems,
    )
}

issues.forEach { issue ->
    val type = capture("gh", "issue", "view", "$issue", "--json", "issueType", "--jq", ".issueType.name // \"\"")
    val required = type.equals("Feature", ignoreCase = true)

    val relative = "docs/changelog/issues/$issue/changelog.json"
    val file = File(repoRoot, relative)

    if (type == null) {
        warn("Issue #$issue has no issue type. Please set one (Feature, Bug or Task).")
        findings.appendLine("- ⚠️ **#$issue** has no issue type. Please set one (Feature, Bug or Task).")
    }

    when {
        !file.exists() && required -> {
            fail("Issue #$issue is a Feature but has no changelog. Please add $relative.")
            findings.appendLine("- ❌ **#$issue** (Feature) needs a changelog. Please add `$relative`.")
        }

        !file.exists() -> {
            warn("Issue #$issue has no changelog ($relative). That is optional for type '${type ?: "unset"}'.")
            findings.appendLine("- ⚠️ **#$issue** (${type ?: "no type"}) has no changelog. `$relative` is optional for this type.")
        }

        else -> {
            // A broken file is always an error, no matter the issue type:
            // generate_changelog.main.kts fails on it at release time, so
            // catching it here is the whole point.
            val changelog = readChangelog(file)
            if (changelog.problems.isEmpty()) {
                println("Issue #$issue (${type ?: "no type"}) has a changelog: ${changelog.title}")
                findings.appendLine("- ✅ **#$issue** (${type ?: "no type"}): ${changelog.title}")
            } else {
                changelog.problems.forEach { fail("$relative: $it") }
                findings.appendLine("- ❌ **#$issue** (${type ?: "no type"}) has an invalid `$relative`:")
                changelog.problems.forEach { findings.appendLine("  - $it") }
            }
        }
    }
}

val checked = issues.joinToString(", ") { "#$it" }
finish(
    when {
        failed -> "The changelog is not ready for $checked."
        warned -> "The changelog needs a look for $checked."
        else -> "Every issue in this pull request has a changelog ($checked)."
    }
)
