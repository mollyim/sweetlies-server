/*
 * Copyright 2013-2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.controllers;

import com.codahale.metrics.annotation.Timed;
import io.dropwizard.auth.Auth;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.validation.Valid;
import javax.validation.valueextraction.Unwrapping;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.profiles.ProfileKeyCommitment;
import org.signal.zkgroup.profiles.ProfileKeyCredentialRequest;
import org.signal.zkgroup.profiles.ProfileKeyCredentialResponse;
import org.signal.zkgroup.profiles.ServerZkProfileOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.auth.Anonymous;
import org.whispersystems.textsecuregcm.auth.AuthenticatedAccount;
import org.whispersystems.textsecuregcm.auth.OptionalAccess;
import org.whispersystems.textsecuregcm.auth.UnidentifiedAccessChecksum;
import org.whispersystems.textsecuregcm.configuration.dynamic.DynamicConfiguration;
import org.whispersystems.textsecuregcm.entities.CreateProfileRequest;
import org.whispersystems.textsecuregcm.entities.Profile;
import org.whispersystems.textsecuregcm.entities.ProfileAvatarUploadAttributes;
import org.whispersystems.textsecuregcm.entities.UserCapabilities;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.s3.PolicySigner;
import org.whispersystems.textsecuregcm.s3.PostPolicyGenerator;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.DynamicConfigurationManager;
import org.whispersystems.textsecuregcm.storage.ProfilesManager;
import org.whispersystems.textsecuregcm.storage.UsernamesManager;
import org.whispersystems.textsecuregcm.storage.VersionedProfile;
import org.whispersystems.textsecuregcm.util.ExactlySize;
import org.whispersystems.textsecuregcm.util.Pair;
import org.whispersystems.textsecuregcm.util.Util;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Path("/v1/profile")
public class ProfileController {

  private final Logger logger = LoggerFactory.getLogger(ProfileController.class);

  private final Clock clock;
  private final RateLimiters     rateLimiters;
  private final ProfilesManager  profilesManager;
  private final AccountsManager  accountsManager;
  private final UsernamesManager usernamesManager;
  private final DynamicConfigurationManager<DynamicConfiguration> dynamicConfigurationManager;

  private final PolicySigner              policySigner;
  private final PostPolicyGenerator       policyGenerator;
  private final ServerZkProfileOperations zkProfileOperations;

  private final S3Client            s3client;
  private final String              bucket;

  public ProfileController(
      Clock clock,
      RateLimiters rateLimiters,
      AccountsManager accountsManager,
      ProfilesManager profilesManager,
      UsernamesManager usernamesManager,
      DynamicConfigurationManager<DynamicConfiguration> dynamicConfigurationManager,
      S3Client s3client,
      PostPolicyGenerator policyGenerator,
      PolicySigner policySigner,
      String bucket,
      ServerZkProfileOperations zkProfileOperations) {
    this.clock = clock;
    this.rateLimiters        = rateLimiters;
    this.accountsManager     = accountsManager;
    this.profilesManager     = profilesManager;
    this.usernamesManager    = usernamesManager;
    this.dynamicConfigurationManager = dynamicConfigurationManager;
    this.zkProfileOperations = zkProfileOperations;
    this.bucket              = bucket;
    this.s3client            = s3client;
    this.policyGenerator     = policyGenerator;
    this.policySigner        = policySigner;
  }

  @Timed
  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response setProfile(@Auth AuthenticatedAccount auth, @Valid CreateProfileRequest request) {
    Optional<VersionedProfile> currentProfile = profilesManager.get(auth.getAccount().getUuid(), request.getVersion());
    String avatar = request.isAvatar() ? generateAvatarObjectName() : null;
    Optional<ProfileAvatarUploadAttributes> response = Optional.empty();

    profilesManager.set(auth.getAccount().getUuid(),
        new VersionedProfile(
            request.getVersion(),
            request.getName(),
            avatar,
            request.getAboutEmoji(),
            request.getAbout(),
            request.getPaymentAddress(),
            request.getCommitment().serialize()));

    if (request.isAvatar()) {
      Optional<String> currentAvatar = Optional.empty();

      if (currentProfile.isPresent() && currentProfile.get().getAvatar() != null && currentProfile.get().getAvatar()
          .startsWith("profiles/")) {
        currentAvatar = Optional.of(currentProfile.get().getAvatar());
      }

      if (currentAvatar.isEmpty() && auth.getAccount().getAvatar() != null && auth.getAccount().getAvatar()
          .startsWith("profiles/")) {
        currentAvatar = Optional.of(auth.getAccount().getAvatar());
      }

      currentAvatar.ifPresent(s -> s3client.deleteObject(DeleteObjectRequest.builder()
          .bucket(bucket)
          .key(s)
          .build()));

      response = Optional.of(generateAvatarUploadForm(avatar));
    }

    accountsManager.update(auth.getAccount(), a -> {
      a.setProfileName(request.getName());
      a.setAvatar(avatar);
      a.setCurrentProfileVersion(request.getVersion());
    });

    if (response.isPresent()) {
      return Response.ok(response).build();
    } else {
      return Response.ok().build();
    }
  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{uuid}/{version}")
  public Optional<Profile> getProfile(
      @Auth Optional<AuthenticatedAccount> auth,
      @HeaderParam(OptionalAccess.UNIDENTIFIED) Optional<Anonymous> accessKey,
      @Context ContainerRequestContext containerRequestContext,
      @PathParam("uuid") UUID uuid,
      @PathParam("version") String version)
      throws RateLimitExceededException {
    return getVersionedProfile(auth.map(AuthenticatedAccount::getAccount), accessKey,
        getAcceptableLanguagesForRequest(containerRequestContext), uuid,
        version, Optional.empty());
  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{uuid}/{version}/{credentialRequest}")
  public Optional<Profile> getProfile(
      @Auth Optional<AuthenticatedAccount> auth,
      @HeaderParam(OptionalAccess.UNIDENTIFIED) Optional<Anonymous> accessKey,
      @Context ContainerRequestContext containerRequestContext,
      @PathParam("uuid") UUID uuid,
      @PathParam("version") String version,
      @PathParam("credentialRequest") String credentialRequest)
      throws RateLimitExceededException {
    return getVersionedProfile(auth.map(AuthenticatedAccount::getAccount), accessKey,
        getAcceptableLanguagesForRequest(containerRequestContext), uuid,
        version, Optional.of(credentialRequest));
  }

  private Optional<Profile> getVersionedProfile(
      Optional<Account> requestAccount,
      Optional<Anonymous> accessKey,
      List<Locale> acceptableLanguages,
      UUID uuid,
      String version,
      Optional<String> credentialRequest)
      throws RateLimitExceededException {
    try {
      if (requestAccount.isEmpty() && accessKey.isEmpty()) {
        throw new WebApplicationException(Response.Status.UNAUTHORIZED);
      }

      boolean isSelf = false;
      if (requestAccount.isPresent()) {
        UUID authedUuid = requestAccount.get().getUuid();
        rateLimiters.getProfileLimiter().validate(authedUuid);
        isSelf = uuid.equals(authedUuid);
      }

      Optional<Account> accountProfile = accountsManager.get(uuid);
      OptionalAccess.verify(requestAccount, accessKey, accountProfile);

      assert(accountProfile.isPresent());

      Optional<String>           username   = usernamesManager.get(accountProfile.get().getUuid());
      Optional<VersionedProfile> profile    = profilesManager.get(uuid, version);

      String name = profile.map(VersionedProfile::getName).orElse(accountProfile.get().getProfileName());
      String about = profile.map(VersionedProfile::getAbout).orElse(null);
      String aboutEmoji = profile.map(VersionedProfile::getAboutEmoji).orElse(null);
      String avatar = profile.map(VersionedProfile::getAvatar).orElse(accountProfile.get().getAvatar());
      Optional<String> currentProfileVersion = accountProfile.get().getCurrentProfileVersion();

      // Allow requests where either the version matches the latest version on Account or the latest version on Account
      // is empty to read the payment address.
      final String paymentAddress = profile
          .filter(p -> currentProfileVersion.map(v -> v.equals(version)).orElse(true))
          .map(VersionedProfile::getPaymentAddress)
          .orElse(null);

      Optional<ProfileKeyCredentialResponse> credential = getProfileCredential(credentialRequest, profile, uuid);

      return Optional.of(new Profile(
          name,
          about,
          aboutEmoji,
          avatar,
          paymentAddress,
          accountProfile.get().getIdentityKey(),
          UnidentifiedAccessChecksum.generateFor(accountProfile.get().getUnidentifiedAccessKey()),
          accountProfile.get().isUnrestrictedUnidentifiedAccess(),
          UserCapabilities.createForAccount(accountProfile.get()),
          username.orElse(null),
          null,
          credential.orElse(null)));
    } catch (InvalidInputException e) {
      logger.info("Bad profile request", e);
      throw new WebApplicationException(Response.Status.BAD_REQUEST);
    }
  }


  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/username/{username}")
  public Profile getProfileByUsername(
      @Auth AuthenticatedAccount auth,
      @Context ContainerRequestContext containerRequestContext,
      @PathParam("username") String username)
      throws RateLimitExceededException {
    rateLimiters.getUsernameLookupLimiter().validate(auth.getAccount().getUuid());

    username = username.toLowerCase();

    Optional<UUID> uuid = usernamesManager.get(username);

    if (uuid.isEmpty()) {
      throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
    }

    final boolean isSelf = auth.getAccount().getUuid().equals(uuid.get());

    Optional<Account> accountProfile = accountsManager.get(uuid.get());

    if (accountProfile.isEmpty()) {
      throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
    }

    return new Profile(
        accountProfile.get().getProfileName(),
        null,
        null,
        accountProfile.get().getAvatar(),
        null,
        accountProfile.get().getIdentityKey(),
        UnidentifiedAccessChecksum.generateFor(accountProfile.get().getUnidentifiedAccessKey()),
        accountProfile.get().isUnrestrictedUnidentifiedAccess(),
        UserCapabilities.createForAccount(accountProfile.get()),
        username,
        accountProfile.get().getUuid(),
        null);
  }

  private Optional<ProfileKeyCredentialResponse> getProfileCredential(Optional<String>           encodedProfileCredentialRequest,
                                                                      Optional<VersionedProfile> profile,
                                                                      UUID                       uuid)
      throws InvalidInputException
  {
    if (encodedProfileCredentialRequest.isEmpty()) return Optional.empty();
    if (profile.isEmpty())                         return Optional.empty();

    try {
      ProfileKeyCommitment         commitment = new ProfileKeyCommitment(profile.get().getCommitment());
      ProfileKeyCredentialRequest  request    = new ProfileKeyCredentialRequest(Hex.decodeHex(encodedProfileCredentialRequest.get()));
      ProfileKeyCredentialResponse response   = zkProfileOperations.issueProfileKeyCredential(request, uuid, commitment);

      return Optional.of(response);
    } catch (DecoderException | VerificationFailedException e) {
      throw new WebApplicationException(e, Response.status(Response.Status.BAD_REQUEST).build());
    }
  }


  // Old profile endpoints. Replaced by versioned profile endpoints (above)

    @Deprecated
    @Timed
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/name/{name}")
    public void setProfile(@Auth AuthenticatedAccount auth,
                           @PathParam("name") @ExactlySize(value = {72, 108}, payload = {Unwrapping.Unwrap.class}) Optional<String> name) {
        accountsManager.update(auth.getAccount(), a -> a.setProfileName(name.orElse(null)));
    }

  @Deprecated
  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{identifier}")
  public Profile getProfile(
      @Auth Optional<AuthenticatedAccount> auth,
      @HeaderParam(OptionalAccess.UNIDENTIFIED) Optional<Anonymous> accessKey,
      @Context ContainerRequestContext containerRequestContext,
      @HeaderParam("User-Agent") String userAgent,
      @PathParam("identifier") UUID identifier,
      @QueryParam("ca") boolean useCaCertificate)
      throws RateLimitExceededException {

    if (auth.isEmpty() && accessKey.isEmpty()) {
      throw new WebApplicationException(Response.Status.UNAUTHORIZED);
    }

    boolean isSelf = false;
    if (auth.isPresent()) {
      UUID authedUuid = auth.get().getAccount().getUuid();
      rateLimiters.getProfileLimiter().validate(authedUuid);
      isSelf = authedUuid.equals(identifier);
    }

    Optional<Account> accountProfile = accountsManager.get(identifier);
    OptionalAccess.verify(auth.map(AuthenticatedAccount::getAccount), accessKey, accountProfile);

    Optional<String> username = usernamesManager.get(accountProfile.get().getUuid());

    return new Profile(
        accountProfile.get().getProfileName(),
        null,
        null,
        accountProfile.get().getAvatar(),
        null,
        accountProfile.get().getIdentityKey(),
        UnidentifiedAccessChecksum.generateFor(accountProfile.get().getUnidentifiedAccessKey()),
        accountProfile.get().isUnrestrictedUnidentifiedAccess(),
        UserCapabilities.createForAccount(accountProfile.get()),
        username.orElse(null),
        null,
        null);
  }

    @Deprecated
    @Timed
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/form/avatar")
    public ProfileAvatarUploadAttributes getAvatarUploadForm(@Auth AuthenticatedAccount auth) {
        String previousAvatar = auth.getAccount().getAvatar();
        String objectName = generateAvatarObjectName();
        ProfileAvatarUploadAttributes profileAvatarUploadAttributes = generateAvatarUploadForm(objectName);

        if (previousAvatar != null && previousAvatar.startsWith("profiles/")) {
            s3client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(previousAvatar)
                    .build());
        }

        accountsManager.update(auth.getAccount(), a -> a.setAvatar(objectName));

    return profileAvatarUploadAttributes;
  }

  ////

  private ProfileAvatarUploadAttributes generateAvatarUploadForm(String objectName) {
    ZonedDateTime        now            = ZonedDateTime.now(ZoneOffset.UTC);
    Pair<String, String> policy         = policyGenerator.createFor(now, objectName, 10 * 1024 * 1024);
    String               signature      = policySigner.getSignature(now, policy.second());

    return new ProfileAvatarUploadAttributes(objectName, policy.first(), "private", "AWS4-HMAC-SHA256",
                                             now.format(PostPolicyGenerator.AWS_DATE_TIME), policy.second(), signature);

  }

  private String generateAvatarObjectName() {
    byte[] object = new byte[16];
    new SecureRandom().nextBytes(object);

    return "profiles/" + Base64.encodeBase64URLSafeString(object);
  }

  private List<Locale> getAcceptableLanguagesForRequest(ContainerRequestContext containerRequestContext) {
    try {
      return containerRequestContext.getAcceptableLanguages();
    } catch (final ProcessingException e) {
      logger.warn("Could not get acceptable languages", e);
      return List.of();
    }
  }
}
