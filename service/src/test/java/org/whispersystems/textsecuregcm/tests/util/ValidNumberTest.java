/*
 * Copyright 2013-2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.tests.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.whispersystems.textsecuregcm.util.ImpossibleNikNumberException;
import org.whispersystems.textsecuregcm.util.Util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidNumberTest {

  @ParameterizedTest
  @ValueSource(strings = {
      "+000123456789012",
      "+000477009001111",
      "+000671234567890"})
  void requireNikNumber(final String number) {
    assertDoesNotThrow(() -> Util.requireNikNumber(number));
  }

  @Test
  void requireNikNumberNullOrEmpty() {
    assertThrows(ImpossibleNikNumberException.class, () -> Util.requireNikNumber(null));
    assertThrows(ImpossibleNikNumberException.class, () -> Util.requireNikNumber(""));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "Definitely not a phone number at all",
      "+141512312341",
      "+000023456789012",
      "+00012345678901b",
      "000477009001111",
      "+0004770090011119",
      " +000477009001111",
      "+001447535742222",
  })
  void requireNikNumberImpossibleNumber(final String number) {
    assertThrows(ImpossibleNikNumberException.class, () -> Util.requireNikNumber(number));
  }
}
