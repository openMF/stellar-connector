/**
 * Copyright 2016 Myrle Krantz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mifos.module.stellar.federation;

import com.google.common.net.InternetDomainName;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StellarAddress {

  private final InternetDomainName domain;
  private final String tenantName;
  private final Optional<String> userAccountId;
  private final boolean isVaultAddress;

  public static StellarAddress forTenant(final String tenantName, final String domain)
  {
    return new StellarAddress(InternetDomainName.from(domain), tenantName, Optional.empty());
  }

  public static StellarAddress parse(final String address)
      throws InvalidStellarAddressException
  {
    //I chose not to use Pattern.UNICODE_CHARACTER_CLASS because of potential performance issues and
    //no current knowledge of use cases which require unicode addresses.  Certainly the domain
    //name can't contain unicode characters.  According to the federation servers specs at
    //Stellar, the part before the * might contain them.  Depending on what use cases we encounter,
    //we may need to adjust this.
    final Pattern stellarAddressPattern = Pattern.compile(
        "(?<name>^[^\\:\\*@\\p{Space}]+)(:(?<subname>[^\\:\\*@\\p{Space}]+))?+\\*(?<domain>[\\p{Alnum}-\\.]+)$");

    final Matcher addressMatcher = stellarAddressPattern.matcher(address);
    if (!addressMatcher.matches()) {
      throw InvalidStellarAddressException.nonConformantStellarAddress(address);
    }

    if (addressMatcher.group("subname") != null)
    {
      return new StellarAddress(getInternetDomainName(addressMatcher.group("domain")),
          addressMatcher.group("name"), Optional.of(addressMatcher.group("subname")));
    }
    else
    {
      return new StellarAddress(getInternetDomainName(addressMatcher.group("domain")),
          addressMatcher.group("name"), Optional.empty());
    }
  }

  private static InternetDomainName getInternetDomainName(final String domain)
      throws InvalidStellarAddressException
  {
    try {
      return InternetDomainName.from(domain);
    }
    catch (final IllegalArgumentException e)
    {
      throw InvalidStellarAddressException.invalidDomainName(domain);
    }
  }

  private StellarAddress(
      final InternetDomainName domain,
      final String tenantName,
      final Optional<String> userAccountId)
  {
    this.domain = domain;
    this.tenantName = tenantName;
    if (userAccountId.orElse("").equals("vault")) {
      isVaultAddress = true;
      this.userAccountId = Optional.empty();
    }
    else
    {
      isVaultAddress = false;
      this.userAccountId = userAccountId;
    }
  }

  public String toString() {
    if (isVaultAddress) {
      return tenantName + ":" + "vault" + "*" + domain.toString();
    }
    else if (userAccountId.isPresent()) {
      return tenantName + ":" + userAccountId.get() + "*" + domain.toString();
    }
    else
    {
      return tenantName + "*" + domain.toString();
    }
  }

  public InternetDomainName getDomain() {
    return domain;
  }

  public String getTenantName() {
    return tenantName;
  }

  public Optional<String> getUserAccountId() {
    return userAccountId;
  }

  public boolean isVaultAddress() {
    return isVaultAddress;
  }
}
