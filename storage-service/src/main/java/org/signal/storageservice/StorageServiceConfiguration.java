/*
 * Copyright 2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storageservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import org.signal.storageservice.configuration.AuthenticationConfiguration;
import org.signal.storageservice.configuration.BigTableConfiguration;
import org.signal.storageservice.configuration.CdnConfiguration;
import org.signal.storageservice.configuration.GroupConfiguration;
import org.signal.storageservice.configuration.ZkConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class StorageServiceConfiguration extends Configuration {

  @JsonProperty
  @Valid
  @NotNull
  private BigTableConfiguration bigtable;

  @JsonProperty
  @Valid
  @NotNull
  private AuthenticationConfiguration authentication;

  @JsonProperty
  @Valid
  @NotNull
  private ZkConfiguration zkConfig;

  @JsonProperty
  @Valid
  @NotNull
  private CdnConfiguration cdn;

  @JsonProperty
  @Valid
  @NotNull
  private GroupConfiguration group;

  public BigTableConfiguration getBigTableConfiguration() {
    return bigtable;
  }

  public AuthenticationConfiguration getAuthenticationConfiguration() {
    return authentication;
  }

  public ZkConfiguration getZkConfiguration() {
    return zkConfig;
  }

  public CdnConfiguration getCdnConfiguration() {
    return cdn;
  }

  public GroupConfiguration getGroupConfiguration() {
    return group;
  }
}
