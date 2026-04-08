package com.stukachoff.domain.checker;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class AndroidVersionChecker_Factory implements Factory<AndroidVersionChecker> {
  @Override
  public AndroidVersionChecker get() {
    return newInstance();
  }

  public static AndroidVersionChecker_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static AndroidVersionChecker newInstance() {
    return new AndroidVersionChecker();
  }

  private static final class InstanceHolder {
    private static final AndroidVersionChecker_Factory INSTANCE = new AndroidVersionChecker_Factory();
  }
}
