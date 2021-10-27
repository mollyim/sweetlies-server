/*
 * Copyright 2013-2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotEmpty;
import java.util.List;

public class PaymentsServiceConfiguration {

  @NotEmpty
  @JsonProperty
  private String fixerApiKey;

  @NotEmpty
  @JsonProperty
  private List<String> paymentCurrencies;

  public String getFixerApiKey() {
    return fixerApiKey;
  }

  public List<String> getPaymentCurrencies() {
    return paymentCurrencies;
  }
}
