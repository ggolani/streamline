package org.apache.streamline.streams.service.exception.request;

import org.apache.streamline.streams.service.exception.StreamServiceException;

import javax.ws.rs.core.Response;

public class BadRequestException extends StreamServiceException {
  private static final String DEFAULT_MESSAGE = "Bad request.";
  private static final String PARAMETER_MISSING_MESSAGE = "Bad request. Param [%s] is missing or empty.";

  private BadRequestException(String message) {
    super(Response.Status.BAD_REQUEST, message);
  }

  private BadRequestException(String message, Throwable cause) {
    super(Response.Status.BAD_REQUEST, message, cause);
  }

  public static BadRequestException of() {
    return new BadRequestException(DEFAULT_MESSAGE);
  }

  public static BadRequestException of(Throwable cause) {
    return new BadRequestException(DEFAULT_MESSAGE, cause);
  }

  public static BadRequestException missingParameter(String parameterName) {
    return new BadRequestException(buildParameterMissingMessage(parameterName));
  }

  public static BadRequestException missingParameter(String parameterName, Throwable cause) {
    return new BadRequestException(buildParameterMissingMessage(parameterName), cause);
  }

  private static String buildParameterMissingMessage(String parameterName) {
    return String.format(PARAMETER_MISSING_MESSAGE, parameterName);
  }
}
