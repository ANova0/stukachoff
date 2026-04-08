package com.stukachoff.di;

import com.stukachoff.domain.checker.DnsChecker;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
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
public final class CheckersModule_ProvideDnsCheckerFactory implements Factory<DnsChecker> {
  @Override
  public DnsChecker get() {
    return provideDnsChecker();
  }

  public static CheckersModule_ProvideDnsCheckerFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static DnsChecker provideDnsChecker() {
    return Preconditions.checkNotNullFromProvides(CheckersModule.INSTANCE.provideDnsChecker());
  }

  private static final class InstanceHolder {
    private static final CheckersModule_ProvideDnsCheckerFactory INSTANCE = new CheckersModule_ProvideDnsCheckerFactory();
  }
}
