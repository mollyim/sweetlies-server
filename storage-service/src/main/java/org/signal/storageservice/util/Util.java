/*
 * Copyright 2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storageservice.util;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class Util {

  public static int currentDaysSinceEpoch() {
    return Util.toIntExact(System.currentTimeMillis() / 1000 / 60 / 60 / 24);
  }

  public static int toIntExact(long value) {
    if ((int)value != value) {
      throw new ArithmeticException("integer overflow");
    }
    return (int)value;
  }

  public static boolean isEmpty(String param) {
    return param == null || param.length() == 0;
  }

  public static byte[] truncate(byte[] element, int length) {
    byte[] result = new byte[length];
    System.arraycopy(element, 0, result, 0, result.length);

    return result;
  }

  public static byte[] generateSecretBytes(int size) {
    byte[] data = new byte[size];
    new SecureRandom().nextBytes(data);
    return data;
  }

  public static int hashCode(Object... objects) {
    return Arrays.hashCode(objects);
  }

  public static long todayInMillis() {
    return TimeUnit.DAYS.toMillis(TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis()));
  }
}
