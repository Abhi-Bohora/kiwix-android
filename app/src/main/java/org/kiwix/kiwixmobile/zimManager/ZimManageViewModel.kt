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

package org.kiwix.kiwixmobile.zimManager

import android.app.Application
import android.net.ConnectivityManager
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.reactivex.Flowable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Function6
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlowable
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
import okhttp3.logging.HttpLoggingInterceptor.Level.NONE
import org.kiwix.kiwixmobile.BuildConfig.DEBUG
import org.kiwix.kiwixmobile.core.R
import org.kiwix.kiwixmobile.core.StorageObserver
import org.kiwix.kiwixmobile.core.base.SideEffect
import org.kiwix.kiwixmobile.core.compat.CompatHelper.Companion.convertToLocal
import org.kiwix.kiwixmobile.core.compat.CompatHelper.Companion.isWifi
import org.kiwix.kiwixmobile.core.dao.DownloadRoomDao
import org.kiwix.kiwixmobile.core.dao.NewBookDao
import org.kiwix.kiwixmobile.core.dao.NewLanguagesDao
import org.kiwix.kiwixmobile.core.data.DataSource
import org.kiwix.kiwixmobile.core.data.remote.KiwixService
import org.kiwix.kiwixmobile.core.data.remote.KiwixService.Companion.LIBRARY_NETWORK_PATH
import org.kiwix.kiwixmobile.core.data.remote.ProgressResponseBody
import org.kiwix.kiwixmobile.core.data.remote.UserAgentInterceptor
import org.kiwix.kiwixmobile.core.di.modules.CALL_TIMEOUT
import org.kiwix.kiwixmobile.core.di.modules.CONNECTION_TIMEOUT
import org.kiwix.kiwixmobile.core.di.modules.KIWIX_DOWNLOAD_URL
import org.kiwix.kiwixmobile.core.di.modules.READ_TIMEOUT
import org.kiwix.kiwixmobile.core.di.modules.USER_AGENT
import org.kiwix.kiwixmobile.core.downloader.downloadManager.DEFAULT_INT_VALUE
import org.kiwix.kiwixmobile.core.downloader.model.DownloadModel
import org.kiwix.kiwixmobile.core.entity.LibraryNetworkEntity
import org.kiwix.kiwixmobile.core.entity.LibraryNetworkEntity.Book
import org.kiwix.kiwixmobile.core.extensions.calculateSearchMatches
import org.kiwix.kiwixmobile.core.extensions.registerReceiver
import org.kiwix.kiwixmobile.core.utils.BookUtils
import org.kiwix.kiwixmobile.core.utils.SharedPreferenceUtil
import org.kiwix.kiwixmobile.core.utils.dialog.AlertDialogShower
import org.kiwix.kiwixmobile.core.utils.files.Log
import org.kiwix.kiwixmobile.core.utils.files.ScanningProgressListener
import org.kiwix.kiwixmobile.core.zim_manager.ConnectivityBroadcastReceiver
import org.kiwix.kiwixmobile.core.zim_manager.Language
import org.kiwix.kiwixmobile.core.zim_manager.NetworkState
import org.kiwix.kiwixmobile.core.zim_manager.NetworkState.CONNECTED
import org.kiwix.kiwixmobile.core.zim_manager.fileselect_view.BooksOnDiskListItem
import org.kiwix.kiwixmobile.core.zim_manager.fileselect_view.BooksOnDiskListItem.BookOnDisk
import org.kiwix.kiwixmobile.core.zim_manager.fileselect_view.SelectionMode.MULTI
import org.kiwix.kiwixmobile.core.zim_manager.fileselect_view.SelectionMode.NORMAL
import org.kiwix.kiwixmobile.zimManager.Fat32Checker.FileSystemState
import org.kiwix.kiwixmobile.zimManager.ZimManageViewModel.FileSelectActions.MultiModeFinished
import org.kiwix.kiwixmobile.zimManager.ZimManageViewModel.FileSelectActions.RequestDeleteMultiSelection
import org.kiwix.kiwixmobile.zimManager.ZimManageViewModel.FileSelectActions.RequestMultiSelection
import org.kiwix.kiwixmobile.zimManager.ZimManageViewModel.FileSelectActions.RequestNavigateTo
import org.kiwix.kiwixmobile.zimManager.ZimManageViewModel.FileSelectActions.RequestSelect
import org.kiwix.kiwixmobile.zimManager.ZimManageViewModel.FileSelectActions.RequestShareMultiSelection
import org.kiwix.kiwixmobile.zimManager.ZimManageViewModel.FileSelectActions.RestartActionMode
import org.kiwix.kiwixmobile.zimManager.ZimManageViewModel.FileSelectActions.UserClickedDownloadBooksButton
import org.kiwix.kiwixmobile.zimManager.fileselectView.FileSelectListState
import org.kiwix.kiwixmobile.zimManager.fileselectView.effects.DeleteFiles
import org.kiwix.kiwixmobile.zimManager.fileselectView.effects.NavigateToDownloads
import org.kiwix.kiwixmobile.zimManager.fileselectView.effects.None
import org.kiwix.kiwixmobile.zimManager.fileselectView.effects.OpenFileWithNavigation
import org.kiwix.kiwixmobile.zimManager.fileselectView.effects.ShareFiles
import org.kiwix.kiwixmobile.zimManager.fileselectView.effects.StartMultiSelection
import org.kiwix.kiwixmobile.zimManager.libraryView.adapter.LibraryListItem
import org.kiwix.kiwixmobile.zimManager.libraryView.adapter.LibraryListItem.BookItem
import org.kiwix.kiwixmobile.zimManager.libraryView.adapter.LibraryListItem.DividerItem
import org.kiwix.kiwixmobile.zimManager.libraryView.adapter.LibraryListItem.LibraryDownloadItem
import java.io.IOException
import java.util.LinkedList
import java.util.Locale
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import javax.inject.Inject

const val DEFAULT_PROGRESS = 0
const val MAX_PROGRESS = 100
private const val TAG_RX_JAVA_DEFAULT_ERROR_HANDLER = "RxJavaDefaultErrorHandler"

class ZimManageViewModel @Inject constructor(
  private val downloadDao: DownloadRoomDao,
  private val bookDao: NewBookDao,
  private val languageDao: NewLanguagesDao,
  private val storageObserver: StorageObserver,
  private var kiwixService: KiwixService,
  val context: Application,
  private val connectivityBroadcastReceiver: ConnectivityBroadcastReceiver,
  private val bookUtils: BookUtils,
  private val fat32Checker: Fat32Checker,
  private val defaultLanguageProvider: DefaultLanguageProvider,
  private val dataSource: DataSource,
  private val connectivityManager: ConnectivityManager,
  private val sharedPreferenceUtil: SharedPreferenceUtil
) : ViewModel() {
  sealed class FileSelectActions {
    data class RequestNavigateTo(val bookOnDisk: BookOnDisk) : FileSelectActions()
    data class RequestSelect(val bookOnDisk: BookOnDisk) : FileSelectActions()
    data class RequestMultiSelection(val bookOnDisk: BookOnDisk) : FileSelectActions()
    object RequestDeleteMultiSelection : FileSelectActions()
    object RequestShareMultiSelection : FileSelectActions()
    object MultiModeFinished : FileSelectActions()
    object RestartActionMode : FileSelectActions()
    object UserClickedDownloadBooksButton : FileSelectActions()
  }

  private var isUnitTestCase: Boolean = false
  val sideEffects: MutableSharedFlow<SideEffect<*>> = MutableSharedFlow()
  val libraryItems: MutableLiveData<List<LibraryListItem>> = MutableLiveData()
  val fileSelectListStates: MutableLiveData<FileSelectListState> = MutableLiveData()
  val deviceListScanningProgress = MutableLiveData<Int>()
  val libraryListIsRefreshing = MutableLiveData<Boolean>()
  val shouldShowWifiOnlyDialog = MutableLiveData<Boolean>()
  val networkStates = MutableLiveData<NetworkState>()

  val requestFileSystemCheck = MutableSharedFlow<Unit>(replay = 0)
  val fileSelectActions = MutableSharedFlow<FileSelectActions>()
  val requestDownloadLibrary = BehaviorProcessor.createDefault(Unit)
  val requestFiltering = BehaviorProcessor.createDefault("")
  val onlineBooksSearchedQuery = MutableLiveData<String>()

  private var compositeDisposable: CompositeDisposable? = CompositeDisposable()
  private val coroutineJobs: MutableList<Job> = mutableListOf()
  val downloadProgress = MutableLiveData<String>()

  private lateinit var alertDialogShower: AlertDialogShower

  init {
    compositeDisposable?.addAll(*disposables())
    observeCoroutineFlows()
    context.registerReceiver(connectivityBroadcastReceiver)
  }

  fun setIsUnitTestCase() {
    isUnitTestCase = true
  }

  fun setAlertDialogShower(alertDialogShower: AlertDialogShower) {
    this.alertDialogShower = alertDialogShower
  }

  private fun createKiwixServiceWithProgressListener(): KiwixService {
    if (isUnitTestCase) return kiwixService
    val contentLength = getContentLengthOfLibraryXmlFile()
    val customOkHttpClient =
      OkHttpClient().newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(CONNECTION_TIMEOUT, SECONDS)
        .readTimeout(READ_TIMEOUT, SECONDS)
        .callTimeout(CALL_TIMEOUT, SECONDS)
        .addNetworkInterceptor(
          HttpLoggingInterceptor().apply {
            level = if (DEBUG) BASIC else NONE
          }
        )
        .addNetworkInterceptor(UserAgentInterceptor(USER_AGENT))
        .addNetworkInterceptor { chain ->
          val originalResponse = chain.proceed(chain.request())
          originalResponse.body?.let { responseBody ->
            originalResponse.newBuilder()
              .body(
                ProgressResponseBody(
                  responseBody,
                  appProgressListener,
                  contentLength
                )
              )
              .build()
          } ?: originalResponse
        }
        .build()
    return KiwixService.ServiceCreator.newHackListService(customOkHttpClient, KIWIX_DOWNLOAD_URL)
      .also {
        kiwixService = it
      }
  }

  private var appProgressListener: AppProgressListenerProvider? = AppProgressListenerProvider(this)

  private fun getContentLengthOfLibraryXmlFile(): Long {
    val headRequest =
      Request.Builder()
        .url("$KIWIX_DOWNLOAD_URL$LIBRARY_NETWORK_PATH")
        .head()
        .header("Accept-Encoding", "identity")
        .build()
    val client =
      OkHttpClient().newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(CONNECTION_TIMEOUT, SECONDS)
        .readTimeout(READ_TIMEOUT, SECONDS)
        .callTimeout(CALL_TIMEOUT, SECONDS)
        .addNetworkInterceptor(UserAgentInterceptor(USER_AGENT))
        .build()
    try {
      client.newCall(headRequest).execute().use { response ->
        if (response.isSuccessful) {
          return@getContentLengthOfLibraryXmlFile response.header("content-length")?.toLongOrNull()
            ?: DEFAULT_INT_VALUE.toLong()
        }
      }
    } catch (_: Exception) {
      // do nothing
    }
    return DEFAULT_INT_VALUE.toLong()
  }

  @VisibleForTesting
  fun onClearedExposed() {
    onCleared()
  }

  private fun observeCoroutineFlows() {
    updateNetworkStates()
  }

  override fun onCleared() {
    super.onCleared()
    compositeDisposable?.clear()
    compositeDisposable = null
    coroutineJobs.forEach { it.cancel() }
    coroutineJobs.clear()
    connectivityBroadcastReceiver.stopNetworkState()
  }

  private fun disposables(): Array<Disposable> {
    // temporary converting to flowable. TODO we will refactor this in upcoming issue.
    val downloads = downloadDao.downloads().asFlowable()
    val booksFromDao = books().asFlowable()
    val networkLibrary = PublishProcessor.create<LibraryNetworkEntity>()
    val languages = languageDao.languages().asFlowable()
    return arrayOf(
      updateLibraryItems(booksFromDao, downloads, networkLibrary, languages),
      updateLanguagesInDao(networkLibrary, languages),
      updateNetworkStates(),
      requestsAndConnectivtyChangesToLibraryRequests(networkLibrary)
    ).also {
      setUpUncaughtErrorHandlerForOnlineLibrary(networkLibrary)
    }
  }

  private fun scanBooksFromStorage(dispatcher: CoroutineDispatcher = Dispatchers.IO) =
    viewModelScope.launch {
      withContext(dispatcher) {
        books()
          .let { checkFileSystemForBooksOnRequest(it) }
          .catch { it.printStackTrace() }
          .collect { books ->
            bookDao.insert(books)
          }
      }
    }

  @Suppress("TooGenericExceptionCaught")
  private fun fileSelectActions() =
    viewModelScope.launch {
      fileSelectActions
        .collect { action ->
          try {
            sideEffects.emit(
              when (action) {
                is RequestNavigateTo -> OpenFileWithNavigation(action.bookOnDisk)
                is RequestMultiSelection -> startMultiSelectionAndSelectBook(action.bookOnDisk)
                RequestDeleteMultiSelection -> DeleteFiles(selectionsFromState(), alertDialogShower)
                RequestShareMultiSelection -> ShareFiles(selectionsFromState())
                MultiModeFinished -> noSideEffectAndClearSelectionState()
                is RequestSelect -> noSideEffectSelectBook(action.bookOnDisk)
                RestartActionMode -> StartMultiSelection(fileSelectActions)
                UserClickedDownloadBooksButton -> NavigateToDownloads
              }
            )
          } catch (e: Throwable) {
            e.printStackTrace()
          }
        }
    }

  private fun startMultiSelectionAndSelectBook(
    bookOnDisk: BookOnDisk
  ): StartMultiSelection {
    fileSelectListStates.value?.let {
      fileSelectListStates.postValue(
        it.copy(
          bookOnDiskListItems = selectBook(it, bookOnDisk),
          selectionMode = MULTI
        )
      )
    }
    return StartMultiSelection(fileSelectActions)
  }

  private fun selectBook(
    it: FileSelectListState,
    bookOnDisk: BookOnDisk
  ): List<BooksOnDiskListItem> {
    return it.bookOnDiskListItems.map { listItem ->
      if (listItem.id == bookOnDisk.id) {
        listItem.apply { isSelected = !isSelected }
      } else {
        listItem
      }
    }
  }

  private fun noSideEffectSelectBook(bookOnDisk: BookOnDisk): SideEffect<Unit> {
    fileSelectListStates.value?.let {
      fileSelectListStates.postValue(
        it.copy(bookOnDiskListItems = selectBook(it, bookOnDisk))
      )
    }
    return None
  }

  private fun selectionsFromState() = fileSelectListStates.value?.selectedBooks.orEmpty()

  private fun noSideEffectAndClearSelectionState(): SideEffect<Unit> {
    fileSelectListStates.value?.let {
      fileSelectListStates.postValue(
        it.copy(
          bookOnDiskListItems =
            it.bookOnDiskListItems.map { booksOnDiskListItem ->
              booksOnDiskListItem.apply { isSelected = false }
            },
          selectionMode = NORMAL
        )
      )
    }
    return None
  }

  private fun updateNetworkStates() {
    viewModelScope.launch {
      connectivityBroadcastReceiver.networkStates.collect { state ->
        networkStates.postValue(state)
      }
    }
  }

  private fun requestsAndConnectivtyChangesToLibraryRequests(
    library: PublishProcessor<LibraryNetworkEntity>
  ) =
    Flowable.combineLatest(
      requestDownloadLibrary,
      connectivityBroadcastReceiver.networkStates.asFlowable().distinctUntilChanged().filter(
        CONNECTED::equals
      )
    ) { _, _ -> }
      .switchMap {
        if (connectivityManager.isWifi()) {
          Flowable.just(Unit)
        } else {
          sharedPreferenceUtil.prefWifiOnlys
            .asFlowable()
            .doOnNext {
              if (it) {
                shouldShowWifiOnlyDialog.postValue(true)
              }
            }
            .filter { !it }
            .map { }
        }
      }
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.io())
      .concatMap {
        Flowable.fromCallable {
          synchronized(this, ::createKiwixServiceWithProgressListener)
        }
      }
      .concatMap {
        kiwixService.library
          .toFlowable()
          .retry(5)
          .doOnSubscribe {
            downloadProgress.postValue(
              context.getString(R.string.starting_downloading_remote_library)
            )
          }
          .map { response ->
            downloadProgress.postValue(context.getString(R.string.parsing_remote_library))
            response
          }
          .doFinally {
            downloadProgress.postValue(context.getString(R.string.parsing_remote_library))
          }
          .onErrorReturn {
            it.printStackTrace()
            LibraryNetworkEntity().apply { book = LinkedList() }
          }
      }
      .subscribe(library::onNext, Throwable::printStackTrace).also {
        compositeDisposable?.add(it)
      }

  private fun updateLibraryItems(
    booksFromDao: io.reactivex.rxjava3.core.Flowable<List<BookOnDisk>>,
    downloads: io.reactivex.rxjava3.core.Flowable<List<DownloadModel>>,
    library: Flowable<LibraryNetworkEntity>,
    languages: io.reactivex.rxjava3.core.Flowable<List<Language>>
  ) {
    viewModelScope.launch {
      val requestFilteringFlow = flow {
        emit("")
        requestFiltering
          .asFlowable()
          .asFlow()
          .onEach { libraryListIsRefreshing.postValue(true) }
          .collect { emit(it) }
      }

      booksFromDao.asFlow()
        .combine(downloads.asFlow()) { books, downloadsList -> Pair(books, downloadsList) }
        .combine(languages.asFlow().filter(List<Language>::isNotEmpty)) { (books, downloads), langs -> Triple(books, downloads, langs) }
        .combine(library.asFlow()) { (books, downloads, langs), lib -> arrayOf(books, downloads, langs, lib) }
        .combine(requestFilteringFlow) { arr, filter -> arr + filter }
        .combine(fat32Checker.fileSystemStates.asFlowable().asFlow()) { arr, state -> arr + state }
        .map { arr -> 
          @Suppress("UNCHECKED_CAST")
          combineLibrarySources(
            arr[0] as List<BookOnDisk>,
            arr[1] as List<DownloadModel>,
            arr[2] as List<Language>,
            arr[3] as LibraryNetworkEntity,
            arr[4] as String,
            arr[5] as FileSystemState
          )
        }
        .onEach { libraryListIsRefreshing.postValue(false) }
        .catch { throwable ->
          if (throwable is OutOfMemoryError) {
            Log.e("ZimManageViewModel", "Error----${throwable.printStackTrace()}")
          }
        }
        .collect { items ->
          libraryItems.postValue(items)
        }
    }
  }

  private fun updateLanguagesInDao(
    library: Flowable<LibraryNetworkEntity>,
    languages: io.reactivex.rxjava3.core.Flowable<List<Language>>
  ) {
    viewModelScope.launch {
      library
        .asFlow()
        .map(LibraryNetworkEntity::book)
        .combine(languages.asFlow()) { books, langs ->
          combineToLanguageList(books, langs)
        }
        .map { it.sortedBy(Language::language) }
        .filter(List<Language>::isNotEmpty)
        .catch { throwable ->
          throwable.printStackTrace()
        }
        .collect { languageList ->
          languageDao.insert(languageList)
        }
    }
  }

  private fun combineToLanguageList(
    booksFromNetwork: List<Book>,
    allLanguages: List<Language>
  ) = when {
    booksFromNetwork.isEmpty() && allLanguages.isEmpty() -> defaultLanguage()
    booksFromNetwork.isEmpty() && allLanguages.isNotEmpty() -> emptyList()
    booksFromNetwork.isNotEmpty() && allLanguages.isEmpty() ->
      fromLocalesWithNetworkMatchesSetActiveBy(
        networkLanguageCounts(booksFromNetwork),
        defaultLanguage()
      )

    booksFromNetwork.isNotEmpty() && allLanguages.isNotEmpty() ->
      fromLocalesWithNetworkMatchesSetActiveBy(
        networkLanguageCounts(booksFromNetwork),
        allLanguages
      )

    else -> throw RuntimeException("Impossible state")
  }

  private fun networkLanguageCounts(booksFromNetwork: List<Book>) =
    booksFromNetwork.mapNotNull(Book::language)
      .fold(
        mutableMapOf<String, Int>()
      ) { acc, language -> acc.increment(language) }

  private fun <K> MutableMap<K, Int>.increment(key: K) =
    apply { set(key, getOrElse(key) { 0 } + 1) }

  private fun fromLocalesWithNetworkMatchesSetActiveBy(
    networkLanguageCounts: MutableMap<String, Int>,
    listToActivateBy: List<Language>
  ) = Locale.getISOLanguages()
    .map { it.convertToLocal() }
    .filter { networkLanguageCounts.containsKey(it.isO3Language) }
    .map { locale ->
      Language(
        locale.isO3Language,
        languageIsActive(listToActivateBy, locale),
        networkLanguageCounts.getOrElse(locale.isO3Language) { 0 }
      )
    }

  private fun defaultLanguage() =
    listOf(
      defaultLanguageProvider.provide()
    )

  private fun languageIsActive(
    allLanguages: List<Language>,
    locale: Locale
  ) = allLanguages.firstOrNull { it.languageCode == locale.isO3Language }?.active == true

  @Suppress("UnsafeCallOnNullableType")
  private fun combineLibrarySources(
    booksOnFileSystem: List<BookOnDisk>,
    activeDownloads: List<DownloadModel>,
    allLanguages: List<Language>,
    libraryNetworkEntity: LibraryNetworkEntity,
    filter: String,
    fileSystemState: FileSystemState
  ): List<LibraryListItem> {
    val activeLanguageCodes =
      allLanguages.filter(Language::active)
        .map(Language::languageCode)
    val allBooks = libraryNetworkEntity.book!! - booksOnFileSystem.map(BookOnDisk::book).toSet()
    val downloadingBooks =
      activeDownloads.mapNotNull { download ->
        allBooks.firstOrNull { it.id == download.book.id }
      }
    val booksUnfilteredByLanguage =
      applySearchFilter(
        allBooks - downloadingBooks.toSet(),
        filter
      )

    val booksWithActiveLanguages =
      booksUnfilteredByLanguage.filter { activeLanguageCodes.contains(it.language) }
    val booksWithoutActiveLanguages = booksUnfilteredByLanguage - booksWithActiveLanguages.toSet()
    return createLibrarySection(
      downloadingBooks,
      activeDownloads,
      fileSystemState,
      R.string.downloading,
      Long.MAX_VALUE
    ) +
      createLibrarySection(
        booksWithActiveLanguages,
        emptyList(),
        fileSystemState,
        R.string.your_languages,
        Long.MAX_VALUE - 1
      ) +
      createLibrarySection(
        booksWithoutActiveLanguages,
        emptyList(),
        fileSystemState,
        R.string.other_languages,
        Long.MIN_VALUE
      )
  }

  private fun createLibrarySection(
    books: List<Book>,
    activeDownloads: List<DownloadModel>,
    fileSystemState: FileSystemState,
    sectionStringId: Int,
    sectionId: Long
  ) =
    if (books.isNotEmpty()) {
      listOf(DividerItem(sectionId, sectionStringId)) +
        books.asLibraryItems(activeDownloads, fileSystemState)
    } else {
      emptyList()
    }

  private fun applySearchFilter(
    unDownloadedBooks: List<Book>,
    filter: String
  ) = if (filter.isEmpty()) {
    unDownloadedBooks
  } else {
    unDownloadedBooks.iterator().forEach { it.calculateSearchMatches(filter, bookUtils) }
    unDownloadedBooks.filter { it.searchMatches > 0 }
  }

  private fun List<Book>.asLibraryItems(
    activeDownloads: List<DownloadModel>,
    fileSystemState: FileSystemState
  ) = map { book ->
    activeDownloads.firstOrNull { download -> download.book == book }
      ?.let(::LibraryDownloadItem)
      ?: BookItem(book, fileSystemState)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun checkFileSystemForBooksOnRequest(
    booksFromDao: Flow<List<BookOnDisk>>
  ): Flow<List<BookOnDisk>> = requestFileSystemCheck
    .flatMapLatest {
      // Initial progress
      deviceListScanningProgress.postValue(DEFAULT_PROGRESS)
      booksFromStorageNotIn(
        booksFromDao,
        object : ScanningProgressListener {
          override fun onProgressUpdate(scannedDirectory: Int, totalDirectory: Int) {
            val overallProgress =
              (scannedDirectory.toDouble() / totalDirectory.toDouble() * MAX_PROGRESS).toInt()
            if (overallProgress != MAX_PROGRESS) {
              deviceListScanningProgress.postValue(overallProgress)
            }
          }
        }
      )
    }
    .onEach {
      deviceListScanningProgress.postValue(MAX_PROGRESS)
    }
    .filter { it.isNotEmpty() }
    .map { books -> books.distinctBy { it.book.id } }

  private fun books() =
    bookDao.books()
      .map { it.sortedBy { book -> book.book.title } }

  private fun booksFromStorageNotIn(
    booksFromDao: Flow<List<BookOnDisk>>,
    scanningProgressListener: ScanningProgressListener
  ): Flow<List<BookOnDisk>> = flow {
    val scannedBooks = storageObserver.getBooksOnFileSystem(scanningProgressListener).first()
    val daoBookIds = booksFromDao.first().map { it.book.id }
    emit(removeBooksAlreadyInDao(scannedBooks, daoBookIds))
  }

  private fun removeBooksAlreadyInDao(
    booksFromFileSystem: Collection<BookOnDisk>,
    idsInDao: List<String>
  ) = booksFromFileSystem.filterNot { idsInDao.contains(it.book.id) }

  private fun updateBookItems() =
    viewModelScope.launch {
      dataSource.booksOnDiskAsListItems()
        .catch { it.printStackTrace() }
        .collect { newList ->
          val currentState = fileSelectListStates.value
          val updatedState = currentState?.let {
            inheritSelections(it, newList.toMutableList())
          } ?: FileSelectListState(newList)

          fileSelectListStates.postValue(updatedState)
        }
    }

  private fun inheritSelections(
    oldState: FileSelectListState,
    newList: MutableList<BooksOnDiskListItem>
  ): FileSelectListState {
    return oldState.copy(
      bookOnDiskListItems =
        newList.map { newBookOnDisk ->
          val firstOrNull =
            oldState.bookOnDiskListItems.firstOrNull { oldBookOnDisk ->
              oldBookOnDisk.id == newBookOnDisk.id
            }
          newBookOnDisk.apply { isSelected = firstOrNull?.isSelected == true }
        }
    )
  }

  private fun setUpUncaughtErrorHandlerForOnlineLibrary(
    library: PublishProcessor<LibraryNetworkEntity>
  ) {
    RxJavaPlugins.setErrorHandler { exception ->
      if (exception is RuntimeException && exception.cause == IOException()) {
        Log.i(
          TAG_RX_JAVA_DEFAULT_ERROR_HANDLER,
          "Caught undeliverable exception: ${exception.cause}"
        )
      }
      when (exception) {
        is UndeliverableException -> {
          library.onNext(
            LibraryNetworkEntity().apply { book = LinkedList() }
          ).also {
            Log.i(
              TAG_RX_JAVA_DEFAULT_ERROR_HANDLER,
              "Caught undeliverable exception: ${exception.cause}"
            )
          }
        }

        else -> {
          Thread.currentThread().also { thread ->
            thread.uncaughtExceptionHandler?.uncaughtException(thread, exception)
          }
        }
      }
    }
  }
}
