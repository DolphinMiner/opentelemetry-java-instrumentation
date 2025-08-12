/*
 * Example usage of Apache HttpClient 4.0 with HTTP body recording
 * 
 * To run this example with body recording enabled:
 * 
 * java -javaagent:opentelemetry-javaagent.jar \
 *      -Dotel.instrumentation.apache-httpclient.capture-request-body=true \
 *      -Dotel.instrumentation.apache-httpclient.capture-response-body=true \
 *      -Dotel.instrumentation.apache-httpclient.max-body-size=8192 \
 *      -Dotel.resource.attributes=service.name=httpclient-example \
 *      -Dotel.traces.exporter=logging \
 *      ExampleUsage
 */

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

public class ExampleUsage {
    
    public static void main(String[] args) throws Exception {
        HttpClient httpClient = new DefaultHttpClient();
        
        // Example 1: GET Request (no request body, will record response body)
        System.out.println("=== GET Request Example ===");
        HttpGet getRequest = new HttpGet("https://httpbin.org/json");
        HttpResponse getResponse = httpClient.execute(getRequest);
        System.out.println("GET Response Status: " + getResponse.getStatusLine().getStatusCode());
        EntityUtils.consume(getResponse.getEntity());
        
        // Example 2: POST Request with JSON body (will record both request and response bodies)
        System.out.println("\n=== POST Request Example ===");
        HttpPost postRequest = new HttpPost("https://httpbin.org/post");
        
        String requestJson = "{\"message\": \"Hello, World!\", \"timestamp\": \"" + 
                           System.currentTimeMillis() + "\"}";
        StringEntity requestEntity = new StringEntity(requestJson, "UTF-8");
        requestEntity.setContentType("application/json");
        postRequest.setEntity(requestEntity);
        
        HttpResponse postResponse = httpClient.execute(postRequest);
        System.out.println("POST Response Status: " + postResponse.getStatusLine().getStatusCode());
        
        // Read and print response body
        HttpEntity responseEntity = postResponse.getEntity();
        if (responseEntity != null) {
            String responseBody = EntityUtils.toString(responseEntity);
            System.out.println("Response Body Length: " + responseBody.length() + " characters");
            // Note: The actual response body is also recorded in span attributes
        }
        
        // Example 3: Large request body (will be truncated in span attributes)
        System.out.println("\n=== Large Request Body Example ===");
        HttpPost largePostRequest = new HttpPost("https://httpbin.org/post");
        
        // Create a large JSON payload (>4KB default limit)
        StringBuilder largeJson = new StringBuilder("{\"data\": \"");
        for (int i = 0; i < 1000; i++) {
            largeJson.append("This is a long string to create a large request body. ");
        }
        largeJson.append("\"}");
        
        StringEntity largeRequestEntity = new StringEntity(largeJson.toString(), "UTF-8");
        largeRequestEntity.setContentType("application/json");
        largePostRequest.setEntity(largeRequestEntity);
        
        HttpResponse largePostResponse = httpClient.execute(largePostRequest);
        System.out.println("Large POST Response Status: " + largePostResponse.getStatusLine().getStatusCode());
        System.out.println("Large Request Body Size: " + largeJson.length() + " characters");
        System.out.println("(Body will be truncated in span attributes due to size limit)");
        
        EntityUtils.consume(largePostResponse.getEntity());
        
        httpClient.getConnectionManager().shutdown();
        System.out.println("\n=== All requests completed ===");
        System.out.println("Check your telemetry backend for spans with the following attributes:");
        System.out.println("- http.request.body (for POST requests)");
        System.out.println("- http.request.body.size");
        System.out.println("- http.response.body");
        System.out.println("- http.response.body.size");
    }
}

/*
 * Expected Span Attributes (when recording is enabled):
 * 
 * GET Request Span:
 * - http.response.body: JSON response from httpbin.org/json
 * - http.response.body.size: size of the response
 * 
 * POST Request Span:
 * - http.request.body: {"message": "Hello, World!", "timestamp": "..."}
 * - http.request.body.size: size of the request body
 * - http.response.body: JSON response from httpbin.org/post (includes echoed request)
 * - http.response.body.size: size of the response
 * 
 * Large POST Request Span:
 * - http.request.body: {"data": "This is a long string...} ... (truncated)
 * - http.request.body.size: actual full size (>4KB)
 * - http.response.body: response (may also be truncated if large)
 * - http.response.body.size: actual response size
 */ 