package org.mifos.module.stellar.federation;

import java.util.Optional;

public class StellarAccountId {
  final String publicKey;
  final Optional<String> subAccount;

  static public StellarAccountId mainAccount(final String publicKey)
  {
    return new StellarAccountId(publicKey, Optional.<String>empty());
  }

  static public StellarAccountId subAccount(final String mainAccountPublicKey,
      final String subAccountId)
  {
    //TODO: Check what the correct form of the memo should be here...
    return new StellarAccountId(mainAccountPublicKey, Optional.of(subAccountId));
  }

  private StellarAccountId(final String publicKey, final Optional<String> subAccount)
  {
    this.publicKey = publicKey;
    this.subAccount = subAccount;
  }

  public String getPublicKey() {
    return publicKey;
  }

  public Optional<String> getSubAccount() {
    return subAccount;
  }
}
