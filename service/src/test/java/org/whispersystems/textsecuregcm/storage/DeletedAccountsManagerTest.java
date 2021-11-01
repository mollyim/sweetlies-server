/*
 * Copyright 2013-2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeletedAccountsManagerTest {

  @RegisterExtension
  static final DynamoDbExtension DELETED_ACCOUNTS_DYNAMODB_EXTENSION = DynamoDbExtension.builder()
      .tableName("deleted_accounts_test")
      .hashKey(DeletedAccounts.KEY_ACCOUNT_E164)
      .attributeDefinition(AttributeDefinition.builder()
          .attributeName(DeletedAccounts.KEY_ACCOUNT_E164)
          .attributeType(ScalarAttributeType.S).build())
      .build();

  @RegisterExtension
  static DynamoDbExtension DELETED_ACCOUNTS_LOCK_DYNAMODB_EXTENSION = DynamoDbExtension.builder()
      .tableName("deleted_accounts_lock_test")
      .hashKey(DeletedAccounts.KEY_ACCOUNT_E164)
      .attributeDefinition(AttributeDefinition.builder()
          .attributeName(DeletedAccounts.KEY_ACCOUNT_E164)
          .attributeType(ScalarAttributeType.S).build())
      .build();

  private DeletedAccounts deletedAccounts;
  private DeletedAccountsManager deletedAccountsManager;

  @BeforeEach
  void setUp() {
    deletedAccounts = new DeletedAccounts(DELETED_ACCOUNTS_DYNAMODB_EXTENSION.getDynamoDbClient(),
        DELETED_ACCOUNTS_DYNAMODB_EXTENSION.getTableName());

    deletedAccountsManager = new DeletedAccountsManager(deletedAccounts,
        DELETED_ACCOUNTS_LOCK_DYNAMODB_EXTENSION.getLegacyDynamoClient(),
        DELETED_ACCOUNTS_LOCK_DYNAMODB_EXTENSION.getTableName());
  }

  @Test
  void testLockAndTake() throws InterruptedException {
    final UUID uuid = UUID.randomUUID();
    final String e164 = "+18005551234";

    deletedAccounts.put(uuid, e164);
    deletedAccountsManager.lockAndTake(e164, maybeUuid -> assertEquals(Optional.of(uuid), maybeUuid));
    assertEquals(Optional.empty(), deletedAccounts.findUuid(e164));
  }

  @Test
  void testLockAndTakeWithException() {
    final UUID uuid = UUID.randomUUID();
    final String e164 = "+18005551234";

    deletedAccounts.put(uuid, e164);

    assertThrows(RuntimeException.class, () -> deletedAccountsManager.lockAndTake(e164, maybeUuid -> {
      assertEquals(Optional.of(uuid), maybeUuid);
      throw new RuntimeException("OH NO");
    }));

    assertEquals(Optional.of(uuid), deletedAccounts.findUuid(e164));
  }
}
