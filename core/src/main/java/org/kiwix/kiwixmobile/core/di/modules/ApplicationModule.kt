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
package org.kiwix.kiwixmobile.core.di.modules

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.storage.StorageManager
import dagger.Module
import dagger.Provides
import org.kiwix.kiwixmobile.core.DarkModeConfig
import org.kiwix.kiwixmobile.core.data.remote.ObjectBoxToLibkiwixMigrator
import org.kiwix.kiwixmobile.core.data.remote.ObjectBoxToRoomMigrator
import org.kiwix.kiwixmobile.core.downloader.DownloadMonitor
import org.kiwix.kiwixmobile.core.downloader.downloadManager.DownloadManagerMonitor
import org.kiwix.kiwixmobile.core.reader.ZimFileReader
import org.kiwix.kiwixmobile.core.utils.BookUtils
import javax.inject.Singleton

@Module(
  includes = [
    DownloaderModule::class,
    DatabaseModule::class
  ]
)
class ApplicationModule {
  @Provides
  @Singleton
  internal fun provideApplication(context: Context): Application =
    context as Application

  @Provides
  @Singleton
  internal fun provideNotificationManager(context: Context): NotificationManager =
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  @Provides
  @Singleton
  internal fun provideBookUtils(): BookUtils = BookUtils()

  @Provides
  @Singleton
  fun provideObjectBoxToLibkiwixMigrator() = ObjectBoxToLibkiwixMigrator()

  @Provides
  @Singleton
  fun provideObjectBoxToRoomMigrator() = ObjectBoxToRoomMigrator()

  @Provides
  @Singleton
  internal fun provideDownloadMonitor(
    downloadManagerMonitor: DownloadManagerMonitor
  ): DownloadMonitor = downloadManagerMonitor

  @Provides
  @Singleton
  internal fun provideStorageManager(context: Context): StorageManager =
    context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

  @Provides
  @Singleton
  internal fun providesZimFileReaderFactory(darkModeConfig: DarkModeConfig):
    ZimFileReader.Factory = ZimFileReader.Factory.Impl(darkModeConfig)

  @Provides
  @Singleton
  fun provideConnectivityManager(context: Context): ConnectivityManager =
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
}
