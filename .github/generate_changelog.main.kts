#!/usr/bin/env kotlin
@file:DependsOn("com.kgit2:kommand-jvm:2.3.0")

import com.kgit2.kommand.process.Command
import com.kgit2.kommand.process.Stdio

val latestRelease = Command("gh")
    .args("release", "view", "--json", "tagName", "--jq", ".tagName")
    .stdout(Stdio.Pipe)
    .output()
    .stdout
    ?.trim()

val commitsFromTagUntilHead = Command("git")
    .args("log", "$latestRelease..HEAD", "--pretty=format:%s")
    .stdout(Stdio.Pipe)
    .output()
    .stdout
    ?.trim()

println(latestRelease)
println(commitsFromTagUntilHead)
