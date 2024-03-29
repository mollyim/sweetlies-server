package org.whispersystems.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotEmpty;

public class AppConfigConfiguration {

  @JsonProperty
  private boolean enabled;

  @JsonProperty
  @NotEmpty
  private String application;

  @JsonProperty
  @NotEmpty
  private String environment;

  @JsonProperty
  @NotEmpty
  private String configuration;

  @JsonProperty
  @NotEmpty
  private String region;

  public boolean getEnabled() {
    return enabled;
  }

  public String getApplication() {
    return application;
  }

  public String getEnvironment() {
    return environment;
  }

  public String getConfigurationName() {
    return configuration;
  }

  public String getRegion() {
    return region;
  }
}
