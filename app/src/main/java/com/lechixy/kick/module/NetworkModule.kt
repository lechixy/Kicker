package com.lechixy.kick.module

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import com.lechixy.kick.data.common.remote.KickNetwork
import com.lechixy.kick.data.common.repository.KickRepository

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideKickRepository(): KickRepository {
        return KickRepository(KickNetwork.api)
    }
}