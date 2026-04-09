package com.stukachoff.di

import android.content.Context
import com.stukachoff.data.network.DnsCheckerImpl
import com.stukachoff.data.network.InterfaceCheckerImpl
import com.stukachoff.data.network.PortScannerImpl
import com.stukachoff.data.network.VpnStatusCheckerImpl
import com.stukachoff.domain.checker.DnsChecker
import com.stukachoff.domain.checker.InterfaceChecker
import com.stukachoff.domain.checker.PortScanner
import com.stukachoff.domain.checker.VpnStatusChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CheckersModule {

    @Provides @Singleton
    fun provideVpnStatusChecker(@ApplicationContext ctx: Context): VpnStatusChecker =
        VpnStatusCheckerImpl(ctx)

    @Provides @Singleton
    fun providePortScanner(): PortScanner = PortScannerImpl()

    @Provides @Singleton
    fun provideInterfaceChecker(): InterfaceChecker = InterfaceCheckerImpl()

    @Provides @Singleton
    fun provideDnsChecker(@ApplicationContext ctx: Context): DnsChecker = DnsCheckerImpl(ctx)
}
