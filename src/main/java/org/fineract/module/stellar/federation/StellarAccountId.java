package org.fineract.module.stellar.federation;

import java.util.Objects;
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

  @Override public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    StellarAccountId that = (StellarAccountId) o;
    return Objects.equals(publicKey, that.publicKey) && Objects.equals(subAccount, that.subAccount);
  }

  @Override public int hashCode() {
    return Objects.hash(publicKey, subAccount);
  }
}
