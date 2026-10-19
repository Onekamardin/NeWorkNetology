package ru.netology.nework.di

import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.netology.nework.BuildConfig
import ru.netology.nework.data.api.NeWorkApi
import ru.netology.nework.data.local.AuthStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideApiKeyInterceptor(authStore: AuthStore): Interceptor = Interceptor { chain ->
        val requestBuilder = chain.request().newBuilder()
            .header("Api-Key", BuildConfig.API_KEY)
            .header("Accept", "application/json")
        authStore.token()?.takeIf(String::isNotBlank)?.let { requestBuilder.header("Authorization", it) }
        val request = requestBuilder.build()
        chain.proceed(request)
    }

    @Provides @Singleton
    fun provideOkHttp(apiKeyInterceptor: Interceptor): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(apiKeyInterceptor)
        .build()

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
        .build()

    @Provides @Singleton
    fun provideApi(retrofit: Retrofit): NeWorkApi = retrofit.create(NeWorkApi::class.java)
}
