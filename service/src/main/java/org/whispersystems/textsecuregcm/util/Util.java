/*
 * Copyright 2013-2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.whispersystems.textsecuregcm.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Util {

  private static final Pattern COUNTRY_CODE_PATTERN = Pattern.compile("^\\+([17]|2[07]|3[0123469]|4[013456789]|5[12345678]|6[0123456]|8[1246]|9[0123458]|\\d{3})");

  private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

  /**
   * Checks that the given number is a valid, E164-normalized phone number.
   *
   * @param number the number to check
   *
   * @throws ImpossiblePhoneNumberException if the given number is not a valid phone number at all
   * @throws NonNormalizedPhoneNumberException if the given number is a valid phone number, but isn't E164-normalized
   */
  public static void requireNormalizedNumber(final String number) throws ImpossiblePhoneNumberException, NonNormalizedPhoneNumberException {
    if (!PHONE_NUMBER_UTIL.isPossibleNumber(number, null)) {
      throw new ImpossiblePhoneNumberException();
    }

    try {
      final PhoneNumber phoneNumber = PHONE_NUMBER_UTIL.parse(number, null);
      final String normalizedNumber = PHONE_NUMBER_UTIL.format(phoneNumber, PhoneNumberFormat.E164);

      if (!number.equals(normalizedNumber)) {
        throw new NonNormalizedPhoneNumberException(number, normalizedNumber);
      }
    } catch (final NumberParseException e) {
      throw new ImpossiblePhoneNumberException(e);
    }
  }

  public static String getCountryCode(String number) {
    Matcher matcher = COUNTRY_CODE_PATTERN.matcher(number);

    if (matcher.find()) return matcher.group(1);
    else                return "0";
  }

  public static String getNumberPrefix(String number) {
    String countryCode  = getCountryCode(number);
    int    remaining    = number.length() - (1 + countryCode.length());
    int    prefixLength = Math.min(4, remaining);

    return number.substring(0, 1 + countryCode.length() + prefixLength);
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
