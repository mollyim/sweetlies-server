/*
 * Copyright 2013-2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.tests.controllers;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import io.dropwizard.auth.PolymorphicAuthValueFactoryProvider;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.whispersystems.textsecuregcm.auth.AuthenticatedAccount;
import org.whispersystems.textsecuregcm.auth.DisabledPermittedAuthenticatedAccount;
import org.whispersystems.textsecuregcm.controllers.PaymentsController;
import org.whispersystems.textsecuregcm.currency.CurrencyConversionManager;
import org.whispersystems.textsecuregcm.entities.CurrencyConversionEntity;
import org.whispersystems.textsecuregcm.entities.CurrencyConversionEntityList;
import org.whispersystems.textsecuregcm.tests.util.AuthHelper;

@ExtendWith(DropwizardExtensionsSupport.class)
class PaymentsControllerTest {

  private static final CurrencyConversionManager currencyManager = mock(CurrencyConversionManager.class);

  private static final ResourceExtension resources = ResourceExtension.builder()
      .addProvider(AuthHelper.getAuthFilter())
      .addProvider(new PolymorphicAuthValueFactoryProvider.Binder<>(
          ImmutableSet.of(AuthenticatedAccount.class, DisabledPermittedAuthenticatedAccount.class)))
      .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
      .addResource(new PaymentsController(currencyManager))
      .build();


  @BeforeEach
  void setup() {
    when(currencyManager.getCurrencyConversions()).thenReturn(Optional.of(
        new CurrencyConversionEntityList(List.of(
            new CurrencyConversionEntity("FOO", Map.of(
                "USD", new BigDecimal("2.35"),
                "EUR", new BigDecimal("1.89")
            )),
            new CurrencyConversionEntity("BAR", Map.of(
                "USD", new BigDecimal("1.50"),
                "EUR", new BigDecimal("0.98")
            ))
        ), System.currentTimeMillis())));
  }

  @Test
  void testGetCurrencyConversions() {
    CurrencyConversionEntityList conversions =
        resources.getJerseyTest()
                 .target("/v1/payments/conversions")
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
                 .get(CurrencyConversionEntityList.class);


    assertThat(conversions.getCurrencies().size()).isEqualTo(2);
    assertThat(conversions.getCurrencies().get(0).getBase()).isEqualTo("FOO");
    assertThat(conversions.getCurrencies().get(0).getConversions().get("USD")).isEqualTo(new BigDecimal("2.35"));
  }

  @Test
  void testGetCurrencyConversions_Json() {
    String json =
        resources.getJerseyTest()
            .target("/v1/payments/conversions")
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
            .get(String.class);

    // the currency serialization might occur in either order
    assertThat(json).containsPattern("\\{(\"EUR\":1.89,\"USD\":2.35|\"USD\":2.35,\"EUR\":1.89)}");
  }

}
