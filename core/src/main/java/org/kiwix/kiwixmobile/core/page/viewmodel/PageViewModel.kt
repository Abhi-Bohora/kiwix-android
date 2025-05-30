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

package org.kiwix.kiwixmobile.core.page.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.jetbrains.annotations.VisibleForTesting
import org.kiwix.kiwixmobile.core.base.SideEffect
import org.kiwix.kiwixmobile.core.dao.PageDao
import org.kiwix.kiwixmobile.core.page.adapter.Page
import org.kiwix.kiwixmobile.core.page.viewmodel.Action.Exit
import org.kiwix.kiwixmobile.core.page.viewmodel.Action.ExitActionModeMenu
import org.kiwix.kiwixmobile.core.page.viewmodel.Action.Filter
import org.kiwix.kiwixmobile.core.page.viewmodel.Action.OnItemClick
import org.kiwix.kiwixmobile.core.page.viewmodel.Action.OnItemLongClick
import org.kiwix.kiwixmobile.core.page.viewmodel.Action.UpdatePages
import org.kiwix.kiwixmobile.core.page.viewmodel.Action.UserClickedDeleteButton
import org.kiwix.kiwixmobile.core.page.viewmodel.Action.UserClickedDeleteSelectedPages
import org.kiwix.kiwixmobile.core.page.viewmodel.Action.UserClickedShowAllToggle
import org.kiwix.kiwixmobile.core.page.viewmodel.effects.OpenPage
import org.kiwix.kiwixmobile.core.reader.ZimReaderContainer
import org.kiwix.kiwixmobile.core.search.viewmodel.effects.PopFragmentBackstack
import org.kiwix.kiwixmobile.core.utils.SharedPreferenceUtil
import org.kiwix.kiwixmobile.core.utils.dialog.AlertDialogShower

abstract class PageViewModel<T : Page, S : PageState<T>>(
  protected val pageDao: PageDao,
  val sharedPreferenceUtil: SharedPreferenceUtil,
  val zimReaderContainer: ZimReaderContainer
) : ViewModel() {
  abstract fun initialState(): S

  lateinit var alertDialogShower: AlertDialogShower

  private lateinit var pageViewModelClickListener: PageViewModelClickListener
  private val _state = MutableStateFlow(initialState())
  val state: StateFlow<S> = _state.asStateFlow()
  val effects = MutableSharedFlow<SideEffect<*>>(extraBufferCapacity = Int.MAX_VALUE)
  val actions = MutableSharedFlow<Action>(extraBufferCapacity = Int.MAX_VALUE)
  private val coroutineJobs = mutableListOf<Job>()

  @VisibleForTesting
  fun getMutableStateForTestCases() = _state

  init {
    coroutineJobs.apply {
      add(observeActions())
      add(observePages())
    }
  }

  private fun observeActions() =
    actions.map { action -> reduce(action, state.value) }
      .onEach { newState -> _state.value = newState }
      .launchIn(viewModelScope)

  private fun observePages(dispatcher: CoroutineDispatcher = Dispatchers.IO) =
    pageDao.pages()
      .flowOn(dispatcher)
      .onEach { actions.tryEmit(UpdatePages(it)) }
      .launchIn(viewModelScope)

  private fun reduce(action: Action, state: S): S =
    when (action) {
      Exit -> exitFragment(state)
      ExitActionModeMenu -> deselectAllPages(state)
      UserClickedDeleteButton, UserClickedDeleteSelectedPages -> offerShowDeleteDialog(state)
      is UserClickedShowAllToggle -> offerUpdateToShowAllToggle(action, state)
      is OnItemClick -> handleItemClick(state, action)
      is OnItemLongClick -> handleItemLongClick(state, action)
      is Filter -> updatePagesBasedOnFilter(state, action)
      is UpdatePages -> updatePages(state, action)
    }

  abstract fun updatePagesBasedOnFilter(state: S, action: Filter): S

  abstract fun updatePages(state: S, action: UpdatePages): S

  abstract fun offerUpdateToShowAllToggle(
    action: UserClickedShowAllToggle,
    state: S
  ): S

  private fun offerShowDeleteDialog(state: S): S {
    effects.tryEmit(createDeletePageDialogEffect(state, viewModelScope = viewModelScope))
    return state
  }

  private fun handleItemLongClick(state: S, action: OnItemLongClick): S =
    copyWithNewItems(state, state.getItemsAfterToggleSelectionOfItem(action.page))

  abstract fun copyWithNewItems(state: S, newItems: List<T>): S

  private fun handleItemClick(state: S, action: Action.OnItemClick): S {
    if (state.isInSelectionState) {
      return copyWithNewItems(state, state.getItemsAfterToggleSelectionOfItem(action.page))
    }
    if (::pageViewModelClickListener.isInitialized) {
      effects.tryEmit(pageViewModelClickListener.onItemClick(action.page))
    } else {
      effects.tryEmit(OpenPage(action.page, zimReaderContainer))
    }
    return state
  }

  fun setOnItemClickListener(clickListener: PageViewModelClickListener) {
    pageViewModelClickListener = clickListener
  }

  abstract fun deselectAllPages(state: S): S

  private fun exitFragment(state: S): S {
    effects.tryEmit(PopFragmentBackstack)
    return state
  }

  override fun onCleared() {
    coroutineJobs.forEach {
      it.cancel()
    }
    coroutineJobs.clear()
    super.onCleared()
  }

  abstract fun createDeletePageDialogEffect(state: S, viewModelScope: CoroutineScope): SideEffect<*>
}
