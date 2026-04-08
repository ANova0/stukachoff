package com.stukachoff.di;

import com.stukachoff.domain.checker.PortScanner;
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
public final class CheckersModule_ProvidePortScannerFactory implements Factory<PortScanner> {
  @Override
  public PortScanner get() {
    return providePortScanner();
  }

  public static CheckersModule_ProvidePortScannerFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static PortScanner providePortScanner() {
    return Preconditions.checkNotNullFromProvides(CheckersModule.INSTANCE.providePortScanner());
  }

  private static final class InstanceHolder {
    private static final CheckersModule_ProvidePortScannerFactory INSTANCE = new CheckersModule_ProvidePortScannerFactory();
  }
}
