/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpEntityRecorderTest {

  private HttpEntityRecorder recorder;
  private AttributesBuilder attributesBuilder;
  private Context context;

  @BeforeEach
  void setUp() {
    // Enable recording for tests
    System.setProperty("otel.instrumentation.apache-httpclient.capture-request-body", "true");
    System.setProperty("otel.instrumentation.apache-httpclient.capture-response-body", "true");
    
    recorder = new HttpEntityRecorder();
    attributesBuilder = io.opentelemetry.api.common.Attributes.builder();
    context = Context.root();
  }

  @Test
  void shouldRecordRequestBody() throws Exception {
    // Given
    String requestBody = "test request body";
    HttpEntityEnclosingRequest httpRequest = mock(HttpEntityEnclosingRequest.class);
    HttpEntity entity = new StringEntity(requestBody, StandardCharsets.UTF_8);
    when(httpRequest.getEntity()).thenReturn(entity);
    
    ApacheHttpClientRequest request = mock(ApacheHttpClientRequest.class);
    when(request.getDelegate()).thenReturn(httpRequest);

    // When
    recorder.onStart(attributesBuilder, context, request);

    // Then
    assertEquals(requestBody, attributesBuilder.build().get(HttpEntityRecorder.HTTP_REQUEST_BODY));
    assertEquals((long) requestBody.getBytes(StandardCharsets.UTF_8).length, 
                 attributesBuilder.build().get(HttpEntityRecorder.HTTP_REQUEST_BODY_SIZE));
  }

  @Test
  void shouldRecordResponseBody() throws Exception {
    // Given
    String responseBody = "test response body";
    HttpResponse httpResponse = mock(HttpResponse.class);
    HttpEntity entity = new StringEntity(responseBody, StandardCharsets.UTF_8);
    when(httpResponse.getEntity()).thenReturn(entity);
    
    ApacheHttpClientRequest request = mock(ApacheHttpClientRequest.class);

    // When
    recorder.onEnd(attributesBuilder, context, request, httpResponse, null);

    // Then
    assertEquals(responseBody, attributesBuilder.build().get(HttpEntityRecorder.HTTP_RESPONSE_BODY));
    assertEquals((long) responseBody.getBytes(StandardCharsets.UTF_8).length, 
                 attributesBuilder.build().get(HttpEntityRecorder.HTTP_RESPONSE_BODY_SIZE));
  }

  @Test
  void shouldTruncateLargeRequestBody() throws Exception {
    // Given
    String largeBody = "x".repeat(5000); // Larger than default MAX_BODY_SIZE (4096)
    HttpEntityEnclosingRequest httpRequest = mock(HttpEntityEnclosingRequest.class);
    HttpEntity entity = new StringEntity(largeBody, StandardCharsets.UTF_8);
    when(httpRequest.getEntity()).thenReturn(entity);
    
    ApacheHttpClientRequest request = mock(ApacheHttpClientRequest.class);
    when(request.getDelegate()).thenReturn(httpRequest);

    // When
    recorder.onStart(attributesBuilder, context, request);

    // Then
    String recordedBody = attributesBuilder.build().get(HttpEntityRecorder.HTTP_REQUEST_BODY);
    assertTrue(recordedBody.endsWith("... (truncated)"));
    assertTrue(recordedBody.length() < largeBody.length());
  }

  @Test
  void shouldNotRecordWhenDisabled() throws Exception {
    // Given
    System.setProperty("otel.instrumentation.apache-httpclient.capture-request-body", "false");
    System.setProperty("otel.instrumentation.apache-httpclient.capture-response-body", "false");
    
    // Recreate recorder to pick up new system properties
    recorder = new HttpEntityRecorder();
    
    String requestBody = "test request body";
    HttpEntityEnclosingRequest httpRequest = mock(HttpEntityEnclosingRequest.class);
    HttpEntity entity = new StringEntity(requestBody, StandardCharsets.UTF_8);
    when(httpRequest.getEntity()).thenReturn(entity);
    
    ApacheHttpClientRequest request = mock(ApacheHttpClientRequest.class);
    when(request.getDelegate()).thenReturn(httpRequest);

    // When
    recorder.onStart(attributesBuilder, context, request);

    // Then
    assertNull(attributesBuilder.build().get(HttpEntityRecorder.HTTP_REQUEST_BODY));
    assertNull(attributesBuilder.build().get(HttpEntityRecorder.HTTP_REQUEST_BODY_SIZE));
  }

  @Test
  void shouldHandleNullEntity() throws Exception {
    // Given
    HttpEntityEnclosingRequest httpRequest = mock(HttpEntityEnclosingRequest.class);
    when(httpRequest.getEntity()).thenReturn(null);
    
    ApacheHttpClientRequest request = mock(ApacheHttpClientRequest.class);
    when(request.getDelegate()).thenReturn(httpRequest);

    // When
    recorder.onStart(attributesBuilder, context, request);

    // Then
    assertNull(attributesBuilder.build().get(HttpEntityRecorder.HTTP_REQUEST_BODY));
    assertNull(attributesBuilder.build().get(HttpEntityRecorder.HTTP_REQUEST_BODY_SIZE));
  }

  @Test
  void shouldHandleNonEntityEnclosingRequest() throws Exception {
    // Given
    HttpRequest httpRequest = mock(HttpRequest.class); // Not HttpEntityEnclosingRequest
    ApacheHttpClientRequest request = mock(ApacheHttpClientRequest.class);
    when(request.getDelegate()).thenReturn(httpRequest);

    // When
    recorder.onStart(attributesBuilder, context, request);

    // Then
    assertNull(attributesBuilder.build().get(HttpEntityRecorder.HTTP_REQUEST_BODY));
    assertNull(attributesBuilder.build().get(HttpEntityRecorder.HTTP_REQUEST_BODY_SIZE));
  }
} 