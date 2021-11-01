/*
 * Copyright 2013-2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.whispersystems.textsecuregcm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

class DeletedAccountsTest {

  private static final String NEEDS_RECONCILIATION_INDEX_NAME = "needs_reconciliation_test";

  @RegisterExtension
  static DynamoDbExtension dynamoDbExtension = DynamoDbExtension.builder()
      .tableName("deleted_accounts_test")
      .hashKey(DeletedAccounts.KEY_ACCOUNT_E164)
      .attributeDefinition(AttributeDefinition.builder()
          .attributeName(DeletedAccounts.KEY_ACCOUNT_E164)
          .attributeType(ScalarAttributeType.S).build())
      .build();

  private DeletedAccounts deletedAccounts;

  @BeforeEach
  void setUp() {
    deletedAccounts = new DeletedAccounts(dynamoDbExtension.getDynamoDbClient(),
        dynamoDbExtension.getTableName());
  }

  @Test
  void testPutFind() {
    final UUID uuid = UUID.randomUUID();
    final String e164 = "+18005551234";

    assertEquals(Optional.empty(), deletedAccounts.findUuid(e164));

    deletedAccounts.put(uuid, e164);

    assertEquals(Optional.of(uuid), deletedAccounts.findUuid(e164));
  }

  @Test
  void testRemove() {
    final UUID uuid = UUID.randomUUID();
    final String e164 = "+18005551234";

    assertEquals(Optional.empty(), deletedAccounts.findUuid(e164));

    deletedAccounts.put(uuid, e164);

    assertEquals(Optional.of(uuid), deletedAccounts.findUuid(e164));

    deletedAccounts.remove(e164);

    assertEquals(Optional.empty(), deletedAccounts.findUuid(e164));
  }
}
