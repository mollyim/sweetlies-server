/*
 * Copyright 2013-2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.whispersystems.textsecuregcm;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.dropwizard.client.JerseyClientConfiguration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.whispersystems.textsecuregcm.configuration.AccountDatabaseCrawlerConfiguration;
import org.whispersystems.textsecuregcm.configuration.AccountsDatabaseConfiguration;
import org.whispersystems.textsecuregcm.configuration.AccountsDynamoDbConfiguration;
import org.whispersystems.textsecuregcm.configuration.ApnConfiguration;
import org.whispersystems.textsecuregcm.configuration.AppConfigConfiguration;
import org.whispersystems.textsecuregcm.configuration.AwsAttachmentsConfiguration;
import org.whispersystems.textsecuregcm.configuration.CdnConfiguration;
import org.whispersystems.textsecuregcm.configuration.DatabaseConfiguration;
import org.whispersystems.textsecuregcm.configuration.DatadogConfiguration;
import org.whispersystems.textsecuregcm.configuration.DynamoDbClientConfiguration;
import org.whispersystems.textsecuregcm.configuration.DynamoDbConfiguration;
import org.whispersystems.textsecuregcm.configuration.GcmConfiguration;
import org.whispersystems.textsecuregcm.configuration.GcpAttachmentsConfiguration;
import org.whispersystems.textsecuregcm.configuration.MaxDeviceConfiguration;
import org.whispersystems.textsecuregcm.configuration.MessageCacheConfiguration;
import org.whispersystems.textsecuregcm.configuration.MessageDynamoDbConfiguration;
import org.whispersystems.textsecuregcm.configuration.MonitoredS3ObjectConfiguration;
import org.whispersystems.textsecuregcm.configuration.PaymentsServiceConfiguration;
import org.whispersystems.textsecuregcm.configuration.PushConfiguration;
import org.whispersystems.textsecuregcm.configuration.RateLimitsConfiguration;
import org.whispersystems.textsecuregcm.configuration.RecaptchaV2Configuration;
import org.whispersystems.textsecuregcm.configuration.RedisClusterConfiguration;
import org.whispersystems.textsecuregcm.configuration.RedisConfiguration;
import org.whispersystems.textsecuregcm.configuration.RemoteConfigConfiguration;
import org.whispersystems.textsecuregcm.configuration.ReportMessageConfiguration;
import org.whispersystems.textsecuregcm.configuration.SecureBackupServiceConfiguration;
import org.whispersystems.textsecuregcm.configuration.SecureStorageServiceConfiguration;
import org.whispersystems.textsecuregcm.configuration.TestDeviceConfiguration;
import org.whispersystems.textsecuregcm.configuration.TurnConfiguration;
import org.whispersystems.textsecuregcm.configuration.TwilioConfiguration;
import org.whispersystems.textsecuregcm.configuration.UnidentifiedDeliveryConfiguration;
import org.whispersystems.textsecuregcm.configuration.VoiceVerificationConfiguration;
import org.whispersystems.textsecuregcm.configuration.ZkConfig;
import org.whispersystems.websocket.configuration.WebSocketConfiguration;

/** @noinspection MismatchedQueryAndUpdateOfCollection, WeakerAccess */
public class WhisperServerConfiguration extends Configuration {

  @NotNull
  @Valid
  @JsonProperty
  private DynamoDbClientConfiguration dynamoDbClientConfiguration;

  @NotNull
  @Valid
  @JsonProperty
  private TwilioConfiguration twilio;

  @NotNull
  @Valid
  @JsonProperty
  private PushConfiguration push;

  @NotNull
  @Valid
  @JsonProperty
  private AwsAttachmentsConfiguration awsAttachments;

  @NotNull
  @Valid
  @JsonProperty
  private GcpAttachmentsConfiguration gcpAttachments;

  @NotNull
  @Valid
  @JsonProperty
  private CdnConfiguration cdn;

  @NotNull
  @Valid
  @JsonProperty
  private DatadogConfiguration datadog;

  @NotNull
  @Valid
  @JsonProperty
  private RedisClusterConfiguration cacheCluster;

  @NotNull
  @Valid
  @JsonProperty
  private RedisConfiguration pubsub;

  @NotNull
  @Valid
  @JsonProperty
  private RedisClusterConfiguration metricsCluster;

  @NotNull
  @Valid
  @JsonProperty
  private AccountDatabaseCrawlerConfiguration accountDatabaseCrawler;

  @NotNull
  @Valid
  @JsonProperty
  private RedisClusterConfiguration pushSchedulerCluster;

  @NotNull
  @Valid
  @JsonProperty
  private RedisClusterConfiguration rateLimitersCluster;

  @NotNull
  @Valid
  @JsonProperty
  private MessageCacheConfiguration messageCache;

  @NotNull
  @Valid
  @JsonProperty
  private RedisClusterConfiguration clientPresenceCluster;

  @Valid
  @NotNull
  @JsonProperty
  private MessageDynamoDbConfiguration messageDynamoDb;

  @Valid
  @NotNull
  @JsonProperty
  private DynamoDbConfiguration keysDynamoDb;

  @Valid
  @NotNull
  @JsonProperty
  private AccountsDynamoDbConfiguration accountsDynamoDb;

  @Valid
  @NotNull
  @JsonProperty
  private DynamoDbConfiguration deletedAccountsDynamoDb;

  @Valid
  @NotNull
  @JsonProperty
  private DynamoDbConfiguration deletedAccountsLockDynamoDb;

  @Valid
  @NotNull
  @JsonProperty
  private DynamoDbConfiguration pushChallengeDynamoDb;

  @Valid
  @NotNull
  @JsonProperty
  private DynamoDbConfiguration reportMessageDynamoDb;

  @Valid
  @NotNull
  @JsonProperty
  private DynamoDbConfiguration pendingAccountsDynamoDb;

  @Valid
  @NotNull
  @JsonProperty
  private DynamoDbConfiguration pendingDevicesDynamoDb;

  @Valid
  @NotNull
  @JsonProperty
  private DatabaseConfiguration abuseDatabase;

  @Valid
  @NotNull
  @JsonProperty
  private List<TestDeviceConfiguration> testDevices = new LinkedList<>();

  @Valid
  @NotNull
  @JsonProperty
  private List<MaxDeviceConfiguration> maxDevices = new LinkedList<>();

  @Valid
  @NotNull
  @JsonProperty
  private AccountsDatabaseConfiguration accountsDatabase;

  @Valid
  @NotNull
  @JsonProperty
  private RateLimitsConfiguration limits = new RateLimitsConfiguration();

  @Valid
  @NotNull
  @JsonProperty
  private JerseyClientConfiguration httpClient = new JerseyClientConfiguration();

  @Valid
  @NotNull
  @JsonProperty
  private WebSocketConfiguration webSocket = new WebSocketConfiguration();

  @Valid
  @NotNull
  @JsonProperty
  private TurnConfiguration turn;

  @Valid
  @NotNull
  @JsonProperty
  private GcmConfiguration gcm;

  @Valid
  @NotNull
  @JsonProperty
  private ApnConfiguration apn;

  @Valid
  @NotNull
  @JsonProperty
  private UnidentifiedDeliveryConfiguration unidentifiedDelivery;

  @Valid
  @NotNull
  @JsonProperty
  private VoiceVerificationConfiguration voiceVerification;

  @Valid
  @NotNull
  @JsonProperty
  private RecaptchaV2Configuration recaptchaV2;

  @Valid
  @NotNull
  @JsonProperty
  private SecureStorageServiceConfiguration storageService;

  @Valid
  @NotNull
  @JsonProperty
  private SecureBackupServiceConfiguration backupService;

  @Valid
  @NotNull
  @JsonProperty
  private PaymentsServiceConfiguration paymentsService;

  @Valid
  @NotNull
  @JsonProperty
  private ZkConfig zkConfig;

  @Valid
  @NotNull
  @JsonProperty
  private RemoteConfigConfiguration remoteConfig;

  @Valid
  @NotNull
  @JsonProperty
  private AppConfigConfiguration appConfig;

  @Valid
  @NotNull
  @JsonProperty
  private MonitoredS3ObjectConfiguration torExitNodeList;

  @Valid
  @NotNull
  @JsonProperty
  private MonitoredS3ObjectConfiguration asnTable;

  @Valid
  @NotNull
  @JsonProperty
  private ReportMessageConfiguration reportMessage = new ReportMessageConfiguration();

  private Map<String, String> transparentDataIndex = new HashMap<>();

  public DynamoDbClientConfiguration getDynamoDbClientConfiguration() {
    return dynamoDbClientConfiguration;
  }

  public RecaptchaV2Configuration getRecaptchaV2Configuration() {
    return recaptchaV2;
  }

  public VoiceVerificationConfiguration getVoiceVerificationConfiguration() {
    return voiceVerification;
  }

  public WebSocketConfiguration getWebSocketConfiguration() {
    return webSocket;
  }

  public TwilioConfiguration getTwilioConfiguration() {
    return twilio;
  }

  public PushConfiguration getPushConfiguration() {
    return push;
  }

  public JerseyClientConfiguration getJerseyClientConfiguration() {
    return httpClient;
  }

  public AwsAttachmentsConfiguration getAwsAttachmentsConfiguration() {
    return awsAttachments;
  }

  public GcpAttachmentsConfiguration getGcpAttachmentsConfiguration() {
    return gcpAttachments;
  }

  public RedisClusterConfiguration getCacheClusterConfiguration() {
    return cacheCluster;
  }

  public RedisConfiguration getPubsubCacheConfiguration() {
    return pubsub;
  }

  public RedisClusterConfiguration getMetricsClusterConfiguration() {
    return metricsCluster;
  }

  public SecureStorageServiceConfiguration getSecureStorageServiceConfiguration() {
    return storageService;
  }

  public AccountDatabaseCrawlerConfiguration getAccountDatabaseCrawlerConfiguration() {
    return accountDatabaseCrawler;
  }

  public MessageCacheConfiguration getMessageCacheConfiguration() {
    return messageCache;
  }

  public RedisClusterConfiguration getClientPresenceClusterConfiguration() {
    return clientPresenceCluster;
  }

  public RedisClusterConfiguration getPushSchedulerCluster() {
    return pushSchedulerCluster;
  }

  public RedisClusterConfiguration getRateLimitersCluster() {
    return rateLimitersCluster;
  }

  public MessageDynamoDbConfiguration getMessageDynamoDbConfiguration() {
    return messageDynamoDb;
  }

  public DynamoDbConfiguration getKeysDynamoDbConfiguration() {
    return keysDynamoDb;
  }

  public AccountsDynamoDbConfiguration getAccountsDynamoDbConfiguration() {
    return accountsDynamoDb;
  }

  public DynamoDbConfiguration getDeletedAccountsDynamoDbConfiguration() {
    return deletedAccountsDynamoDb;
  }

  public DynamoDbConfiguration getDeletedAccountsLockDynamoDbConfiguration() {
    return deletedAccountsLockDynamoDb;
  }

  public DatabaseConfiguration getAbuseDatabaseConfiguration() {
    return abuseDatabase;
  }

  public AccountsDatabaseConfiguration getAccountsDatabaseConfiguration() {
    return accountsDatabase;
  }

  public RateLimitsConfiguration getLimitsConfiguration() {
    return limits;
  }

  public TurnConfiguration getTurnConfiguration() {
    return turn;
  }

  public GcmConfiguration getGcmConfiguration() {
    return gcm;
  }

  public ApnConfiguration getApnConfiguration() {
    return apn;
  }

  public CdnConfiguration getCdnConfiguration() {
    return cdn;
  }

  public DatadogConfiguration getDatadogConfiguration() {
    return datadog;
  }

  public UnidentifiedDeliveryConfiguration getDeliveryCertificate() {
    return unidentifiedDelivery;
  }

  public Map<String, Integer> getTestDevices() {
    Map<String, Integer> results = new HashMap<>();

    for (TestDeviceConfiguration testDeviceConfiguration : testDevices) {
      results.put(testDeviceConfiguration.getNumber(),
                  testDeviceConfiguration.getCode());
    }

    return results;
  }

  public Map<String, Integer> getMaxDevices() {
    Map<String, Integer> results = new HashMap<>();

    for (MaxDeviceConfiguration maxDeviceConfiguration : maxDevices) {
      results.put(maxDeviceConfiguration.getNumber(),
                  maxDeviceConfiguration.getCount());
    }

    return results;
  }

  public Map<String, String> getTransparentDataIndex() {
    return transparentDataIndex;
  }

  public SecureBackupServiceConfiguration getSecureBackupServiceConfiguration() {
    return backupService;
  }

  public PaymentsServiceConfiguration getPaymentsServiceConfiguration() {
    return paymentsService;
  }

  public ZkConfig getZkConfig() {
    return zkConfig;
  }

  public RemoteConfigConfiguration getRemoteConfigConfiguration() {
    return remoteConfig;
  }

  public AppConfigConfiguration getAppConfig() {
    return appConfig;
  }

  public DynamoDbConfiguration getPushChallengeDynamoDbConfiguration() {
    return pushChallengeDynamoDb;
  }

  public DynamoDbConfiguration getReportMessageDynamoDbConfiguration() {
    return reportMessageDynamoDb;
  }

  public DynamoDbConfiguration getPendingAccountsDynamoDbConfiguration() {
    return pendingAccountsDynamoDb;
  }

  public DynamoDbConfiguration getPendingDevicesDynamoDbConfiguration() {
    return pendingDevicesDynamoDb;
  }

  public MonitoredS3ObjectConfiguration getTorExitNodeListConfiguration() {
    return torExitNodeList;
  }

  public MonitoredS3ObjectConfiguration getAsnTableConfiguration() {
    return asnTable;
  }

  public ReportMessageConfiguration getReportMessageConfiguration() {
    return reportMessage;
  }
}
