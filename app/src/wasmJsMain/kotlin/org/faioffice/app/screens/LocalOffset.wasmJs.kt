/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

@file:OptIn(ExperimentalWasmJsInterop::class)

package org.faioffice.app.screens

import kotlin.js.ExperimentalWasmJsInterop

private fun offsetMinutes(epochMillis: Double): Double = js("new Date(epochMillis).getTimezoneOffset()")

private const val MILLIS_PER_MINUTE = 60_000L

// getTimezoneOffset is UTC minus local, in minutes.
actual fun localOffsetMillis(epochMillis: Long): Long =
    -offsetMinutes(epochMillis.toDouble()).toLong() * MILLIS_PER_MINUTE
