/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.screens

/** Offset of the device time zone from UTC at [epochMillis], in milliseconds (daylight saving included). */
expect fun localOffsetMillis(epochMillis: Long): Long
