/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.core

import kotlin.test.Test
import kotlin.test.assertTrue

class AppInfoTest {
    @Test
    fun versionIsSemantic() {
        assertTrue(Regex("""\d+\.\d+\.\d+(-[0-9A-Za-z.]+)?""").matches(AppInfo.VERSION))
    }
}
