/*
 * Copyright 2013-2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.whispersystems.textsecuregcm.util;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class Util {

  private static final Pattern VALID_NIKNUM_PATTERN = Pattern.compile("\\+0{3}[1-9][0-9]{11}");

  /**
   * Checks that the given number is a valid, E164-normalized niknumber.
   *
   * @param number the number to check
   *
   * @throws ImpossibleNikNumberException if the given number is not a valid niknumber at all
   */
  public static void requireNikNumber(final String number) throws ImpossibleNikNumberException {
    if (number != null && VALID_NIKNUM_PATTERN.matcher(number).matches()) {
      return;
    }
    throw new ImpossibleNikNumberException();
  }

  public static boolean isEmpty(String param) {
    return param == null || param.length() == 0;
  }

  public static byte[] truncate(byte[] element, int length) {
    byte[] result = new byte[length];
    System.arraycopy(element, 0, result, 0, result.length);

    return result;
  }

  public static int toIntExact(long value) {
    if ((int)value != value) {
      throw new ArithmeticException("integer overflow");
    }
    return (int)value;
  }

  public static int currentDaysSinceEpoch() {
    return Util.toIntExact(System.currentTimeMillis() / 1000 / 60 / 60 / 24);
  }

  public static void sleep(long i) {
    try {
      Thread.sleep(i);
    } catch (InterruptedException ie) {}
  }

  public static void wait(Object object) {
    try {
      object.wait();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  public static void wait(Object object, long timeoutMs) {
    try {
      object.wait(timeoutMs);
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  public static int hashCode(Object... objects) {
    return Arrays.hashCode(objects);
  }

  public static long todayInMillis() {
    return todayInMillis(Clock.systemUTC());
  }

  public static long todayInMillis(Clock clock) {
    return TimeUnit.DAYS.toMillis(TimeUnit.MILLISECONDS.toDays(clock.instant().toEpochMilli()));
  }

  public static long todayInMillisGivenOffsetFromNow(Clock clock, Duration offset) {
    final long currentTimeSeconds = offset.addTo(clock.instant()).getLong(ChronoField.INSTANT_SECONDS);
    return TimeUnit.DAYS.toMillis(TimeUnit.SECONDS.toDays(currentTimeSeconds));
  }

  public static Optional<String> findBestLocale(List<LanguageRange> priorityList, Collection<String> supportedLocales) {
    return Optional.ofNullable(Locale.lookupTag(priorityList, supportedLocales));
  }
}
