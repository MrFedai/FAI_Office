/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import java.net.URI
import java.net.URISyntaxException
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * The files on a desktop command line: Windows "Open with" (`"fai-office.exe" "%1"`), a Linux desktop entry
 * (`%U`, `file:` URIs), a terminal (relative paths). Pure functions, tested on the JVM.
 */
object DesktopCommandLine {
    /**
     * Absolute, normalised paths of the files in [args], resolved against [cwd]: `file:` URIs are decoded, other
     * URIs (`http:`, `content:`) are dropped (no network), options before `--` are skipped (`-psn_…` from macOS,
     * unknown flags), everything after `--` is a file. Duplicates are dropped, order kept, at most
     * [IncomingFiles.MAX_FILES].
     */
    fun files(
        args: List<String>,
        cwd: Path,
    ): List<Path> {
        var options = true
        val paths = mutableListOf<Path>()
        for (raw in args) {
            val arg = raw.trim()
            when {
                arg.isEmpty() -> continue
                options && arg == "--" -> options = false
                options && arg.startsWith("-") -> continue
                else -> pathOf(arg, cwd)?.let(paths::add)
            }
        }
        return paths.distinct().take(IncomingFiles.MAX_FILES)
    }

    private fun pathOf(
        arg: String,
        cwd: Path,
    ): Path? =
        try {
            when {
                arg.startsWith("file:", ignoreCase = true) -> Path.of(URI(arg))
                URI_SCHEME.matches(arg) -> null
                else -> cwd.resolve(arg)
            }?.toAbsolutePath()?.normalize()
        } catch (_: InvalidPathException) {
            null
        } catch (_: URISyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null // a file: URI with an authority or a query
        }

    /** `scheme://…` with a scheme of two or more letters (a drive letter `C:` is one). */
    private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]+://.*")

    /**
     * Splits a Windows command line the way the C runtime (`CommandLineToArgvW`) does: white space separates
     * arguments outside quotes; `2n` backslashes before a quote give `n` backslashes and the quote toggles quoting,
     * `2n+1` give `n` backslashes and a literal quote; other backslashes are literal; `""` inside quotes is a quote.
     * The first argument (the program) follows the simpler rule of the runtime: up to the next quote or space.
     */
    fun splitWindows(line: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        // Program name: quoted up to the next quote, or up to the first white space; no escapes.
        while (i < line.length && line[i].isWhitespace()) i++
        if (i >= line.length) return out
        if (line[i] == '"') {
            val end = line.indexOf('"', i + 1).let { if (it < 0) line.length else it }
            out += line.substring(i + 1, end)
            i = end + 1
        } else {
            val start = i
            while (i < line.length && !line[i].isWhitespace()) i++
            out += line.substring(start, i)
        }
        while (true) {
            while (i < line.length && line[i].isWhitespace()) i++
            if (i >= line.length) return out
            val arg = StringBuilder()
            var quoted = false
            while (i < line.length && (quoted || !line[i].isWhitespace())) {
                when (val c = line[i]) {
                    '\\' -> {
                        var slashes = 0
                        while (i < line.length && line[i] == '\\') {
                            slashes++
                            i++
                        }
                        if (i < line.length && line[i] == '"') {
                            repeat(slashes / 2) { arg.append('\\') }
                            if (slashes % 2 == 1) {
                                arg.append('"')
                                i++
                            }
                        } else {
                            repeat(slashes) { arg.append('\\') }
                        }
                    }

                    '"' -> {
                        if (quoted && i + 1 < line.length && line[i + 1] == '"') {
                            arg.append('"')
                            i += 2
                        } else {
                            quoted = !quoted
                            i++
                        }
                    }

                    else -> {
                        arg.append(c)
                        i++
                    }
                }
            }
            out += arg.toString()
        }
    }

    /**
     * The program arguments as Windows passed them: on Windows `main(args)` comes through the ANSI code page, so a
     * name outside it (Turkish letters on an English Windows) arrives as `?`. The process's own command line is
     * UTF-16 (`ProcessHandle` reads it from the process); its split replaces [args] when it has the same count.
     * Elsewhere, or when the command line is not readable, [args] as given.
     */
    fun programArgs(
        args: List<String>,
        windows: Boolean,
        commandLine: String?,
    ): List<String> {
        if (!windows || commandLine == null) return args
        val split = splitWindows(commandLine).drop(1)
        return if (split.size == args.size) split else args
    }
}
