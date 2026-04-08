package com.stukachoff.di;

import android.content.Context;
import com.stukachoff.domain.checker.VpnStatusChecker;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class CheckersModule_ProvideVpnStatusCheckerFactory implements Factory<VpnStatusChecker> {
  private final Provider<Context> ctxProvider;

  public CheckersModule_ProvideVpnStatusCheckerFactory(Provider<Context> ctxProvider) {
    this.ctxProvider = ctxProvider;
  }

  @Override
  public VpnStatusChecker get() {
    return provideVpnStatusChecker(ctxProvider.get());
  }

  public static CheckersModule_ProvideVpnStatusCheckerFactory create(
      Provider<Context> ctxProvider) {
    return new CheckersModule_ProvideVpnStatusCheckerFactory(ctxProvider);
  }

  public static VpnStatusChecker provideVpnStatusChecker(Context ctx) {
    return Preconditions.checkNotNullFromProvides(CheckersModule.INSTANCE.provideVpnStatusChecker(ctx));
  }
}
