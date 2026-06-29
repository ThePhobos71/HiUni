package de.transio.hiuni.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.BuildConfig
import de.transio.hiuni.core.network.OkHttpClientProvider
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient =
        OkHttpClientProvider.create(context, debug = BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttp: OkHttpClient
    ): coil.ImageLoader = coil.ImageLoader.Builder(context)
        .okHttpClient(okHttp)
        // Disk-Cache aggressiv: 100MB, persistiert über App-Restarts, sodass
        // Movie-Poster bei Re-Open sofort da sind statt erneut zu pullen.
        .diskCache(
            coil.disk.DiskCache.Builder()
                .directory(context.cacheDir.resolve("coil_image_cache"))
                .maxSizeBytes(100L * 1024 * 1024)
                .build()
        )
        // Speicher-Cache als RAM-LRU; Compose-Subscreens behalten so Bilder
        // ohne Re-Decoding-Cost.
        .memoryCache(
            coil.memory.MemoryCache.Builder(context)
                .maxSizePercent(0.20)
                .build()
        )
        .respectCacheHeaders(false)
        .build()
}
