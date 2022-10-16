/*
 * Copyright 2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storageservice.storage;

import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.protobuf.ByteString;
import org.signal.storageservice.storage.protos.groups.Group;
import org.signal.storageservice.storage.protos.groups.GroupChange;
import org.signal.storageservice.storage.protos.groups.GroupChanges.GroupChangeState;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class GroupsManager {

  private final GroupsTable   groupsTable;
  private final GroupLogTable groupLogTable;

  public GroupsManager(BigtableDataClient client, String groupsTableId, String groupLogsTableId) {
    this.groupsTable   = new GroupsTable  (client, groupsTableId   );
    this.groupLogTable = new GroupLogTable(client, groupLogsTableId);
  }

  public CompletableFuture<Optional<Group>> getGroup(ByteString groupId) {
    return groupsTable.getGroup(groupId);
  }

  public CompletableFuture<Boolean> createGroup(ByteString groupId, Group group) {
    return groupsTable.createGroup(groupId, group);
  }

  public CompletableFuture<Optional<Group>> updateGroup(ByteString groupId, Group group) {
    return groupsTable.updateGroup(groupId, group)
                      .thenCompose(modified -> {
                        if (modified) return CompletableFuture.completedFuture(Optional.empty());
                        else          return getGroup(groupId).thenApply(result -> Optional.of(result.orElseThrow()));
                      });
  }

  public CompletableFuture<List<GroupChangeState>> getChangeRecords(ByteString groupId, Group group, int fromVersionInclusive, int toVersionExclusive) {
    if (fromVersionInclusive >= toVersionExclusive) {
      throw new IllegalArgumentException("Version to read from (" + fromVersionInclusive + ") must be less than version to read to (" + toVersionExclusive + ")");
    }

    return groupLogTable.getRecordsFromVersion(groupId, fromVersionInclusive, toVersionExclusive)
                        .thenApply(groupChangeStates -> {
                          if (isGroupInRange(group, fromVersionInclusive, toVersionExclusive) && groupVersionMissing(group, groupChangeStates) && toVersionExclusive - 1 == group.getVersion()) {
                            groupChangeStates.add(GroupChangeState.newBuilder().setGroupState(group).build());
                          }
                          return groupChangeStates;
                        });
  }

  public CompletableFuture<Boolean> appendChangeRecord(ByteString groupId, int version, GroupChange change, Group state) {
    return groupLogTable.append(groupId, version, change, state);
  }

  private static boolean isGroupInRange(Group group, int fromVersionInclusive, int toVersionExclusive) {
    return fromVersionInclusive <= group.getVersion() && group.getVersion() < toVersionExclusive;
  }

  private static boolean groupVersionMissing(Group group, List<GroupChangeState> groupChangeStates) {
    return groupChangeStates.stream().noneMatch(groupChangeState -> groupChangeState.getGroupState().getVersion() == group.getVersion());
  }
}
