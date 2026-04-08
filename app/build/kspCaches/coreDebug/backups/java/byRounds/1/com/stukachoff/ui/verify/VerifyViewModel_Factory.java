package com.stukachoff.ui.verify;

import com.stukachoff.domain.usecase.ScanOrchestrator;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class VerifyViewModel_Factory implements Factory<VerifyViewModel> {
  private final Provider<ScanOrchestrator> orchestratorProvider;

  public VerifyViewModel_Factory(Provider<ScanOrchestrator> orchestratorProvider) {
    this.orchestratorProvider = orchestratorProvider;
  }

  @Override
  public VerifyViewModel get() {
    return newInstance(orchestratorProvider.get());
  }

  public static VerifyViewModel_Factory create(Provider<ScanOrchestrator> orchestratorProvider) {
    return new VerifyViewModel_Factory(orchestratorProvider);
  }

  public static VerifyViewModel newInstance(ScanOrchestrator orchestrator) {
    return new VerifyViewModel(orchestrator);
  }
}
