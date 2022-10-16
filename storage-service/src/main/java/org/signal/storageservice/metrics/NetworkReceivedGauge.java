/*
 * Copyright 2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storageservice.metrics;

import org.signal.storageservice.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class NetworkReceivedGauge extends NetworkGauge {

  private final Logger logger = LoggerFactory.getLogger(NetworkSentGauge.class);

  private long lastTimestamp;
  private long lastReceived;

  public NetworkReceivedGauge() {
    try {
      this.lastTimestamp = System.currentTimeMillis();
      this.lastReceived  = getSentReceived().second();
    } catch (IOException e) {
      logger.warn(NetworkReceivedGauge.class.getSimpleName(), e);
    }
  }

  @Override
  public Double getValue() {
    try {
      long             timestamp       = System.currentTimeMillis();
      Pair<Long, Long> sentAndReceived = getSentReceived();
      double           bytesReceived   = sentAndReceived.second() - lastReceived;
      double           secondsElapsed  = (timestamp - this.lastTimestamp) / 1000;
      double           result          = bytesReceived / secondsElapsed;

      this.lastTimestamp = timestamp;
      this.lastReceived  = sentAndReceived.second();

      return result;
    } catch (IOException e) {
      logger.warn("NetworkReceivedGauge", e);
      return -1D;
    }
  }

}
