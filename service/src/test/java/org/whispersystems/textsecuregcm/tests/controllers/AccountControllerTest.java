/*
 * Copyright 2013-2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.tests.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import io.dropwizard.auth.PolymorphicAuthValueFactoryProvider;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.Ignore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.whispersystems.textsecuregcm.auth.AuthenticatedAccount;
import org.whispersystems.textsecuregcm.auth.AuthenticationCredentials;
import org.whispersystems.textsecuregcm.auth.DisabledPermittedAuthenticatedAccount;
import org.whispersystems.textsecuregcm.auth.ExternalServiceCredentialGenerator;
import org.whispersystems.textsecuregcm.auth.StoredRegistrationLock;
import org.whispersystems.textsecuregcm.auth.StoredVerificationCode;
import org.whispersystems.textsecuregcm.auth.TurnTokenGenerator;
import org.whispersystems.textsecuregcm.configuration.dynamic.DynamicConfiguration;
import org.whispersystems.textsecuregcm.configuration.dynamic.DynamicSignupCaptchaConfiguration;
import org.whispersystems.textsecuregcm.controllers.AccountController;
import org.whispersystems.textsecuregcm.controllers.RateLimitExceededException;
import org.whispersystems.textsecuregcm.entities.AccountAttributes;
import org.whispersystems.textsecuregcm.entities.AccountCreationResult;
import org.whispersystems.textsecuregcm.entities.ApnRegistrationId;
import org.whispersystems.textsecuregcm.entities.ChangePhoneNumberRequest;
import org.whispersystems.textsecuregcm.entities.GcmRegistrationId;
import org.whispersystems.textsecuregcm.entities.RegistrationLock;
import org.whispersystems.textsecuregcm.limits.RateLimiter;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.mappers.ImpossibleNikNumberExceptionMapper;
import org.whispersystems.textsecuregcm.mappers.RateLimitExceededExceptionMapper;
import org.whispersystems.textsecuregcm.push.APNSender;
import org.whispersystems.textsecuregcm.push.ApnMessage;
import org.whispersystems.textsecuregcm.push.GCMSender;
import org.whispersystems.textsecuregcm.push.GcmMessage;
import org.whispersystems.textsecuregcm.recaptcha.RecaptchaClient;
import org.whispersystems.textsecuregcm.storage.AbusiveHostRule;
import org.whispersystems.textsecuregcm.storage.AbusiveHostRules;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.DynamicConfigurationManager;
import org.whispersystems.textsecuregcm.storage.StoredVerificationCodeManager;
import org.whispersystems.textsecuregcm.storage.UsernamesManager;
import org.whispersystems.textsecuregcm.tests.util.AccountsHelper;
import org.whispersystems.textsecuregcm.tests.util.AuthHelper;
import org.whispersystems.textsecuregcm.util.Hex;
import org.whispersystems.textsecuregcm.util.SystemMapper;

@ExtendWith(DropwizardExtensionsSupport.class)
class AccountControllerTest {

  private static final String SENDER             = "+000014152222222";
  private static final String SENDER_OLD         = "+000014151111111";
  private static final String SENDER_PIN         = "+000014153333333";
  private static final String SENDER_OVER_PIN    = "+000014154444444";
  private static final String SENDER_OVER_PREFIX = "+000014156666666";
  private static final String SENDER_PREAUTH     = "+000014157777777";
  private static final String SENDER_REG_LOCK    = "+000014158888888";
  private static final String SENDER_HAS_STORAGE = "+000014159999999";
  private static final String SENDER_TRANSFER    = "+000014151111112";

  private static final UUID   SENDER_REG_LOCK_UUID = UUID.randomUUID();
  private static final UUID   SENDER_TRANSFER_UUID = UUID.randomUUID();

  private static final String ABUSIVE_HOST             = "192.168.1.1";
  private static final String RESTRICTED_HOST          = "192.168.1.2";
  private static final String NICE_HOST                = "127.0.0.1";
  private static final String RATE_LIMITED_IP_HOST     = "10.0.0.1";
  private static final String RATE_LIMITED_PREFIX_HOST = "10.0.0.2";
  private static final String RATE_LIMITED_HOST2       = "10.0.0.3";

  private static final String VALID_CAPTCHA_TOKEN   = "valid_token";
  private static final String INVALID_CAPTCHA_TOKEN = "invalid_token";

  private static StoredVerificationCodeManager pendingAccountsManager = mock(StoredVerificationCodeManager.class);
  private static AccountsManager        accountsManager        = mock(AccountsManager.class);
  private static AbusiveHostRules       abusiveHostRules       = mock(AbusiveHostRules.class);
  private static RateLimiters           rateLimiters           = mock(RateLimiters.class);
  private static RateLimiter            rateLimiter            = mock(RateLimiter.class);
  private static RateLimiter            pinLimiter             = mock(RateLimiter.class);
  private static RateLimiter            smsVoiceIpLimiter      = mock(RateLimiter.class);
  private static RateLimiter            smsVoicePrefixLimiter  = mock(RateLimiter.class);
  private static RateLimiter            autoBlockLimiter       = mock(RateLimiter.class);
  private static RateLimiter            usernameSetLimiter     = mock(RateLimiter.class);
  private static TurnTokenGenerator     turnTokenGenerator     = mock(TurnTokenGenerator.class);
  private static Account                senderPinAccount       = mock(Account.class);
  private static Account                senderRegLockAccount   = mock(Account.class);
  private static Account                senderHasStorage       = mock(Account.class);
  private static Account                senderTransfer         = mock(Account.class);
  private static RecaptchaClient        recaptchaClient        = mock(RecaptchaClient.class);
  private static GCMSender              gcmSender              = mock(GCMSender.class);
  private static APNSender              apnSender              = mock(APNSender.class);
  private static UsernamesManager       usernamesManager       = mock(UsernamesManager.class);

  private static DynamicConfigurationManager dynamicConfigurationManager = mock(DynamicConfigurationManager.class);

  private byte[] registration_lock_key = new byte[32];
  private static ExternalServiceCredentialGenerator storageCredentialGenerator = new ExternalServiceCredentialGenerator(new byte[32], new byte[32], false);

  private static final ResourceExtension resources = ResourceExtension.builder()
      .addProvider(AuthHelper.getAuthFilter())
      .addProvider(
          new PolymorphicAuthValueFactoryProvider.Binder<>(
              ImmutableSet.of(AuthenticatedAccount.class,
                  DisabledPermittedAuthenticatedAccount.class)))
      .addProvider(new RateLimitExceededExceptionMapper())
      .addProvider(new ImpossibleNikNumberExceptionMapper())
      .setMapper(SystemMapper.getMapper())
      .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
      .addResource(new AccountController(pendingAccountsManager,
          accountsManager,
          usernamesManager,
          abusiveHostRules,
          rateLimiters,
          dynamicConfigurationManager,
          turnTokenGenerator,
          new HashMap<>(),
          recaptchaClient,
          gcmSender,
          apnSender,
          storageCredentialGenerator))
      .build();


  @BeforeEach
  void setup() throws Exception {
    clearInvocations(AuthHelper.VALID_ACCOUNT, AuthHelper.UNDISCOVERABLE_ACCOUNT);

    new SecureRandom().nextBytes(registration_lock_key);
    AuthenticationCredentials registrationLockCredentials = new AuthenticationCredentials(Hex.toStringCondensed(registration_lock_key));

    AccountsHelper.setupMockUpdate(accountsManager);

    when(rateLimiters.getSmsDestinationLimiter()).thenReturn(rateLimiter);
    when(rateLimiters.getVoiceDestinationLimiter()).thenReturn(rateLimiter);
    when(rateLimiters.getVoiceDestinationDailyLimiter()).thenReturn(rateLimiter);
    when(rateLimiters.getVerifyLimiter()).thenReturn(rateLimiter);
    when(rateLimiters.getPinLimiter()).thenReturn(pinLimiter);
    when(rateLimiters.getSmsVoiceIpLimiter()).thenReturn(smsVoiceIpLimiter);
    when(rateLimiters.getSmsVoicePrefixLimiter()).thenReturn(smsVoicePrefixLimiter);
    when(rateLimiters.getAutoBlockLimiter()).thenReturn(autoBlockLimiter);
    when(rateLimiters.getUsernameSetLimiter()).thenReturn(usernameSetLimiter);

    when(senderPinAccount.getLastSeen()).thenReturn(System.currentTimeMillis());
    when(senderPinAccount.getRegistrationLock()).thenReturn(new StoredRegistrationLock(Optional.empty(), Optional.empty(), System.currentTimeMillis()));

    when(senderHasStorage.getUuid()).thenReturn(UUID.randomUUID());
    when(senderHasStorage.isStorageSupported()).thenReturn(true);
    when(senderHasStorage.getRegistrationLock()).thenReturn(new StoredRegistrationLock(Optional.empty(), Optional.empty(), System.currentTimeMillis()));

    when(senderRegLockAccount.getRegistrationLock()).thenReturn(new StoredRegistrationLock(Optional.of(registrationLockCredentials.getHashedAuthenticationToken()), Optional.of(registrationLockCredentials.getSalt()), System.currentTimeMillis()));
    when(senderRegLockAccount.getLastSeen()).thenReturn(System.currentTimeMillis());
    when(senderRegLockAccount.getUuid()).thenReturn(SENDER_REG_LOCK_UUID);
    when(senderRegLockAccount.getNumber()).thenReturn(SENDER_REG_LOCK);

    when(senderTransfer.getRegistrationLock()).thenReturn(new StoredRegistrationLock(Optional.empty(), Optional.empty(), System.currentTimeMillis()));
    when(senderTransfer.getUuid()).thenReturn(SENDER_TRANSFER_UUID);
    when(senderTransfer.getNumber()).thenReturn(SENDER_TRANSFER);

    when(pendingAccountsManager.getCodeForNumber(SENDER)).thenReturn(Optional.of(new StoredVerificationCode("1234", System.currentTimeMillis(), "1234-push")));
    when(pendingAccountsManager.getCodeForNumber(SENDER_OLD)).thenReturn(Optional.empty());
    when(pendingAccountsManager.getCodeForNumber(SENDER_PIN)).thenReturn(Optional.of(new StoredVerificationCode("333333", System.currentTimeMillis(), null)));
    when(pendingAccountsManager.getCodeForNumber(SENDER_REG_LOCK)).thenReturn(Optional.of(new StoredVerificationCode("666666", System.currentTimeMillis(), null)));
    when(pendingAccountsManager.getCodeForNumber(SENDER_OVER_PIN)).thenReturn(Optional.of(new StoredVerificationCode("444444", System.currentTimeMillis(), null)));
    when(pendingAccountsManager.getCodeForNumber(SENDER_OVER_PREFIX)).thenReturn(Optional.of(new StoredVerificationCode("777777", System.currentTimeMillis(), "1234-push")));
    when(pendingAccountsManager.getCodeForNumber(SENDER_PREAUTH)).thenReturn(Optional.of(new StoredVerificationCode("555555", System.currentTimeMillis(), "validchallenge")));
    when(pendingAccountsManager.getCodeForNumber(SENDER_HAS_STORAGE)).thenReturn(Optional.of(new StoredVerificationCode("666666", System.currentTimeMillis(), null)));
    when(pendingAccountsManager.getCodeForNumber(SENDER_TRANSFER)).thenReturn(Optional.of(new StoredVerificationCode("1234", System.currentTimeMillis(), null)));

    when(accountsManager.get(eq(SENDER_PIN))).thenReturn(Optional.of(senderPinAccount));
    when(accountsManager.get(eq(SENDER_REG_LOCK))).thenReturn(Optional.of(senderRegLockAccount));
    when(accountsManager.get(eq(SENDER_OVER_PIN))).thenReturn(Optional.of(senderPinAccount));
    when(accountsManager.get(eq(SENDER))).thenReturn(Optional.empty());
    when(accountsManager.get(eq(SENDER_OLD))).thenReturn(Optional.empty());
    when(accountsManager.get(eq(SENDER_PREAUTH))).thenReturn(Optional.empty());
    when(accountsManager.get(eq(SENDER_HAS_STORAGE))).thenReturn(Optional.of(senderHasStorage));
    when(accountsManager.get(eq(SENDER_TRANSFER))).thenReturn(Optional.of(senderTransfer));

    when(accountsManager.create(any(), any(), any(), any())).thenAnswer((Answer<Account>) invocation -> {
      final Account account = mock(Account.class);
      when(account.getUuid()).thenReturn(UUID.randomUUID());
      when(account.getNumber()).thenReturn(invocation.getArgument(0, String.class));

      return account;
    });

    when(usernamesManager.put(eq(AuthHelper.VALID_UUID), eq("n00bkiller"))).thenReturn(true);
    when(usernamesManager.put(eq(AuthHelper.VALID_UUID), eq("takenusername"))).thenReturn(false);

    {
      DynamicConfiguration dynamicConfiguration = mock(DynamicConfiguration.class);
      when(dynamicConfigurationManager.getConfiguration())
          .thenReturn(dynamicConfiguration);

      DynamicSignupCaptchaConfiguration signupCaptchaConfig = new DynamicSignupCaptchaConfiguration();

      when(dynamicConfiguration.getSignupCaptchaConfiguration()).thenReturn(signupCaptchaConfig);
    }
    when(abusiveHostRules.getAbusiveHostRulesFor(eq(ABUSIVE_HOST))).thenReturn(Collections.singletonList(new AbusiveHostRule(ABUSIVE_HOST, true, Collections.emptyList())));
    when(abusiveHostRules.getAbusiveHostRulesFor(eq(RESTRICTED_HOST))).thenReturn(Collections.singletonList(new AbusiveHostRule(RESTRICTED_HOST, false, Collections.singletonList("+123"))));
    when(abusiveHostRules.getAbusiveHostRulesFor(eq(NICE_HOST))).thenReturn(Collections.emptyList());

    when(recaptchaClient.verify(eq(INVALID_CAPTCHA_TOKEN), anyString())).thenReturn(false);
    when(recaptchaClient.verify(eq(VALID_CAPTCHA_TOKEN), anyString())).thenReturn(true);

    doThrow(new RateLimitExceededException(SENDER_OVER_PIN, Duration.ZERO)).when(pinLimiter).validate(eq(SENDER_OVER_PIN));

    doThrow(new RateLimitExceededException(RATE_LIMITED_PREFIX_HOST, Duration.ZERO)).when(autoBlockLimiter).validate(eq(RATE_LIMITED_PREFIX_HOST));
    doThrow(new RateLimitExceededException(RATE_LIMITED_IP_HOST, Duration.ZERO)).when(autoBlockLimiter).validate(eq(RATE_LIMITED_IP_HOST));

    doThrow(new RateLimitExceededException(SENDER_OVER_PREFIX, Duration.ZERO)).when(smsVoicePrefixLimiter).validate(SENDER_OVER_PREFIX.substring(0, 4+2));
    doThrow(new RateLimitExceededException(RATE_LIMITED_IP_HOST, Duration.ZERO)).when(smsVoiceIpLimiter).validate(RATE_LIMITED_IP_HOST);
    doThrow(new RateLimitExceededException(RATE_LIMITED_HOST2, Duration.ZERO)).when(smsVoiceIpLimiter).validate(RATE_LIMITED_HOST2);
  }

  @AfterEach
  void teardown() {
    reset(
        pendingAccountsManager,
        accountsManager,
        abusiveHostRules,
        rateLimiters,
        rateLimiter,
        pinLimiter,
        smsVoiceIpLimiter,
        smsVoicePrefixLimiter,
        autoBlockLimiter,
        usernameSetLimiter,
        turnTokenGenerator,
        senderPinAccount,
        senderRegLockAccount,
        senderHasStorage,
        senderTransfer,
        recaptchaClient,
        gcmSender,
        apnSender,
        usernamesManager);

    clearInvocations(AuthHelper.DISABLED_DEVICE);
  }

  @Test
  void testGetFcmPreauth() throws Exception {
    Response response = resources.getJerseyTest()
                                 .target("/v1/accounts/fcm/preauth/mytoken/+000014152222222")
                                 .request()
                                 .get();

    assertThat(response.getStatus()).isEqualTo(200);

    ArgumentCaptor<GcmMessage> captor = ArgumentCaptor.forClass(GcmMessage.class);

    verify(gcmSender, times(1)).sendMessage(captor.capture());
    assertThat(captor.getValue().getGcmId()).isEqualTo("mytoken");
    assertThat(captor.getValue().getData().isPresent()).isTrue();
    assertThat(captor.getValue().getData().get().length()).isEqualTo(32);

    verifyNoMoreInteractions(apnSender);
  }

  @Test
  void testGetFcmPreauthIvoryCoast() throws Exception {
    Response response = resources.getJerseyTest()
            .target("/v1/accounts/fcm/preauth/mytoken/+000022507073123")
            .request()
            .get();

    assertThat(response.getStatus()).isEqualTo(200);

    ArgumentCaptor<GcmMessage> captor = ArgumentCaptor.forClass(GcmMessage.class);

    verify(gcmSender, times(1)).sendMessage(captor.capture());
    assertThat(captor.getValue().getGcmId()).isEqualTo("mytoken");
    assertThat(captor.getValue().getData().isPresent()).isTrue();
    assertThat(captor.getValue().getData().get().length()).isEqualTo(32);

    verifyNoMoreInteractions(apnSender);
  }

  @Test
  void testGetApnPreauth() throws Exception {
    Response response = resources.getJerseyTest()
                                 .target("/v1/accounts/apn/preauth/mytoken/+000014152222222")
                                 .request()
                                 .get();

    assertThat(response.getStatus()).isEqualTo(200);

    ArgumentCaptor<ApnMessage> captor = ArgumentCaptor.forClass(ApnMessage.class);

    verify(apnSender, times(1)).sendMessage(captor.capture());
    assertThat(captor.getValue().getApnId()).isEqualTo("mytoken");
    assertThat(captor.getValue().getChallengeData().isPresent()).isTrue();
    assertThat(captor.getValue().getChallengeData().get().length()).isEqualTo(32);
    assertThat(captor.getValue().getMessage()).contains("\"challenge\" : \"" + captor.getValue().getChallengeData().get() + "\"");
    assertThat(captor.getValue().isVoip()).isTrue();

    verifyNoMoreInteractions(gcmSender);
  }

  @Test
  void testGetApnPreauthExplicitVoip() throws Exception {
    Response response = resources.getJerseyTest()
        .target("/v1/accounts/apn/preauth/mytoken/+000014152222222")
        .queryParam("voip", "true")
        .request()
        .get();

    assertThat(response.getStatus()).isEqualTo(200);

    ArgumentCaptor<ApnMessage> captor = ArgumentCaptor.forClass(ApnMessage.class);

    verify(apnSender, times(1)).sendMessage(captor.capture());
    assertThat(captor.getValue().getApnId()).isEqualTo("mytoken");
    assertThat(captor.getValue().getChallengeData().isPresent()).isTrue();
    assertThat(captor.getValue().getChallengeData().get().length()).isEqualTo(32);
    assertThat(captor.getValue().getMessage()).contains("\"challenge\" : \"" + captor.getValue().getChallengeData().get() + "\"");
    assertThat(captor.getValue().isVoip()).isTrue();

    verifyNoMoreInteractions(gcmSender);
  }

  @Test
  void testGetApnPreauthExplicitNoVoip() throws Exception {
    Response response = resources.getJerseyTest()
        .target("/v1/accounts/apn/preauth/mytoken/+000014152222222")
        .queryParam("voip", "false")
        .request()
        .get();

    assertThat(response.getStatus()).isEqualTo(200);

    ArgumentCaptor<ApnMessage> captor = ArgumentCaptor.forClass(ApnMessage.class);

    verify(apnSender, times(1)).sendMessage(captor.capture());
    assertThat(captor.getValue().getApnId()).isEqualTo("mytoken");
    assertThat(captor.getValue().getChallengeData().isPresent()).isTrue();
    assertThat(captor.getValue().getChallengeData().get().length()).isEqualTo(32);
    assertThat(captor.getValue().getMessage()).contains("\"challenge\" : \"" + captor.getValue().getChallengeData().get() + "\"");
    assertThat(captor.getValue().isVoip()).isFalse();

    verifyNoMoreInteractions(gcmSender);
  }

  @Test
  void testGetPreauthImpossibleNumber() {
    final Response response = resources.getJerseyTest()
        .target("/v1/accounts/fcm/preauth/mytoken/BogusNumber")
        .request()
        .get();

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.readEntity(String.class)).isBlank();

    verifyNoMoreInteractions(gcmSender);
    verifyNoMoreInteractions(apnSender);
  }

  @Test
  void testVerifyCodeBadCredentials() {
    final Response response = resources.getJerseyTest()
        .target(String.format("/v1/accounts/code/%s", "1234"))
        .request()
        .header("Authorization", "This is not a valid authorization header")
        .put(Entity.entity(new AccountAttributes(), MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void testChangePhoneNumber() throws InterruptedException {
    final String number = "+000018005559876";
    final String code = "987654";

    when(pendingAccountsManager.getCodeForNumber(number)).thenReturn(Optional.of(
        new StoredVerificationCode(code, System.currentTimeMillis(), "push")));

    final Response response =
        resources.getJerseyTest()
            .target("/v1/accounts/number")
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
            .put(Entity.entity(new ChangePhoneNumberRequest(number, code, null),
                MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(204);
    verify(accountsManager).changeNumber(AuthHelper.VALID_ACCOUNT, number);
  }

  @Test
  void testChangePhoneNumberImpossibleNumber() throws InterruptedException {
    final String number = "This is not a real phone number";
    final String code = "987654";

    final Response response =
        resources.getJerseyTest()
            .target("/v1/accounts/number")
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
            .put(Entity.entity(new ChangePhoneNumberRequest(number, code, null),
                MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.readEntity(String.class)).isBlank();
    verify(accountsManager, never()).changeNumber(any(), any());
  }

  @Test
  void testChangePhoneNumberSameNumber() throws InterruptedException {
    final Response response =
        resources.getJerseyTest()
            .target("/v1/accounts/number")
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
            .put(Entity.entity(new ChangePhoneNumberRequest(AuthHelper.VALID_NUMBER, "567890", null),
                MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(204);
    verify(accountsManager, never()).changeNumber(eq(AuthHelper.VALID_ACCOUNT), any());
  }

  @Test
  void testChangePhoneNumberExistingAccountReglockNotRequired() throws InterruptedException {
    final String number = "+000018005559876";
    final String code = "987654";

    when(pendingAccountsManager.getCodeForNumber(number)).thenReturn(Optional.of(
        new StoredVerificationCode(code, System.currentTimeMillis(), "push")));

    final StoredRegistrationLock existingRegistrationLock = mock(StoredRegistrationLock.class);
    when(existingRegistrationLock.requiresClientRegistrationLock()).thenReturn(false);

    final Account existingAccount = mock(Account.class);
    when(existingAccount.getNumber()).thenReturn(number);
    when(existingAccount.getUuid()).thenReturn(UUID.randomUUID());
    when(existingAccount.getRegistrationLock()).thenReturn(existingRegistrationLock);

    when(accountsManager.get(number)).thenReturn(Optional.of(existingAccount));

    final Response response =
        resources.getJerseyTest()
            .target("/v1/accounts/number")
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
            .put(Entity.entity(new ChangePhoneNumberRequest(number, code, null),
                MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(204);
    verify(accountsManager).changeNumber(eq(AuthHelper.VALID_ACCOUNT), any());
  }

  @Test
  void testChangePhoneNumberExistingAccountReglockRequiredNotProvided() throws InterruptedException {
    final String number = "+000018005559876";
    final String code = "987654";

    when(pendingAccountsManager.getCodeForNumber(number)).thenReturn(Optional.of(
        new StoredVerificationCode(code, System.currentTimeMillis(), "push")));

    final StoredRegistrationLock existingRegistrationLock = mock(StoredRegistrationLock.class);
    when(existingRegistrationLock.requiresClientRegistrationLock()).thenReturn(true);

    final Account existingAccount = mock(Account.class);
    when(existingAccount.getNumber()).thenReturn(number);
    when(existingAccount.getUuid()).thenReturn(UUID.randomUUID());
    when(existingAccount.getRegistrationLock()).thenReturn(existingRegistrationLock);

    when(accountsManager.get(number)).thenReturn(Optional.of(existingAccount));

    final Response response =
        resources.getJerseyTest()
            .target("/v1/accounts/number")
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
            .put(Entity.entity(new ChangePhoneNumberRequest(number, code, null),
                MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(423);
    verify(accountsManager, never()).changeNumber(eq(AuthHelper.VALID_ACCOUNT), any());
  }

  @Test
  void testChangePhoneNumberExistingAccountReglockRequiredIncorrect() throws InterruptedException {
    final String number = "+000018005559876";
    final String code = "987654";
    final String reglock = "setec-astronomy";

    when(pendingAccountsManager.getCodeForNumber(number)).thenReturn(Optional.of(
        new StoredVerificationCode(code, System.currentTimeMillis(), "push")));

    final StoredRegistrationLock existingRegistrationLock = mock(StoredRegistrationLock.class);
    when(existingRegistrationLock.requiresClientRegistrationLock()).thenReturn(true);
    when(existingRegistrationLock.verify(anyString())).thenReturn(false);

    final Account existingAccount = mock(Account.class);
    when(existingAccount.getNumber()).thenReturn(number);
    when(existingAccount.getUuid()).thenReturn(UUID.randomUUID());
    when(existingAccount.getRegistrationLock()).thenReturn(existingRegistrationLock);

    when(accountsManager.get(number)).thenReturn(Optional.of(existingAccount));

    final Response response =
        resources.getJerseyTest()
            .target("/v1/accounts/number")
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
            .put(Entity.entity(new ChangePhoneNumberRequest(number, code, reglock),
                MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(423);
    verify(accountsManager, never()).changeNumber(eq(AuthHelper.VALID_ACCOUNT), any());
  }

  @Test
  void testChangePhoneNumberExistingAccountReglockRequiredCorrect() throws InterruptedException {
    final String number = "+000018005559876";
    final String code = "987654";
    final String reglock = "setec-astronomy";

    when(pendingAccountsManager.getCodeForNumber(number)).thenReturn(Optional.of(
        new StoredVerificationCode(code, System.currentTimeMillis(), "push")));

    final StoredRegistrationLock existingRegistrationLock = mock(StoredRegistrationLock.class);
    when(existingRegistrationLock.requiresClientRegistrationLock()).thenReturn(true);
    when(existingRegistrationLock.verify(reglock)).thenReturn(true);

    final Account existingAccount = mock(Account.class);
    when(existingAccount.getNumber()).thenReturn(number);
    when(existingAccount.getUuid()).thenReturn(UUID.randomUUID());
    when(existingAccount.getRegistrationLock()).thenReturn(existingRegistrationLock);

    when(accountsManager.get(number)).thenReturn(Optional.of(existingAccount));

    final Response response =
        resources.getJerseyTest()
            .target("/v1/accounts/number")
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
            .put(Entity.entity(new ChangePhoneNumberRequest(number, code, reglock),
                MediaType.APPLICATION_JSON_TYPE));

    assertThat(response.getStatus()).isEqualTo(204);
    verify(accountsManager).changeNumber(eq(AuthHelper.VALID_ACCOUNT), any());
  }

  @Test
  void testSetRegistrationLock() {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/registration_lock/")
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
                 .put(Entity.json(new RegistrationLock("1234567890123456789012345678901234567890123456789012345678901234")));

    assertThat(response.getStatus()).isEqualTo(204);

    ArgumentCaptor<String> pinCapture     = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> pinSaltCapture = ArgumentCaptor.forClass(String.class);

    verify(AuthHelper.VALID_ACCOUNT, times(1)).setRegistrationLock(pinCapture.capture(), pinSaltCapture.capture());

    assertThat(pinCapture.getValue()).isNotEmpty();
    assertThat(pinSaltCapture.getValue()).isNotEmpty();

    assertThat(pinCapture.getValue().length()).isEqualTo(40);
  }

  @Test
  void testSetShortRegistrationLock() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/registration_lock/")
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
                 .put(Entity.json(new RegistrationLock("313")));

    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  void testSetRegistrationLockDisabled() throws Exception {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/registration_lock/")
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.DISABLED_UUID, AuthHelper.DISABLED_PASSWORD))
                 .put(Entity.json(new RegistrationLock("1234567890123456789012345678901234567890123456789012345678901234")));

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void testSetGcmId() {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/gcm/")
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.DISABLED_UUID, AuthHelper.DISABLED_PASSWORD))
                 .put(Entity.json(new GcmRegistrationId("z000")));

    assertThat(response.getStatus()).isEqualTo(204);

    verify(AuthHelper.DISABLED_DEVICE, times(1)).setGcmId(eq("z000"));
    verify(accountsManager, times(1)).updateDevice(eq(AuthHelper.DISABLED_ACCOUNT), anyLong(), any());
  }

  @Test
  void testSetApnId() {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/apn/")
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.DISABLED_UUID, AuthHelper.DISABLED_PASSWORD))
                 .put(Entity.json(new ApnRegistrationId("first", "second")));

    assertThat(response.getStatus()).isEqualTo(204);

    verify(AuthHelper.DISABLED_DEVICE, times(1)).setApnId(eq("first"));
    verify(AuthHelper.DISABLED_DEVICE, times(1)).setVoipApnId(eq("second"));
    verify(accountsManager, times(1)).updateDevice(eq(AuthHelper.DISABLED_ACCOUNT), anyLong(), any());
  }

  @Test
  void testSetApnIdNoVoip() {
    Response response =
        resources.getJerseyTest()
            .target("/v1/accounts/apn/")
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.DISABLED_UUID, AuthHelper.DISABLED_PASSWORD))
            .put(Entity.json(new ApnRegistrationId("first", null)));

    assertThat(response.getStatus()).isEqualTo(204);

    verify(AuthHelper.DISABLED_DEVICE, times(1)).setApnId(eq("first"));
    verify(AuthHelper.DISABLED_DEVICE, times(1)).setVoipApnId(null);
    verify(accountsManager, times(1)).updateDevice(eq(AuthHelper.DISABLED_ACCOUNT), anyLong(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v1/accounts/whoami/", "/v1/accounts/me/"})
  public void testWhoAmI(final String path) {
    AccountCreationResult response =
        resources.getJerseyTest()
                 .target(path)
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
                 .get(AccountCreationResult.class);

    assertThat(response.getUuid()).isEqualTo(AuthHelper.VALID_UUID);
  }

  @Test
  void testSetUsername() {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/username/n00bkiller")
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
                 .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void testSetTakenUsername() {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/username/takenusername")
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
                 .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(409);
  }

  @Test
  void testSetInvalidUsername() {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/username/p\u0430ypal")
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
                 .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  void testSetInvalidPrefixUsername() {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/username/0n00bkiller")
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
                 .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  void testSetUsernameBadAuth() {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/username/n00bkiller")
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.INVALID_PASSWORD))
                 .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void testDeleteUsername() {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/username/")
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
                 .delete();

    assertThat(response.getStatus()).isEqualTo(204);
    verify(usernamesManager, times(1)).delete(eq(AuthHelper.VALID_UUID));
  }

  @Test
  void testDeleteUsernameBadAuth() {
    Response response =
        resources.getJerseyTest()
                 .target("/v1/accounts/username/")
                 .request()
                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.INVALID_PASSWORD))
                 .delete();

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void testSetAccountAttributesNoDiscoverabilityChange() {
    Response response =
            resources.getJerseyTest()
                    .target("/v1/accounts/attributes/")
                    .request()
                    .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
                    .put(Entity.json(new AccountAttributes(false, 2222, null, null, true, null)));

    assertThat(response.getStatus()).isEqualTo(204);
  }

  @Test
  void testSetAccountAttributesEnableDiscovery() {
    Response response =
            resources.getJerseyTest()
                    .target("/v1/accounts/attributes/")
                    .request()
                    .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.UNDISCOVERABLE_UUID, AuthHelper.UNDISCOVERABLE_PASSWORD))
                    .put(Entity.json(new AccountAttributes(false, 2222, null, null, true, null)));

    assertThat(response.getStatus()).isEqualTo(204);
  }

  @Test
  void testSetAccountAttributesDisableDiscovery() {
    Response response =
            resources.getJerseyTest()
                    .target("/v1/accounts/attributes/")
                    .request()
                    .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
                    .put(Entity.json(new AccountAttributes(false, 2222, null, null, false, null)));

    assertThat(response.getStatus()).isEqualTo(204);
  }

  @Test
  void testDeleteAccount() throws InterruptedException {
    Response response =
            resources.getJerseyTest()
                     .target("/v1/accounts/me")
                     .request()
                     .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
                     .delete();

    assertThat(response.getStatus()).isEqualTo(204);
    verify(accountsManager).delete(AuthHelper.VALID_ACCOUNT, AccountsManager.DeletionReason.USER_REQUEST);
  }

  @Test
  void testDeleteAccountInterrupted() throws InterruptedException {
    doThrow(InterruptedException.class).when(accountsManager).delete(any(), any());

    Response response =
        resources.getJerseyTest()
            .target("/v1/accounts/me")
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
            .delete();

    assertThat(response.getStatus()).isEqualTo(500);
    verify(accountsManager).delete(AuthHelper.VALID_ACCOUNT, AccountsManager.DeletionReason.USER_REQUEST);
  }
}
