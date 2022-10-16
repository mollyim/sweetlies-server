/*
 * Copyright 2013-2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.tests.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.lettuce.core.RedisException;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.Answer;
import org.whispersystems.textsecuregcm.entities.AccountAttributes;
import org.whispersystems.textsecuregcm.entities.SignedPreKey;
import org.whispersystems.textsecuregcm.push.ClientPresenceManager;
import org.whispersystems.textsecuregcm.securebackup.SecureBackupClient;
import org.whispersystems.textsecuregcm.securestorage.SecureStorageClient;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Accounts;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.ContestedOptimisticLockException;
import org.whispersystems.textsecuregcm.storage.DeletedAccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.Device.DeviceCapabilities;
import org.whispersystems.textsecuregcm.storage.KeysDynamoDb;
import org.whispersystems.textsecuregcm.storage.MessagesManager;
import org.whispersystems.textsecuregcm.storage.ProfilesManager;
import org.whispersystems.textsecuregcm.storage.StoredVerificationCodeManager;
import org.whispersystems.textsecuregcm.storage.UsernamesManager;
import org.whispersystems.textsecuregcm.tests.util.RedisClusterHelper;

class AccountsManagerTest {

  private Accounts accounts;
  private DeletedAccountsManager deletedAccountsManager;
  private KeysDynamoDb keys;
  private MessagesManager messagesManager;
  private ProfilesManager profilesManager;

  private RedisAdvancedClusterCommands<String, String> commands;
  private AccountsManager accountsManager;

  private static final Answer<?> ACCOUNT_UPDATE_ANSWER = (answer) -> {
    // it is implicit in the update() contract is that a successful call will
    // result in an incremented version
    final Account updatedAccount = answer.getArgument(0, Account.class);
    updatedAccount.setVersion(updatedAccount.getVersion() + 1);
    return null;
  };

  @BeforeEach
  void setup() throws InterruptedException {
    accounts = mock(Accounts.class);
    deletedAccountsManager = mock(DeletedAccountsManager.class);
    keys = mock(KeysDynamoDb.class);
    messagesManager = mock(MessagesManager.class);
    profilesManager = mock(ProfilesManager.class);

    //noinspection unchecked
    commands = mock(RedisAdvancedClusterCommands.class);

    doAnswer((Answer<Void>) invocation -> {
      final Account account = invocation.getArgument(0, Account.class);
      final String number = invocation.getArgument(1, String.class);

      account.setNumber(number);

      return null;
    }).when(accounts).changeNumber(any(), anyString());

    doAnswer(invocation -> {
      //noinspection unchecked
      invocation.getArgument(1, Consumer.class).accept(Optional.empty());
      return null;
    }).when(deletedAccountsManager).lockAndTake(anyString(), any());

    final SecureStorageClient storageClient = mock(SecureStorageClient.class);
    when(storageClient.deleteStoredData(any())).thenReturn(CompletableFuture.completedFuture(null));

    final SecureBackupClient backupClient = mock(SecureBackupClient.class);
    when(backupClient.deleteBackups(any())).thenReturn(CompletableFuture.completedFuture(null));

    accountsManager = new AccountsManager(
        accounts,
        RedisClusterHelper.buildMockRedisCluster(commands),
        deletedAccountsManager,
        keys,
        messagesManager,
        mock(UsernamesManager.class),
        profilesManager,
        mock(StoredVerificationCodeManager.class),
        storageClient,
        backupClient,
        mock(ClientPresenceManager.class));
  }

  @Test
  void testGetAccountByNumberInCache() {
    UUID uuid = UUID.randomUUID();

    when(commands.get(eq("AccountMap::+14152222222"))).thenReturn(uuid.toString());
    when(commands.get(eq("Account3::" + uuid))).thenReturn("{\"number\": \"+14152222222\", \"name\": \"test\"}");

    Optional<Account> account = accountsManager.get("+14152222222");

    assertTrue(account.isPresent());
    assertEquals(account.get().getNumber(), "+14152222222");
    assertEquals(account.get().getProfileName(), "test");

    verify(commands, times(1)).get(eq("AccountMap::+14152222222"));
    verify(commands, times(1)).get(eq("Account3::" + uuid));
    verifyNoMoreInteractions(commands);

    verifyNoInteractions(accounts);
  }

  @Test
  void testGetAccountByUuidInCache() {
    UUID uuid = UUID.randomUUID();

    when(commands.get(eq("Account3::" + uuid))).thenReturn("{\"number\": \"+14152222222\", \"name\": \"test\"}");

    Optional<Account> account = accountsManager.get(uuid);

    assertTrue(account.isPresent());
    assertEquals(account.get().getNumber(), "+14152222222");
    assertEquals(account.get().getUuid(), uuid);
    assertEquals(account.get().getProfileName(), "test");

    verify(commands, times(1)).get(eq("Account3::" + uuid));
    verifyNoMoreInteractions(commands);

    verifyNoInteractions(accounts);
  }


  @Test
  void testGetAccountByNumberNotInCache() {
    UUID uuid = UUID.randomUUID();
    Account account = new Account("+14152222222", uuid, new HashSet<>(), new byte[16]);

    when(commands.get(eq("AccountMap::+14152222222"))).thenReturn(null);
    when(accounts.get(eq("+14152222222"))).thenReturn(Optional.of(account));

    Optional<Account> retrieved = accountsManager.get("+14152222222");

    assertTrue(retrieved.isPresent());
    assertSame(retrieved.get(), account);

    verify(commands, times(1)).get(eq("AccountMap::+14152222222"));
    verify(commands, times(1)).set(eq("AccountMap::+14152222222"), eq(uuid.toString()));
    verify(commands, times(1)).set(eq("Account3::" + uuid), anyString());
    verifyNoMoreInteractions(commands);

    verify(accounts, times(1)).get(eq("+14152222222"));
    verifyNoMoreInteractions(accounts);
  }

  @Test
  void testGetAccountByUuidNotInCache() {
    UUID uuid = UUID.randomUUID();
    Account account = new Account("+14152222222", uuid, new HashSet<>(), new byte[16]);

    when(commands.get(eq("Account3::" + uuid))).thenReturn(null);
    when(accounts.get(eq(uuid))).thenReturn(Optional.of(account));

    Optional<Account> retrieved = accountsManager.get(uuid);

    assertTrue(retrieved.isPresent());
    assertSame(retrieved.get(), account);

    verify(commands, times(1)).get(eq("Account3::" + uuid));
    verify(commands, times(1)).set(eq("AccountMap::+14152222222"), eq(uuid.toString()));
    verify(commands, times(1)).set(eq("Account3::" + uuid), anyString());
    verifyNoMoreInteractions(commands);

    verify(accounts, times(1)).get(eq(uuid));
    verifyNoMoreInteractions(accounts);
  }

  @Test
  void testGetAccountByNumberBrokenCache() {
    UUID uuid = UUID.randomUUID();
    Account account = new Account("+14152222222", uuid, new HashSet<>(), new byte[16]);

    when(commands.get(eq("AccountMap::+14152222222"))).thenThrow(new RedisException("Connection lost!"));
    when(accounts.get(eq("+14152222222"))).thenReturn(Optional.of(account));

    Optional<Account> retrieved = accountsManager.get("+14152222222");

    assertTrue(retrieved.isPresent());
    assertSame(retrieved.get(), account);

    verify(commands, times(1)).get(eq("AccountMap::+14152222222"));
    verify(commands, times(1)).set(eq("AccountMap::+14152222222"), eq(uuid.toString()));
    verify(commands, times(1)).set(eq("Account3::" + uuid), anyString());
    verifyNoMoreInteractions(commands);

    verify(accounts, times(1)).get(eq("+14152222222"));
    verifyNoMoreInteractions(accounts);
  }

  @Test
  void testGetAccountByUuidBrokenCache() {
    UUID uuid = UUID.randomUUID();
    Account account = new Account("+14152222222", uuid, new HashSet<>(), new byte[16]);

    when(commands.get(eq("Account3::" + uuid))).thenThrow(new RedisException("Connection lost!"));
    when(accounts.get(eq(uuid))).thenReturn(Optional.of(account));

    Optional<Account> retrieved = accountsManager.get(uuid);

    assertTrue(retrieved.isPresent());
    assertSame(retrieved.get(), account);

    verify(commands, times(1)).get(eq("Account3::" + uuid));
    verify(commands, times(1)).set(eq("AccountMap::+14152222222"), eq(uuid.toString()));
    verify(commands, times(1)).set(eq("Account3::" + uuid), anyString());
    verifyNoMoreInteractions(commands);

    verify(accounts, times(1)).get(eq(uuid));
    verifyNoMoreInteractions(accounts);
  }

  @Test
  void testUpdate_optimisticLockingFailure() {
    UUID uuid = UUID.randomUUID();
    Account account = new Account("+14152222222", uuid, new HashSet<>(), new byte[16]);

    when(commands.get(eq("Account3::" + uuid))).thenReturn(null);

    when(accounts.get(uuid)).thenReturn(
        Optional.of(new Account("+14152222222", uuid, new HashSet<>(), new byte[16])));
    doThrow(ContestedOptimisticLockException.class)
        .doAnswer(ACCOUNT_UPDATE_ANSWER)
        .when(accounts).update(any());

    when(accounts.get(uuid)).thenReturn(
        Optional.of(new Account("+14152222222", uuid, new HashSet<>(), new byte[16])));
    doThrow(ContestedOptimisticLockException.class)
        .doAnswer(ACCOUNT_UPDATE_ANSWER)
        .when(accounts).update(any());

    account = accountsManager.update(account, a -> a.setProfileName("name"));

    assertEquals(1, account.getVersion());
    assertEquals("name", account.getProfileName());

    verify(accounts, times(1)).get(uuid);
    verify(accounts, times(2)).update(any());
    verifyNoMoreInteractions(accounts);
  }

  @Test
  void testUpdate_dynamoOptimisticLockingFailureDuringCreate() {
    UUID uuid = UUID.randomUUID();
    Account account = new Account("+14152222222", uuid, new HashSet<>(), new byte[16]);

    when(commands.get(eq("Account3::" + uuid))).thenReturn(null);
    when(accounts.get(uuid)).thenReturn(Optional.empty())
        .thenReturn(Optional.of(account));
    when(accounts.create(any())).thenThrow(ContestedOptimisticLockException.class);

    accountsManager.update(account, a -> {
    });

    verify(accounts, times(1)).update(account);
    verifyNoMoreInteractions(accounts);
  }

  @Test
  void testUpdateDevice() {
    final UUID uuid = UUID.randomUUID();
    Account account = new Account("+14152222222", uuid, new HashSet<>(), new byte[16]);

    when(accounts.get(uuid)).thenReturn(
        Optional.of(new Account("+14152222222", uuid, new HashSet<>(), new byte[16])));

    assertTrue(account.getDevices().isEmpty());

    Device enabledDevice = new Device();
    enabledDevice.setFetchesMessages(true);
    enabledDevice.setSignedPreKey(new SignedPreKey(1L, "key", "signature"));
    enabledDevice.setLastSeen(System.currentTimeMillis());
    final long deviceId = account.getNextDeviceId();
    enabledDevice.setId(deviceId);
    account.addDevice(enabledDevice);

    @SuppressWarnings("unchecked") Consumer<Device> deviceUpdater = mock(Consumer.class);
    @SuppressWarnings("unchecked") Consumer<Device> unknownDeviceUpdater = mock(Consumer.class);

    account = accountsManager.updateDevice(account, deviceId, deviceUpdater);
    account = accountsManager.updateDevice(account, deviceId, d -> d.setName("deviceName"));

    assertEquals("deviceName", account.getDevice(deviceId).orElseThrow().getName());

    verify(deviceUpdater, times(1)).accept(any(Device.class));

    accountsManager.updateDevice(account, account.getNextDeviceId(), unknownDeviceUpdater);

    verify(unknownDeviceUpdater, never()).accept(any(Device.class));
  }

  @Test
  void testCreateFreshAccount() throws InterruptedException {
    when(accounts.create(any())).thenReturn(true);

    final String e164 = "+18005550123";
    final AccountAttributes attributes = new AccountAttributes(false, 0, null, null, true, null);
    accountsManager.create(e164, "password", null, attributes);

    verify(accounts).create(argThat(account -> e164.equals(account.getNumber())));
    verifyNoInteractions(keys);
    verifyNoInteractions(messagesManager);
    verifyNoInteractions(profilesManager);
  }

  @Test
  void testReregisterAccount() throws InterruptedException {
    final UUID existingUuid = UUID.randomUUID();

    when(accounts.create(any())).thenAnswer(invocation -> {
      invocation.getArgument(0, Account.class).setUuid(existingUuid);
      return false;
    });

    final String e164 = "+18005550123";
    final AccountAttributes attributes = new AccountAttributes(false, 0, null, null, true, null);
    accountsManager.create(e164, "password", null, attributes);

    verify(accounts).create(
        argThat(account -> e164.equals(account.getNumber()) && existingUuid.equals(account.getUuid())));
    verify(keys).delete(existingUuid);
    verify(messagesManager).clear(existingUuid);
    verify(profilesManager).deleteAll(existingUuid);
  }

  @Test
  void testCreateAccountRecentlyDeleted() throws InterruptedException {
    final UUID recentlyDeletedUuid = UUID.randomUUID();

    doAnswer(invocation -> {
      //noinspection unchecked
      invocation.getArgument(1, Consumer.class).accept(Optional.of(recentlyDeletedUuid));
      return null;
    }).when(deletedAccountsManager).lockAndTake(anyString(), any());

    when(accounts.create(any())).thenReturn(true);

    final String e164 = "+18005550123";
    final AccountAttributes attributes = new AccountAttributes(false, 0, null, null, true, null);
    accountsManager.create(e164, "password", null, attributes);

    verify(accounts).create(
        argThat(account -> e164.equals(account.getNumber()) && recentlyDeletedUuid.equals(account.getUuid())));
    verifyNoInteractions(keys);
    verifyNoInteractions(messagesManager);
    verifyNoInteractions(profilesManager);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testCreateWithDiscoverability(final boolean discoverable) throws InterruptedException {
    final AccountAttributes attributes = new AccountAttributes(false, 0, null, null, discoverable, null);
    final Account account = accountsManager.create("+18005550123", "password", null, attributes);

    assertEquals(discoverable, account.isDiscoverableByPhoneNumber());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testCreateWithStorageCapability(final boolean hasStorage) throws InterruptedException {
    final AccountAttributes attributes = new AccountAttributes(false, 0, null, null, true,
        new DeviceCapabilities(false, false, false, hasStorage, false, false, false, false, false));

    final Account account = accountsManager.create("+18005550123", "password", null, attributes);

    assertEquals(hasStorage, account.isStorageSupported());
  }

  @ParameterizedTest
  @MethodSource
  void testUpdateDeviceLastSeen(final boolean expectUpdate, final long initialLastSeen, final long updatedLastSeen) {
    final Account account = new Account("+14152222222", UUID.randomUUID(), new HashSet<>(), new byte[16]);
    final Device device = new Device(Device.MASTER_ID, "device", "token", "salt", null, null, null, true, 1,
        new SignedPreKey(1, "key", "sig"), initialLastSeen, 0,
        "OWT", 0, new DeviceCapabilities());
    account.addDevice(device);

    accountsManager.updateDeviceLastSeen(account, device, updatedLastSeen);

    assertEquals(expectUpdate ? updatedLastSeen : initialLastSeen, device.getLastSeen());
    verify(accounts, expectUpdate ? times(1) : never()).update(account);
  }

  @SuppressWarnings("unused")
  private static Stream<Arguments> testUpdateDeviceLastSeen() {
    return Stream.of(
        Arguments.of(true, 1, 2),
        Arguments.of(false, 1, 1),
        Arguments.of(false, 2, 1)
    );
  }

  @Test
  void testChangePhoneNumber() throws InterruptedException {
    doAnswer(invocation -> invocation.getArgument(2, Supplier.class).get())
        .when(deletedAccountsManager).lockAndPut(anyString(), anyString(), any());

    final String originalNumber = "+14152222222";
    final String targetNumber = "+14153333333";
    final UUID uuid = UUID.randomUUID();

    Account account = new Account(originalNumber, uuid, new HashSet<>(), new byte[16]);
    account = accountsManager.changeNumber(account, targetNumber);

    assertEquals(targetNumber, account.getNumber());
  }

  @Test
  void testChangePhoneNumberSameNumber() throws InterruptedException {
    final String number = "+14152222222";

    Account account = new Account(number, UUID.randomUUID(), new HashSet<>(), new byte[16]);
    account = accountsManager.changeNumber(account, number);

    assertEquals(number, account.getNumber());
    verify(deletedAccountsManager, never()).lockAndPut(anyString(), anyString(), any());
  }

  @Test
  void testChangePhoneNumberExistingAccount() throws InterruptedException {
    doAnswer(invocation -> invocation.getArgument(2, Supplier.class).get())
        .when(deletedAccountsManager).lockAndPut(anyString(), anyString(), any());

    final String originalNumber = "+14152222222";
    final String targetNumber = "+14153333333";
    final UUID existingAccountUuid = UUID.randomUUID();
    final UUID uuid = UUID.randomUUID();

    final Account existingAccount = new Account(targetNumber, existingAccountUuid, new HashSet<>(), new byte[16]);
    when(accounts.get(targetNumber)).thenReturn(Optional.of(existingAccount));

    Account account = new Account(originalNumber, uuid, new HashSet<>(), new byte[16]);
    account = accountsManager.changeNumber(account, targetNumber);

    assertEquals(targetNumber, account.getNumber());
  }

  @Test
  void testChangePhoneNumberViaUpdate() {
    final String originalNumber = "+14152222222";
    final String targetNumber = "+14153333333";
    final UUID uuid = UUID.randomUUID();

    final Account account = new Account(originalNumber, uuid, new HashSet<>(), new byte[16]);

    assertThrows(AssertionError.class, () -> accountsManager.update(account, a -> a.setNumber(targetNumber)));
  }
}
