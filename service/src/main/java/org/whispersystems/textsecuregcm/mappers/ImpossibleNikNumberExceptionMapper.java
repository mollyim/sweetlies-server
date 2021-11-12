/*
 * Copyright 2013-2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.mappers;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import org.whispersystems.textsecuregcm.util.ImpossibleNikNumberException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static org.whispersystems.textsecuregcm.metrics.MetricsUtil.name;

public class ImpossibleNikNumberExceptionMapper implements ExceptionMapper<ImpossibleNikNumberException> {

  private static final Counter IMPOSSIBLE_NUMBER_COUNTER =
      Metrics.counter(name(ImpossibleNikNumberExceptionMapper.class, "impossibleNumbers"));

  @Override
  public Response toResponse(final ImpossibleNikNumberException exception) {
    IMPOSSIBLE_NUMBER_COUNTER.increment();

    return Response.status(Response.Status.BAD_REQUEST).build();
  }
}
