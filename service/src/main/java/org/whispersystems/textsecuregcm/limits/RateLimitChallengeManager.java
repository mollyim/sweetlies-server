package org.whispersystems.textsecuregcm.limits;

import static com.codahale.metrics.MetricRegistry.name;

import com.vdurmont.semver4j.Semver;
import io.micrometer.core.instrument.Metrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.whispersystems.textsecuregcm.configuration.dynamic.DynamicConfiguration;
import org.whispersystems.textsecuregcm.controllers.RateLimitExceededException;
import org.whispersystems.textsecuregcm.push.NotPushRegisteredException;
import org.whispersystems.textsecuregcm.recaptcha.RecaptchaClient;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.DynamicConfigurationManager;
import org.whispersystems.textsecuregcm.util.Util;
import org.whispersystems.textsecuregcm.util.ua.UnrecognizedUserAgentException;
import org.whispersystems.textsecuregcm.util.ua.UserAgent;
import org.whispersystems.textsecuregcm.util.ua.UserAgentUtil;

public class RateLimitChallengeManager {

  private final PushChallengeManager pushChallengeManager;
  private final RecaptchaClient recaptchaClient;

  private final PreKeyRateLimiter preKeyRateLimiter;
  private final UnsealedSenderRateLimiter unsealedSenderRateLimiter;

  private final RateLimiters rateLimiters;
  private final DynamicConfigurationManager<DynamicConfiguration> dynamicConfigurationManager;

  public static final String OPTION_RECAPTCHA = "recaptcha";
  public static final String OPTION_PUSH_CHALLENGE = "pushChallenge";

  private static final String RECAPTCHA_ATTEMPT_COUNTER_NAME = name(RateLimitChallengeManager.class, "recaptcha", "attempt");
  private static final String RESET_RATE_LIMIT_EXCEEDED_COUNTER_NAME = name(RateLimitChallengeManager.class, "resetRateLimitExceeded");

  private static final String SUCCESS_TAG_NAME = "success";

  public RateLimitChallengeManager(
      final PushChallengeManager pushChallengeManager,
      final RecaptchaClient recaptchaClient,
      final PreKeyRateLimiter preKeyRateLimiter,
      final UnsealedSenderRateLimiter unsealedSenderRateLimiter,
      final RateLimiters rateLimiters,
      final DynamicConfigurationManager<DynamicConfiguration> dynamicConfigurationManager) {

    this.pushChallengeManager = pushChallengeManager;
    this.recaptchaClient = recaptchaClient;
    this.preKeyRateLimiter = preKeyRateLimiter;
    this.unsealedSenderRateLimiter = unsealedSenderRateLimiter;
    this.rateLimiters = rateLimiters;
    this.dynamicConfigurationManager = dynamicConfigurationManager;
  }

  public void answerPushChallenge(final Account account, final String challenge) throws RateLimitExceededException {
    rateLimiters.getPushChallengeAttemptLimiter().validate(account.getUuid());

    final boolean challengeSuccess = pushChallengeManager.answerChallenge(account, challenge);

    if (challengeSuccess) {
      rateLimiters.getPushChallengeSuccessLimiter().validate(account.getUuid());
      resetRateLimits(account);
    }
  }

  public void answerRecaptchaChallenge(final Account account, final String captcha, final String mostRecentProxyIp)
      throws RateLimitExceededException {

    rateLimiters.getRecaptchaChallengeAttemptLimiter().validate(account.getUuid());

    final boolean challengeSuccess = recaptchaClient.verify(captcha, mostRecentProxyIp);

    Metrics.counter(RECAPTCHA_ATTEMPT_COUNTER_NAME,
        SUCCESS_TAG_NAME, String.valueOf(challengeSuccess)).increment();

    if (challengeSuccess) {
      rateLimiters.getRecaptchaChallengeSuccessLimiter().validate(account.getUuid());
      resetRateLimits(account);
    }
  }

  private void resetRateLimits(final Account account) throws RateLimitExceededException {
    try {
      rateLimiters.getRateLimitResetLimiter().validate(account.getUuid());
    } catch (final RateLimitExceededException e) {
      Metrics.counter(RESET_RATE_LIMIT_EXCEEDED_COUNTER_NAME).increment();

      throw e;
    }

    preKeyRateLimiter.handleRateLimitReset(account);
    unsealedSenderRateLimiter.handleRateLimitReset(account);
  }

  public boolean isClientBelowMinimumVersion(final String userAgent) {
    try {
      final UserAgent client = UserAgentUtil.parseUserAgentString(userAgent);
      final Optional<Semver> minimumClientVersion = dynamicConfigurationManager.getConfiguration()
          .getRateLimitChallengeConfiguration()
          .getMinimumSupportedVersion(client.getPlatform());

      return minimumClientVersion.map(version -> version.isGreaterThan(client.getVersion()))
          .orElse(true);
    } catch (final UnrecognizedUserAgentException ignored) {
      return false;
    }
  }

  public List<String> getChallengeOptions(final Account account) {
    final List<String> options = new ArrayList<>(2);

    if (rateLimiters.getRecaptchaChallengeAttemptLimiter().hasAvailablePermits(account.getUuid(), 1) &&
        rateLimiters.getRecaptchaChallengeSuccessLimiter().hasAvailablePermits(account.getUuid(), 1)) {

      options.add(OPTION_RECAPTCHA);
    }

    if (rateLimiters.getPushChallengeAttemptLimiter().hasAvailablePermits(account.getUuid(), 1) &&
        rateLimiters.getPushChallengeSuccessLimiter().hasAvailablePermits(account.getUuid(), 1)) {

      options.add(OPTION_PUSH_CHALLENGE);
    }

    return options;
  }

  public void sendPushChallenge(final Account account) throws NotPushRegisteredException {
    pushChallengeManager.sendChallenge(account);
  }
}
