/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.entity.ByteArrayEntity;

public final class WrappingStatusSettingResponseHandler<T> implements ResponseHandler<T> {
  private static final Logger logger = Logger.getLogger(WrappingStatusSettingResponseHandler.class.getName());
  
  private final Context context;
  private final Context parentContext;
  private final ApacheHttpClientRequest request;
  private final ResponseHandler<T> handler;

  public WrappingStatusSettingResponseHandler(
      Context context,
      Context parentContext,
      ApacheHttpClientRequest request,
      ResponseHandler<T> handler) {
    this.context = context;
    this.parentContext = parentContext;
    this.request = request;
    this.handler = handler;
  }

  @Override
  public T handleResponse(HttpResponse response) throws IOException {
    // Make the response entity repeatable if needed for recording
    makeResponseEntityRepeatable(response);
    
    instrumenter().end(context, request, response, null);
    // ending the span before executing the callback handler (and scoping the callback handler to
    // the parent context), even though we are inside of a synchronous http client callback
    // underneath HttpClient.execute(..), in order to not attribute other CLIENT span timings that
    // may be performed in the callback handler to the http client span (and so we don't end up with
    // nested CLIENT spans, which we currently suppress)
    try (Scope ignored = parentContext.makeCurrent()) {
      return handler.handleResponse(response);
    }
  }
  
  /**
   * Ensures that the response entity is repeatable so it can be read by both
   * the instrumentation (for recording) and the application code.
   */
  private void makeResponseEntityRepeatable(HttpResponse response) {
    HttpEntity entity = response.getEntity();
    if (entity != null && !entity.isRepeatable()) {
      try {
        // Read the content into memory to make it repeatable
        try (InputStream inputStream = entity.getContent();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
          
          byte[] buffer = new byte[8192];
          int bytesRead;
          while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
          }
          
          // Create a new repeatable entity
          ByteArrayEntity repeatableEntity = new ByteArrayEntity(outputStream.toByteArray());
          repeatableEntity.setContentType(entity.getContentType());
          repeatableEntity.setContentEncoding(entity.getContentEncoding());
          
          // Replace the original entity
          response.setEntity(repeatableEntity);
        }
      } catch (IOException e) {
        logger.log(Level.FINE, "Failed to make response entity repeatable", e);
        // Continue with original entity - recording may fail but application should work
      }
    }
  }
}
