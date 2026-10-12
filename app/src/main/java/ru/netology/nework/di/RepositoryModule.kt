package ru.netology.nework.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ru.netology.nework.data.repository.NeWorkRepositoryImpl
import ru.netology.nework.domain.repository.NeWorkRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindNeWorkRepository(repository: NeWorkRepositoryImpl): NeWorkRepository
}
