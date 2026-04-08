package com.stukachoff.di;

import com.stukachoff.domain.checker.InterfaceChecker;
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
public final class CheckersModule_ProvideInterfaceCheckerFactory implements Factory<InterfaceChecker> {
  @Override
  public InterfaceChecker get() {
    return provideInterfaceChecker();
  }

  public static CheckersModule_ProvideInterfaceCheckerFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static InterfaceChecker provideInterfaceChecker() {
    return Preconditions.checkNotNullFromProvides(CheckersModule.INSTANCE.provideInterfaceChecker());
  }

  private static final class InstanceHolder {
    private static final CheckersModule_ProvideInterfaceCheckerFactory INSTANCE = new CheckersModule_ProvideInterfaceCheckerFactory();
  }
}
