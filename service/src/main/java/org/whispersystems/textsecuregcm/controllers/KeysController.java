/*
 * Copyright 2013-2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.whispersystems.textsecuregcm.controllers;

import static com.codahale.metrics.MetricRegistry.name;

import com.codahale.metrics.annotation.Timed;
import io.dropwizard.auth.Auth;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.whispersystems.textsecuregcm.auth.Anonymous;
import org.whispersystems.textsecuregcm.auth.AuthenticatedAccount;
import org.whispersystems.textsecuregcm.auth.DisabledPermittedAuthenticatedAccount;
import org.whispersystems.textsecuregcm.auth.OptionalAccess;
import org.whispersystems.textsecuregcm.entities.PreKey;
import org.whispersystems.textsecuregcm.entities.PreKeyCount;
import org.whispersystems.textsecuregcm.entities.PreKeyResponse;
import org.whispersystems.textsecuregcm.entities.PreKeyResponseItem;
import org.whispersystems.textsecuregcm.entities.PreKeyState;
import org.whispersystems.textsecuregcm.entities.SignedPreKey;
import org.whispersystems.textsecuregcm.limits.PreKeyRateLimiter;
import org.whispersystems.textsecuregcm.limits.RateLimitChallengeException;
import org.whispersystems.textsecuregcm.limits.RateLimitChallengeManager;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.KeysDynamoDb;
import org.whispersystems.textsecuregcm.util.Util;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Path("/v2/keys")
public class KeysController {

  private final RateLimiters                rateLimiters;
  private final KeysDynamoDb                keysDynamoDb;
  private final AccountsManager             accounts;
  private final PreKeyRateLimiter           preKeyRateLimiter;

  private final RateLimitChallengeManager rateLimitChallengeManager;

  private static final String PREKEY_REQUEST_COUNTER_NAME = name(KeysController.class, "preKeyGet");
  private static final String RATE_LIMITED_GET_PREKEYS_COUNTER_NAME = name(KeysController.class, "rateLimitedGetPreKeys");

  public KeysController(RateLimiters rateLimiters, KeysDynamoDb keysDynamoDb, AccountsManager accounts,
      PreKeyRateLimiter preKeyRateLimiter,
      RateLimitChallengeManager rateLimitChallengeManager) {
    this.rateLimiters                = rateLimiters;
    this.keysDynamoDb                = keysDynamoDb;
    this.accounts                    = accounts;
    this.preKeyRateLimiter           = preKeyRateLimiter;
    this.rateLimitChallengeManager   = rateLimitChallengeManager;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public PreKeyCount getStatus(@Auth AuthenticatedAccount auth) {
    int count = keysDynamoDb.getCount(auth.getAccount(), auth.getAuthenticatedDevice().getId());

    if (count > 0) {
      count = count - 1;
    }

    return new PreKeyCount(count);
  }

  @Timed
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  public void setKeys(@Auth DisabledPermittedAuthenticatedAccount disabledPermittedAuth, @Valid PreKeyState preKeys) {
    Account account = disabledPermittedAuth.getAccount();
    Device device = disabledPermittedAuth.getAuthenticatedDevice();
    boolean updateAccount = false;

    if (!preKeys.getSignedPreKey().equals(device.getSignedPreKey())) {
      updateAccount = true;
    }

    if (!preKeys.getIdentityKey().equals(account.getIdentityKey())) {
      updateAccount = true;
    }

    if (updateAccount) {
      account = accounts.update(account, a -> {
        a.getDevice(device.getId()).ifPresent(d -> d.setSignedPreKey(preKeys.getSignedPreKey()));
        a.setIdentityKey(preKeys.getIdentityKey());
      });
    }

    keysDynamoDb.store(account, device.getId(), preKeys.getPreKeys());
  }

  @Timed
  @GET
  @Path("/{identifier}/{device_id}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getDeviceKeys(@Auth Optional<AuthenticatedAccount> auth,
      @HeaderParam(OptionalAccess.UNIDENTIFIED) Optional<Anonymous> accessKey,
      @PathParam("identifier") UUID targetUuid,
      @PathParam("device_id") String deviceId,
      @HeaderParam("User-Agent") String userAgent)
      throws RateLimitExceededException, RateLimitChallengeException, ServerRejectedException {

    if (!auth.isPresent() && !accessKey.isPresent()) {
      throw new WebApplicationException(Response.Status.UNAUTHORIZED);
    }

    final Optional<Account> account = auth.map(AuthenticatedAccount::getAccount);

    Optional<Account> target = accounts.get(targetUuid);
    OptionalAccess.verify(account, accessKey, target, deviceId);

    assert (target.isPresent());

    {
      Metrics.counter(PREKEY_REQUEST_COUNTER_NAME).increment();
    }

    if (account.isPresent()) {
      rateLimiters.getPreKeysLimiter().validate(
          account.get().getUuid() + "." + auth.get().getAuthenticatedDevice().getId() + "__" + target.get().getUuid()
              + "." + deviceId);

      try {
        preKeyRateLimiter.validate(account.get());
      } catch (RateLimitExceededException e) {

        final boolean legacyClient = rateLimitChallengeManager.isClientBelowMinimumVersion(userAgent);

        Metrics.counter(RATE_LIMITED_GET_PREKEYS_COUNTER_NAME,
                "legacyClient", String.valueOf(legacyClient))
            .increment();

        if (legacyClient) {
          throw new ServerRejectedException();
        }
        throw new RateLimitChallengeException(account.get(), e.getRetryDuration());
      }
    }

    Map<Long, PreKey>        preKeysByDeviceId = getLocalKeys(target.get(), deviceId);
    List<PreKeyResponseItem> responseItems     = new LinkedList<>();

    for (Device device : target.get().getDevices()) {
      if (device.isEnabled() && (deviceId.equals("*") || device.getId() == Long.parseLong(deviceId))) {
        SignedPreKey signedPreKey = device.getSignedPreKey();
        PreKey       preKey       = preKeysByDeviceId.get(device.getId());

        if (signedPreKey != null || preKey != null) {
          responseItems.add(new PreKeyResponseItem(device.getId(), device.getRegistrationId(), signedPreKey, preKey));
        }
      }
    }

    if (responseItems.isEmpty()) return Response.status(404).build();
    else                         return Response.ok().entity(new PreKeyResponse(target.get().getIdentityKey(), responseItems)).build();
  }

  @Timed
  @PUT
  @Path("/signed")
  @Consumes(MediaType.APPLICATION_JSON)
  public void setSignedKey(@Auth AuthenticatedAccount auth, @Valid SignedPreKey signedPreKey) {
    Device device = auth.getAuthenticatedDevice();

    accounts.updateDevice(auth.getAccount(), device.getId(), d -> d.setSignedPreKey(signedPreKey));
  }

  @Timed
  @GET
  @Path("/signed")
  @Produces(MediaType.APPLICATION_JSON)
  public Optional<SignedPreKey> getSignedKey(@Auth AuthenticatedAccount auth) {
    Device device = auth.getAuthenticatedDevice();
    SignedPreKey signedPreKey = device.getSignedPreKey();

    if (signedPreKey != null) {
      return Optional.of(signedPreKey);
    } else {
      return Optional.empty();
    }
  }

  private Map<Long, PreKey> getLocalKeys(Account destination, String deviceIdSelector) {
    try {
      if (deviceIdSelector.equals("*")) {
        return keysDynamoDb.take(destination);
      }

      long deviceId = Long.parseLong(deviceIdSelector);

      return keysDynamoDb.take(destination, deviceId)
              .map(preKey -> Map.of(deviceId, preKey))
              .orElse(Collections.emptyMap());
    } catch (NumberFormatException e) {
      throw new WebApplicationException(Response.status(422).build());
    }
  }
}
