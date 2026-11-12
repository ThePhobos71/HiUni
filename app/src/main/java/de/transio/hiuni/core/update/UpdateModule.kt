package de.transio.hiuni.core.update

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.BuildConfig

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {

    @Binds abstract fun bindPreferences(impl: DataStoreUpdatePreferences): UpdatePreferences
    @Binds abstract fun bindSource(impl: GitHubReleaseSource): ReleaseSource
    @Binds abstract fun bindNotifier(impl: AndroidUpdateNotifier): UpdateNotifier
    @Binds abstract fun bindDownloader(impl: OkHttpApkDownloader): ApkDownloader
    @Binds abstract fun bindVerifier(impl: AndroidApkVerifier): ApkVerifier
    @Binds abstract fun bindInstaller(impl: PackageInstallerUpdateInstaller): UpdateInstaller

    companion object {
        @Provides
        fun provideBuildInfo(@ApplicationContext context: Context): AppBuildInfo = AppBuildInfo(
            isDebug = BuildConfig.DEBUG,
            versionCode = BuildConfig.VERSION_CODE,
            versionName = BuildConfig.VERSION_NAME,
            packageName = context.packageName
        )
    }
}
