package org.kiwix.kiwixmobile.core.page.history.viewmodel.effects

/*
 * Kiwix Android
 * Copyright (c) 2020 Kiwix <android.kiwix.org>
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

import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import org.kiwix.kiwixmobile.core.base.SideEffect
import org.kiwix.kiwixmobile.core.dao.PageDao
import org.kiwix.kiwixmobile.core.extensions.ActivityExtensions.cachedComponent
import org.kiwix.kiwixmobile.core.page.history.viewmodel.HistoryState
import org.kiwix.kiwixmobile.core.page.viewmodel.effects.DeletePageItems
import org.kiwix.kiwixmobile.core.utils.dialog.DialogShower
import org.kiwix.kiwixmobile.core.utils.dialog.KiwixDialog.DeleteAllHistory
import org.kiwix.kiwixmobile.core.utils.dialog.KiwixDialog.DeleteSelectedHistory

data class ShowDeleteHistoryDialog(
  private val effects: MutableSharedFlow<SideEffect<*>>,
  private val state: HistoryState,
  private val pageDao: PageDao,
  private val viewModelScope: CoroutineScope,
  var dialogShower: DialogShower
) : SideEffect<Unit> {
  override fun invokeWith(activity: AppCompatActivity) {
    activity.cachedComponent.inject(this)
    dialogShower.show(if (state.isInSelectionState) DeleteSelectedHistory else DeleteAllHistory, {
      effects.tryEmit(DeletePageItems(state, pageDao, viewModelScope))
    })
  }
}
