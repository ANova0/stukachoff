package com.stukachoff.di

import com.stukachoff.data.update.FullNetworkUpdateSource
import com.stukachoff.data.update.NetworkUpdateSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {
    @Binds @Singleton
    abstract fun bindNetworkUpdateSource(impl: FullNetworkUpdateSource): NetworkUpdateSource
}
