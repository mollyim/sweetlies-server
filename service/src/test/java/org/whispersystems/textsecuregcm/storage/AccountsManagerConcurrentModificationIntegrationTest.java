/*
 * Copyright 2013-2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.storage;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.whispersystems.textsecuregcm.auth.AuthenticationCredentials;
import org.whispersystems.textsecuregcm.entities.AccountAttributes;
import org.whispersystems.textsecuregcm.entities.SignedPreKey;
import org.whispersystems.textsecuregcm.push.ClientPresenceManager;
import org.whispersystems.textsecuregcm.securebackup.SecureBackupClient;
import org.whispersystems.textsecuregcm.securestorage.SecureStorageClient;
import org.whispersystems.textsecuregcm.tests.util.JsonHelpers;
import org.whispersystems.textsecuregcm.tests.util.RedisClusterHelper;
import org.whispersystems.textsecuregcm.util.Pair;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

class AccountsManagerConcurrentModificationIntegrationTest {

  private static final String ACCOUNTS_TABLE_NAME = "accounts_test";
  private static final String NUMBERS_TABLE_NAME = "numbers_test";

  private static final int SCAN_PAGE_SIZE = 1;

  @RegisterExtension
  static DynamoDbExtension dynamoDbExtension = DynamoDbExtension.builder()
      .tableName(ACCOUNTS_TABLE_NAME)
      .hashKey(Accounts.KEY_ACCOUNT_UUID)
      .attributeDefinition(AttributeDefinition.builder()
          .attributeName(Accounts.KEY_ACCOUNT_UUID)
          .attributeType(ScalarAttributeType.B)
          .build())
      .build();

  private Accounts accounts;

  private AccountsManager accountsManager;

  private RedisAdvancedClusterCommands<String, String> commands;

  private Executor mutationExecutor = new ThreadPoolExecutor(20, 20, 5, TimeUnit.SECONDS, new LinkedBlockingDeque<>(20));

  @BeforeEach
  void setup() throws InterruptedException {

    {
      CreateTableRequest createNumbersTableRequest = CreateTableRequest.builder()
          .tableName(NUMBERS_TABLE_NAME)
          .keySchema(KeySchemaElement.builder()
              .attributeName(Accounts.ATTR_ACCOUNT_E164)
              .keyType(KeyType.HASH)
              .build())
          .attributeDefinitions(AttributeDefinition.builder()
              .attributeName(Accounts.ATTR_ACCOUNT_E164)
              .attributeType(ScalarAttributeType.S)
              .build())
          .provisionedThroughput(DynamoDbExtension.DEFAULT_PROVISIONED_THROUGHPUT)
          .build();

      dynamoDbExtension.getDynamoDbClient().createTable(createNumbersTableRequest);
    }

    accounts = new Accounts(
        dynamoDbExtension.getDynamoDbClient(),
        dynamoDbExtension.getTableName(),
        NUMBERS_TABLE_NAME,
        SCAN_PAGE_SIZE);

    {
      //noinspection unchecked
      commands = mock(RedisAdvancedClusterCommands.class);

      final DeletedAccountsManager deletedAccountsManager = mock(DeletedAccountsManager.class);

      doAnswer(invocation -> {
        //noinspection unchecked
        invocation.getArgument(1, Consumer.class).accept(Optional.empty());
        return null;
      }).when(deletedAccountsManager).lockAndTake(anyString(), any());

      accountsManager = new AccountsManager(
          accounts,
          RedisClusterHelper.buildMockRedisCluster(commands),
          deletedAccountsManager,
          mock(KeysDynamoDb.class),
          mock(MessagesManager.class),
          mock(UsernamesManager.class),
          mock(ProfilesManager.class),
          mock(StoredVerificationCodeManager.class),
          mock(SecureStorageClient.class),
          mock(SecureBackupClient.class),
          mock(ClientPresenceManager.class)
      );
    }
  }

  @Test
  void testConcurrentUpdate() throws IOException, InterruptedException {

    final UUID uuid;
    {
      final Account account = accountsManager.update(
          accountsManager.create("+14155551212", "password", null, new AccountAttributes()),
          a -> {
            a.setUnidentifiedAccessKey(new byte[16]);

            final Random random = new Random();
            final SignedPreKey signedPreKey = new SignedPreKey(random.nextInt(), "testPublicKey-" + random.nextInt(),
                "testSignature-" + random.nextInt());

            a.removeDevice(1);
            a.addDevice(new Device(1, "testName-" + random.nextInt(), "testAuthToken-" + random.nextInt(),
                "testSalt-" + random.nextInt(),
                "testGcmId-" + random.nextInt(), "testApnId-" + random.nextInt(), "testVoipApnId-" + random.nextInt(),
                random.nextBoolean(), random.nextInt(), signedPreKey, random.nextInt(), random.nextInt(),
                "testUserAgent-" + random.nextInt(), 0,
                new Device.DeviceCapabilities(random.nextBoolean(), random.nextBoolean(), random.nextBoolean(),
                    random.nextBoolean(), random.nextBoolean(), random.nextBoolean(),
                    random.nextBoolean(), random.nextBoolean(), random.nextBoolean())));
          });

      uuid = account.getUuid();
    }

    final String profileName = "name";
    final String avatar = "avatar";
    final boolean discoverableByPhoneNumber = false;
    final String currentProfileVersion = "cpv";
    final String identityKey = "ikey";
    final byte[] unidentifiedAccessKey = new byte[]{1};
    final String pin = "1234";
    final String registrationLock = "reglock";
    final AuthenticationCredentials credentials = new AuthenticationCredentials(registrationLock);
    final boolean unrestrictedUnidentifiedAccess = true;
    final long lastSeen = Instant.now().getEpochSecond();

    CompletableFuture.allOf(
        modifyAccount(uuid, account -> account.setProfileName(profileName)),
        modifyAccount(uuid, account -> account.setAvatar(avatar)),
        modifyAccount(uuid, account -> account.setDiscoverableByPhoneNumber(discoverableByPhoneNumber)),
        modifyAccount(uuid, account -> account.setCurrentProfileVersion(currentProfileVersion)),
        modifyAccount(uuid, account -> account.setIdentityKey(identityKey)),
        modifyAccount(uuid, account -> account.setUnidentifiedAccessKey(unidentifiedAccessKey)),
        modifyAccount(uuid, account -> account.setRegistrationLock(credentials.getHashedAuthenticationToken(), credentials.getSalt())),
        modifyAccount(uuid, account -> account.setUnrestrictedUnidentifiedAccess(unrestrictedUnidentifiedAccess)),
        modifyDevice(uuid, Device.MASTER_ID, device -> device.setLastSeen(lastSeen)),
        modifyDevice(uuid, Device.MASTER_ID, device -> device.setName("deviceName"))
    ).join();

    final Account managerAccount = accountsManager.get(uuid).orElseThrow();
    final Account dynamoAccount = accounts.get(uuid).orElseThrow();

    final Account redisAccount = getLastAccountFromRedisMock(commands);

    Stream.of(
        new Pair<>("manager", managerAccount),
        new Pair<>("dynamo", dynamoAccount),
        new Pair<>("redis", redisAccount)
    ).forEach(pair ->
        verifyAccount(pair.first(), pair.second(), profileName, avatar, discoverableByPhoneNumber,
            currentProfileVersion, identityKey, unidentifiedAccessKey, pin, registrationLock,
            unrestrictedUnidentifiedAccess, lastSeen));
  }

  private Account getLastAccountFromRedisMock(RedisAdvancedClusterCommands<String, String> commands) throws IOException {
    ArgumentCaptor<String> redisSetArgumentCapture = ArgumentCaptor.forClass(String.class);

    verify(commands, atLeast(20)).set(anyString(), redisSetArgumentCapture.capture());

    return JsonHelpers.fromJson(redisSetArgumentCapture.getValue(), Account.class);
  }

  private void verifyAccount(final String name, final Account account, final String profileName, final String avatar, final boolean discoverableByPhoneNumber, final String currentProfileVersion, final String identityKey, final byte[] unidentifiedAccessKey, final String pin, final String clientRegistrationLock, final boolean unrestrictedUnidentifiedAcces, final long lastSeen) {

    assertAll(name,
        () -> assertEquals(profileName, account.getProfileName()),
        () -> assertEquals(avatar, account.getAvatar()),
        () -> assertEquals(discoverableByPhoneNumber, account.isDiscoverableByPhoneNumber()),
        () -> assertEquals(currentProfileVersion, account.getCurrentProfileVersion().orElseThrow()),
        () -> assertEquals(identityKey, account.getIdentityKey()),
        () -> assertArrayEquals(unidentifiedAccessKey, account.getUnidentifiedAccessKey().orElseThrow()),
        () -> assertTrue(account.getRegistrationLock().verify(clientRegistrationLock)),
        () -> assertEquals(unrestrictedUnidentifiedAcces, account.isUnrestrictedUnidentifiedAccess())
    );
  }

  private CompletableFuture<?> modifyAccount(final UUID uuid, final Consumer<Account> accountMutation) {

    return CompletableFuture.runAsync(() -> {
      final Account account = accountsManager.get(uuid).orElseThrow();
      accountsManager.update(account, accountMutation);
    }, mutationExecutor);
  }

  private CompletableFuture<?> modifyDevice(final UUID uuid, final long deviceId, final Consumer<Device> deviceMutation) {

    return CompletableFuture.runAsync(() -> {
      final Account account = accountsManager.get(uuid).orElseThrow();
      accountsManager.updateDevice(account, deviceId, deviceMutation);
    }, mutationExecutor);
  }
}
