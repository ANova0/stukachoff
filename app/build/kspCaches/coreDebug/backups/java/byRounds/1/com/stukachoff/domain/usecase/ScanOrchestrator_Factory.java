package com.stukachoff.domain.usecase;

import com.stukachoff.domain.checker.AndroidVersionChecker;
import com.stukachoff.domain.checker.DnsChecker;
import com.stukachoff.domain.checker.InterfaceChecker;
import com.stukachoff.domain.checker.PortScanner;
import com.stukachoff.domain.checker.VpnStatusChecker;
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
public final class ScanOrchestrator_Factory implements Factory<ScanOrchestrator> {
  private final Provider<VpnStatusChecker> vpnStatusCheckerProvider;

  private final Provider<PortScanner> portScannerProvider;

  private final Provider<InterfaceChecker> interfaceCheckerProvider;

  private final Provider<DnsChecker> dnsCheckerProvider;

  private final Provider<AndroidVersionChecker> androidVersionCheckerProvider;

  public ScanOrchestrator_Factory(Provider<VpnStatusChecker> vpnStatusCheckerProvider,
      Provider<PortScanner> portScannerProvider,
      Provider<InterfaceChecker> interfaceCheckerProvider, Provider<DnsChecker> dnsCheckerProvider,
      Provider<AndroidVersionChecker> androidVersionCheckerProvider) {
    this.vpnStatusCheckerProvider = vpnStatusCheckerProvider;
    this.portScannerProvider = portScannerProvider;
    this.interfaceCheckerProvider = interfaceCheckerProvider;
    this.dnsCheckerProvider = dnsCheckerProvider;
    this.androidVersionCheckerProvider = androidVersionCheckerProvider;
  }

  @Override
  public ScanOrchestrator get() {
    return newInstance(vpnStatusCheckerProvider.get(), portScannerProvider.get(), interfaceCheckerProvider.get(), dnsCheckerProvider.get(), androidVersionCheckerProvider.get());
  }

  public static ScanOrchestrator_Factory create(Provider<VpnStatusChecker> vpnStatusCheckerProvider,
      Provider<PortScanner> portScannerProvider,
      Provider<InterfaceChecker> interfaceCheckerProvider, Provider<DnsChecker> dnsCheckerProvider,
      Provider<AndroidVersionChecker> androidVersionCheckerProvider) {
    return new ScanOrchestrator_Factory(vpnStatusCheckerProvider, portScannerProvider, interfaceCheckerProvider, dnsCheckerProvider, androidVersionCheckerProvider);
  }

  public static ScanOrchestrator newInstance(VpnStatusChecker vpnStatusChecker,
      PortScanner portScanner, InterfaceChecker interfaceChecker, DnsChecker dnsChecker,
      AndroidVersionChecker androidVersionChecker) {
    return new ScanOrchestrator(vpnStatusChecker, portScanner, interfaceChecker, dnsChecker, androidVersionChecker);
  }
}
