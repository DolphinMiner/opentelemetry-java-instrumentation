# Apache HttpClient 4.0 - HTTP Request/Response Body Recording

This instrumentation module has been enhanced to support recording HTTP request and response bodies as span attributes.

## Configuration

The HTTP body recording feature is controlled by the following system properties:

### Enable/Disable Recording

- **`otel.instrumentation.apache-httpclient.capture-request-body`** (boolean, default: `false`)
  - When set to `true`, enables recording of HTTP request bodies
  - Example: `-Dotel.instrumentation.apache-httpclient.capture-request-body=true`

- **`otel.instrumentation.apache-httpclient.capture-response-body`** (boolean, default: `false`)
  - When set to `true`, enables recording of HTTP response bodies
  - Example: `-Dotel.instrumentation.apache-httpclient.capture-response-body=true`

### Size Limits

- **`otel.instrumentation.apache-httpclient.max-body-size`** (integer, default: `4096`)
  - Maximum size in bytes for recorded request/response bodies
  - Bodies larger than this limit will be truncated with "... (truncated)" suffix
  - Example: `-Dotel.instrumentation.apache-httpclient.max-body-size=8192`

## Span Attributes

When enabled, the following attributes will be added to HTTP client spans:

### Request Body Attributes

- **`http.request.body`** (string)
  - The complete HTTP request body content (up to the size limit)
  - Only present for requests that contain entity data (POST, PUT, PATCH, etc.)

- **`http.request.body.size`** (long)
  - The size in bytes of the HTTP request body
  - Represents the actual content length, not the truncated recorded length

### Response Body Attributes

- **`http.response.body`** (string)
  - The complete HTTP response body content (up to the size limit)
  - Only present for responses that contain entity data

- **`http.response.body.size`** (long)
  - The size in bytes of the HTTP response body
  - Represents the actual content length, not the truncated recorded length

## Usage Examples

### Basic Configuration

To enable both request and response body recording:

```bash
java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.instrumentation.apache-httpclient.capture-request-body=true \
     -Dotel.instrumentation.apache-httpclient.capture-response-body=true \
     -jar your-application.jar
```

### Custom Size Limit

To increase the maximum recorded body size to 16KB:

```bash
java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.instrumentation.apache-httpclient.capture-request-body=true \
     -Dotel.instrumentation.apache-httpclient.capture-response-body=true \
     -Dotel.instrumentation.apache-httpclient.max-body-size=16384 \
     -jar your-application.jar
```

### Request Body Only

To record only request bodies (useful for debugging outgoing API calls):

```bash
java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.instrumentation.apache-httpclient.capture-request-body=true \
     -jar your-application.jar
```

## Performance Considerations

### Memory Usage

- Recorded bodies are stored in memory as span attributes
- Large bodies consume additional memory proportional to their size
- Use appropriate size limits to prevent excessive memory usage

### Request Processing Impact

- **Request Bodies**: Minimal impact as bodies are typically small and already in memory
- **Response Bodies**: May require buffering the entire response in memory to make it repeatable
- **Non-repeatable Entities**: Will be converted to repeatable entities, consuming additional memory

### Network Impact

- No additional network overhead
- Recording happens after data is already received/sent

## Security Considerations

### Sensitive Data

**⚠️ WARNING**: HTTP bodies may contain sensitive information such as:
- Authentication tokens
- Personal identifiable information (PII)
- API keys and passwords
- Business-critical data

### Recommendations

1. **Disable in Production**: Consider disabling body recording in production environments
2. **Filter Sensitive Endpoints**: Implement custom filtering logic if needed
3. **Use Size Limits**: Keep body size limits small to reduce exposure
4. **Log Monitoring**: Ensure your telemetry backend properly handles and secures span data

## Troubleshooting

### Bodies Not Being Recorded

1. **Check Configuration**: Ensure the appropriate system properties are set
2. **Verify Entity Type**: Only requests with `HttpEntity` content are recorded
3. **Check Size**: Very small or empty entities may not be recorded
4. **Review Logs**: Enable debug logging with `-Dotel.javaagent.debug=true`

### Memory Issues

1. **Reduce Size Limit**: Lower the `max-body-size` setting
2. **Disable Response Recording**: Response bodies are typically larger than request bodies
3. **Selective Enabling**: Only enable recording when debugging specific issues

### Application Behavior Changes

1. **Entity Consumption**: Response entities are made repeatable, which may affect application logic
2. **Error Handling**: Recording errors are logged but should not affect application functionality
3. **Performance**: Large body recording may impact response times

## Implementation Details

### Entity Handling

- **Repeatable Entities**: Read directly without modification
- **Non-repeatable Entities**: Converted to `ByteArrayEntity` to make them repeatable
- **Large Entities**: Read up to the size limit only

### Character Encoding

- All bodies are decoded using UTF-8 encoding
- Binary content may not be displayed correctly
- Content-Type headers are preserved during entity conversion

### Error Recovery

- Recording failures are logged at FINE level and do not interrupt request processing
- Original entities are preserved if recording fails
- Application functionality is not affected by recording errors 