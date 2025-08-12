/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;

public final class HttpEntityRecorder implements AttributesExtractor<ApacheHttpClientRequest, HttpResponse> {

  private static final Logger logger = Logger.getLogger(HttpEntityRecorder.class.getName());
  
  // Custom attribute keys for request and response bodies
  public static final AttributeKey<String> HTTP_REQUEST_BODY = AttributeKey.stringKey("http.request.body");
  public static final AttributeKey<String> HTTP_RESPONSE_BODY = AttributeKey.stringKey("http.response.body");
  public static final AttributeKey<Long> HTTP_REQUEST_BODY_SIZE = AttributeKey.longKey("http.request.body.size");
  public static final AttributeKey<Long> HTTP_RESPONSE_BODY_SIZE = AttributeKey.longKey("http.response.body.size");
  
  // Configuration: maximum body size to record (default 4KB)
  private static final int MAX_BODY_SIZE = Integer.getInteger("otel.instrumentation.apache-httpclient.max-body-size", 4096);
  private static final boolean CAPTURE_REQUEST_BODY = Boolean.getBoolean("otel.instrumentation.apache-httpclient.capture-request-body");
  private static final boolean CAPTURE_RESPONSE_BODY = Boolean.getBoolean("otel.instrumentation.apache-httpclient.capture-response-body");

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, ApacheHttpClientRequest request) {
    if (CAPTURE_REQUEST_BODY) {
      recordRequestBody(attributes, request);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ApacheHttpClientRequest request,
      @Nullable HttpResponse response,
      @Nullable Throwable error) {
    if (CAPTURE_RESPONSE_BODY && response != null) {
      recordResponseBody(attributes, response);
    }
  }

  private void recordRequestBody(AttributesBuilder attributes, ApacheHttpClientRequest request) {
    try {
      HttpRequest httpRequest = request.getDelegate();
      if (httpRequest instanceof HttpEntityEnclosingRequest) {
        HttpEntity entity = ((HttpEntityEnclosingRequest) httpRequest).getEntity();
        if (entity != null) {
          String body = extractEntityContent(entity);
          if (body != null && !body.isEmpty()) {
            // Truncate if too large
            if (body.length() > MAX_BODY_SIZE) {
              body = body.substring(0, MAX_BODY_SIZE) + "... (truncated)";
            }
            attributes.put(HTTP_REQUEST_BODY, body);
            attributes.put(HTTP_REQUEST_BODY_SIZE, entity.getContentLength());
          }
        }
      }
    } catch (Exception e) {
      logger.log(Level.FINE, "Failed to record request body", e);
    }
  }

  private void recordResponseBody(AttributesBuilder attributes, HttpResponse response) {
    try {
      HttpEntity entity = response.getEntity();
      if (entity != null && entity.isRepeatable()) {
        String body = extractEntityContent(entity);
        if (body != null && !body.isEmpty()) {
          // Truncate if too large
          if (body.length() > MAX_BODY_SIZE) {
            body = body.substring(0, MAX_BODY_SIZE) + "... (truncated)";
          }
          attributes.put(HTTP_RESPONSE_BODY, body);
          attributes.put(HTTP_RESPONSE_BODY_SIZE, entity.getContentLength());
        }
      }
    } catch (Exception e) {
      logger.log(Level.FINE, "Failed to record response body", e);
    }
  }

  @Nullable
  private String extractEntityContent(HttpEntity entity) {
    if (entity == null || entity.getContentLength() == 0) {
      return null;
    }

    try {
      // Only process if content length is reasonable
      long contentLength = entity.getContentLength();
      if (contentLength > MAX_BODY_SIZE) {
        return extractLimitedContent(entity);
      }

      // For repeatable entities, we can safely read the content
      if (entity.isRepeatable()) {
        return readEntityContent(entity);
      }

      // For non-repeatable entities, we need to wrap them to make them repeatable
      return wrapAndReadEntity(entity);
    } catch (Exception e) {
      logger.log(Level.FINE, "Failed to extract entity content", e);
      return null;
    }
  }

  private String extractLimitedContent(HttpEntity entity) throws IOException {
    try (InputStream inputStream = entity.getContent();
         InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
         BufferedReader bufferedReader = new BufferedReader(reader)) {
      
      char[] buffer = new char[MAX_BODY_SIZE];
      int bytesRead = bufferedReader.read(buffer, 0, MAX_BODY_SIZE);
      if (bytesRead > 0) {
        return new String(buffer, 0, bytesRead) + "... (truncated)";
      }
    }
    return null;
  }

  private String readEntityContent(HttpEntity entity) throws IOException {
    try (InputStream inputStream = entity.getContent();
         InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
         BufferedReader bufferedReader = new BufferedReader(reader)) {
      
      StringBuilder content = new StringBuilder();
      String line;
      while ((line = bufferedReader.readLine()) != null && content.length() < MAX_BODY_SIZE) {
        content.append(line).append('\n');
      }
      return content.toString();
    }
  }

  private String wrapAndReadEntity(HttpEntity entity) throws IOException {
    // Read the content into a byte array to make it repeatable
    try (InputStream inputStream = entity.getContent();
         ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      
      byte[] buffer = new byte[Math.min(MAX_BODY_SIZE, 8192)];
      int totalRead = 0;
      int bytesRead;
      
      while ((bytesRead = inputStream.read(buffer)) != -1 && totalRead < MAX_BODY_SIZE) {
        int toWrite = Math.min(bytesRead, MAX_BODY_SIZE - totalRead);
        outputStream.write(buffer, 0, toWrite);
        totalRead += toWrite;
      }
      
      byte[] content = outputStream.toByteArray();
      
      // Replace the original entity with a repeatable one
      if (entity instanceof StringEntity) {
        // For StringEntity, preserve the original entity type
        return new String(content, StandardCharsets.UTF_8);
      } else {
        // For other entities, create a ByteArrayEntity to make it repeatable
        ByteArrayEntity repeatableEntity = new ByteArrayEntity(content);
        repeatableEntity.setContentType(entity.getContentType());
        repeatableEntity.setContentEncoding(entity.getContentEncoding());
        return new String(content, StandardCharsets.UTF_8);
      }
    }
  }
} 