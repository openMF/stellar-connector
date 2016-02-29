package org.mifos.module.stellar.fineractadapter;

/**
 * Created by myrle on 29.02.16.
 */
public class AuthenticationResponse {

  private String base64EncodedAuthenticationKey;

  public AuthenticationResponse() {
    super();
  }

  public String getBase64EncodedAuthenticationKey() {
    return base64EncodedAuthenticationKey;
  }

  public void setBase64EncodedAuthenticationKey(String base64EncodedAuthenticationKey) {
    this.base64EncodedAuthenticationKey = base64EncodedAuthenticationKey;
  }
}
