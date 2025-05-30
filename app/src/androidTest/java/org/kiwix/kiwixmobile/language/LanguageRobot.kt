/*
 * Kiwix Android
 * Copyright (c) 2019 Kiwix <android.kiwix.org>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.kiwix.kiwixmobile.language

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import applyWithViewHierarchyPrinting
import com.adevinta.android.barista.interaction.BaristaSleepInteractions
import org.kiwix.kiwixmobile.BaseRobot
import org.kiwix.kiwixmobile.Findable.ViewId
import org.kiwix.kiwixmobile.R
import org.kiwix.kiwixmobile.core.page.SEARCH_ICON_TESTING_TAG
import org.kiwix.kiwixmobile.core.search.SEARCH_FIELD_TESTING_TAG
import org.kiwix.kiwixmobile.language.composables.LANGUAGE_ITEM_CHECKBOX_TESTING_TAG
import org.kiwix.kiwixmobile.nav.destination.library.online.LANGUAGE_MENU_ICON_TESTING_TAG
import org.kiwix.kiwixmobile.testutils.TestUtils
import org.kiwix.kiwixmobile.testutils.TestUtils.waitUntilTimeout

fun language(func: LanguageRobot.() -> Unit) = LanguageRobot().applyWithViewHierarchyPrinting(func)

class LanguageRobot : BaseRobot() {
  fun clickDownloadOnBottomNav() {
    clickOn(ViewId(R.id.downloadsFragment))
  }

  fun clickOnLanguageIcon(composeTestRule: ComposeContentTestRule) {
    // Wait for a few seconds to properly save selected language.
    composeTestRule.apply {
      waitUntilTimeout()
      onNodeWithTag(LANGUAGE_MENU_ICON_TESTING_TAG).performClick()
    }
  }

  fun clickOnSaveLanguageIcon(composeTestRule: ComposeContentTestRule) {
    composeTestRule.onNodeWithTag(SAVE_ICON_TESTING_TAG)
      .performClick()
  }

  fun clickOnLanguageSearchIcon(composeTestRule: ComposeContentTestRule) {
    composeTestRule.onNodeWithTag(SEARCH_ICON_TESTING_TAG).performClick()
  }

  fun searchLanguage(
    composeTestRule: ComposeContentTestRule,
    searchLanguage: String
  ) {
    val searchField = composeTestRule.onNodeWithTag(SEARCH_FIELD_TESTING_TAG)
    searchField.performTextInput(text = searchLanguage)
  }

  // error prone
  fun deSelectLanguageIfAlreadySelected(
    composeTestRule: ComposeContentTestRule,
    matchLanguage: String
  ) {
    BaristaSleepInteractions.sleep(TestUtils.TEST_PAUSE_MS.toLong())
    try {
      composeTestRule.onNodeWithTag("$LANGUAGE_ITEM_CHECKBOX_TESTING_TAG$matchLanguage")
        .assertIsOff()
    } catch (_: AssertionError) {
      composeTestRule.onNodeWithTag("$LANGUAGE_ITEM_CHECKBOX_TESTING_TAG$matchLanguage")
        .performClick()
    }
  }

  fun selectLanguage(
    composeTestRule: ComposeContentTestRule,
    matchLanguage: String
  ) {
    composeTestRule.onNodeWithText(matchLanguage)
      .performClick()
  }

  fun checkIsLanguageSelected(
    composeTestRule: ComposeContentTestRule,
    matchLanguage: String
  ) {
    BaristaSleepInteractions.sleep(TestUtils.TEST_PAUSE_MS.toLong())
    composeTestRule.onNodeWithTag("$LANGUAGE_ITEM_CHECKBOX_TESTING_TAG$matchLanguage")
      .assertIsOn()
  }
}
