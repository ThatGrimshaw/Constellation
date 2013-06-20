/*
Copyright 2011 WebDriver committers
Copyright 2011 Software Freedom Conservancy

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.openqa.grid.internal;

import org.openqa.grid.common.exception.GridException;

import com.google.common.io.ByteStreams;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.openqa.grid.internal.listeners.CommandListener;
import org.openqa.grid.web.Hub;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Represent a running test for the hub/registry. A test session is created when a TestSlot becomes
 * available for a test. <p/> The session is destroyed when the test ends ( ended by the client or
 * timed out)
 */
@SuppressWarnings("JavaDoc")
public class TestSession {

  private static final Logger log = Logger.getLogger(TestSession.class.getName());
  static final int MAX_IDLE_TIME_BEFORE_CONSIDERED_ORPHANED = 5000;

  private final String internalKey;
  private final TestSlot slot;
  private volatile ExternalSessionKey externalKey = null;
  private volatile long sessionCreatedAt;
  private volatile long lastActivity;
  private final Map<String, Object> requestedCapabilities;
  private Map<String, Object> objects = Collections.synchronizedMap(new HashMap<String, Object>());
  private volatile boolean ignoreTimeout = false;
  private final TimeSource timeSource;

  public String getInternalKey() {
    return internalKey;
  }

  /**
   * Creates a test session on the specified testSlot.
   */
  TestSession(TestSlot slot, Map<String, Object> requestedCapabilities,
              TimeSource timeSource) {
    internalKey = UUID.randomUUID().toString();
    this.slot = slot;
    this.requestedCapabilities = requestedCapabilities;
    this.timeSource = timeSource;
    lastActivity = this.timeSource.currentTimeInMillis();
  }

  /**
   * the capabilities the client requested. It will match the TestSlot capabilities, but is not
   * equals.
   */
  public Map<String, Object> getRequestedCapabilities() {
    return requestedCapabilities;
  }

  /**
   * Get the session key from the remote. It's up to the remote to guarantee the key is unique. If 2
   * remotes return the same session key, the tests will overwrite each other.
   *
   * @return the key that was provided by the remote when the POST /session command was sent.
   */
  public ExternalSessionKey getExternalKey() {
    return externalKey;
  }

  /**
   * associate this session to the session provided by the remote.
   */
  public void setExternalKey(ExternalSessionKey externalKey) {
    this.externalKey = externalKey;
    sessionCreatedAt = lastActivity;
  }

  /**
   * give the time in milliseconds since the last access to this test session, or 0 is ignore time
   * out has been set to true.
   *
   * @return time in millis
   * @see TestSession#setIgnoreTimeout(boolean)
   */
  public long getInactivityTime() {
    if (ignoreTimeout) {
      return 0;
    } else {
      return timeSource.currentTimeInMillis() - lastActivity;
    }

  }

  public boolean isOrphaned() {
    final long elapsedSinceCreation = timeSource.currentTimeInMillis() - sessionCreatedAt;
    // The session needs to have been open for at least the time interval and we need to have not
    // seen any new
    // commands during that time frame.
    return slot.getProtocol().isSelenium()
           && elapsedSinceCreation > MAX_IDLE_TIME_BEFORE_CONSIDERED_ORPHANED
           && sessionCreatedAt == lastActivity;
  }

  /**
   * @return the TestSlot this session is executed against.
   */
  public TestSlot getSlot() {
    return slot;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((internalKey == null) ? 0 : internalKey.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    TestSession other = (TestSession) obj;
    return internalKey.equals(other.internalKey);
  }

  @Override
  public String toString() {
    return externalKey != null ? "ext. key " + externalKey : internalKey
                                                             + " (int. key, remote not contacted yet.)";
  }

  /**
   * Forward the request to the remote, execute the TestSessionListeners if applicable.
   */
  public void forward(HttpServletRequest request, HttpServletResponse response) throws IOException {
    forward(request, response, null, false);
  }

  private HttpClient getClient() {
    return slot.getProxy().getHttpClientFactory().getGridHttpClient();
  }

  /**
   * Forward the request to the remote.
   *
   * @param content               Overwrite the body. Useful when the body of the request was
   *                              already read.
   * @param interceptResponseBody for selenium1 protocol, you need to read the content of the
   *                              response to find the session.
   * @return the content of the response if interceptResponseBody=true. null otherwise
   */
  public String forward(HttpServletRequest request, HttpServletResponse response, String content,
                        boolean interceptResponseBody) throws IOException {
    String res = null;

    if (slot.getProxy() instanceof CommandListener) {
      ((CommandListener) slot.getProxy()).beforeCommand(this, request, response);
    }

    lastActivity = timeSource.currentTimeInMillis();
    URL remoteURL = slot.getRemoteURL();

    String pathSpec = request.getServletPath() + request.getContextPath();
    String path = request.getRequestURI();
    if (!path.startsWith(pathSpec)) {
      throw new IllegalStateException("Expected path " + path + " to start with pathSpec "
                                      + pathSpec);
    }
    String end = path.substring(pathSpec.length());
    String ok = remoteURL + end;
    String uri = new URL(remoteURL, ok).toExternalForm();

    InputStream body = null;
    if (request.getContentLength() > 0 || request.getHeader("Transfer-Encoding") != null) {
      body = request.getInputStream();
    }

    HttpRequest proxyRequest;
    if (content != null) {
      BasicHttpEntityEnclosingRequest r =
          new BasicHttpEntityEnclosingRequest(request.getMethod(), uri);
      r.setEntity(new StringEntity(content));
      proxyRequest = r;
    } else if (body != null) {
      BasicHttpEntityEnclosingRequest r =
          new BasicHttpEntityEnclosingRequest(request.getMethod(), uri);
      r.setEntity(new InputStreamEntity(body, request.getContentLength()));
      proxyRequest = r;
    } else {
      proxyRequest = new BasicHttpRequest(request.getMethod(), uri);
    }

    for (Enumeration<?> e = request.getHeaderNames(); e.hasMoreElements(); ) {
      String headerName = (String) e.nextElement();

      if ("Content-Length".equalsIgnoreCase(headerName)) {
        continue; // already set
      }

      proxyRequest.setHeader(headerName, request.getHeader(headerName));
    }

    HttpClient client = getClient();

    HttpHost host = new HttpHost(remoteURL.getHost(), remoteURL.getPort());

    HttpResponse proxyResponse = client.execute(host, proxyRequest);
    lastActivity = timeSource.currentTimeInMillis();

    response.setStatus(proxyResponse.getStatusLine().getStatusCode());
    HttpEntity responseBody = proxyResponse.getEntity();

    processResponseHeaders(response, remoteURL, pathSpec, proxyResponse);

    if (responseBody != null) {
      InputStream in = responseBody.getContent();

      if (interceptResponseBody) {
        res = getResponseUtf8Content(in);
        in = new ByteArrayInputStream(res.getBytes("UTF-8"));
      }

      writeRawBody(response, in);
    }

    if (slot.getProxy() instanceof CommandListener) {
      ((CommandListener) slot.getProxy()).afterCommand(this, request, response);
    }
    return res;
  }

  private void writeRawBody(HttpServletResponse response, InputStream in) throws IOException {
    try {
      OutputStream out = response.getOutputStream();
      try {
        byte[] rawBody = ByteStreams.toByteArray(in);

        // We need to set the Content-Length header before we write to the output stream. Usually
        // the
        // Content-Length header is already set because we take it from the proxied request. But, it
        // won't
        // be set when we consume chunked content, since that doesn't use Content-Length. As we're
        // not
        // going to send a chunked response, we need to set the Content-Length in order for the
        // response
        // to be valid.
        if (!response.containsHeader("Content-Length")) {
          response.setIntHeader("Content-Length", rawBody.length);
        }

        out.write(rawBody);
      } finally {
        try {
          out.close();
        } catch (IOException e) {
          log.log(Level.SEVERE, "Problem closing response's output stream.", e);
        }
      }
    } finally {
      try {
        in.close();
      } catch (IOException e) {
        log.log(Level.SEVERE, "Problem closing proxied response's input stream.", e);
      }
    }
  }

  private String getResponseUtf8Content(InputStream in) {
    String res;
    StringBuilder sb = new StringBuilder();
    String line;
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
      while ((line = reader.readLine()) != null) {
        // TODO freynaud bug ?
        sb.append(line);/* .append("\n") */
      }
      in.close();
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    res = sb.toString();
    return res;
  }

  private void processResponseHeaders(HttpServletResponse response, URL remoteURL, String pathSpec,
                                      HttpResponse proxyResponse) throws MalformedURLException {
    for (Header header : proxyResponse.getAllHeaders()) {
      String name = header.getName();
      String value = header.getValue();

      // HttpEntity#getContent() chews up the chunk-size octet (i.e., the InputStream does not
      // actually map 1:1 to the underlying response body). This breaks any client expecting the
      // chunk size. We could
      // try to recreate it, but since the chunks are already read in and decoded, you'd end up with
      // a
      // single chunk, which isn't all that useful. So, we return the response as a traditional
      // response with a
      // Content-Length header, obviating the need for the Transfer-Encoding header.
      if (name.equalsIgnoreCase("Transfer-Encoding") && value.equalsIgnoreCase("chunked")) {
        continue;
      }

      // the location needs to point to the hub that will proxy
      // everything.
      if (name.equalsIgnoreCase("Location")) {
        URL returnedLocation = new URL(value);
        String driverPath = remoteURL.getPath();
        String wrongPath = returnedLocation.getPath();
        String correctPath = wrongPath.replace(driverPath, "");
        Hub hub = slot.getProxy().getRegistry().getHub();
        String location = "http://" + hub.getHost() + ":" + hub.getPort() + pathSpec + correctPath;
        response.setHeader(name, location);
      } else {
        response.setHeader(name, value);
      }
    }
  }

  /**
   * Allow you to retrieve an object previously stored on the test session.
   *
   * @return the object you stored
   */
  public Object get(String key) {
    return objects.get(key);
  }

  /**
   * Allows you to store an object on the test session.
   *
   * @param key a non-null string
   */
  public void put(String key, Object value) {
    objects.put(key, value);
  }


  /**
   * Sends a DELETE/testComplete (webdriver/selenium) session command to the remote, following web
   * driver protocol.
   *
   * @return true is the remote replied successfully to the request.
   */
  public boolean sendDeleteSessionRequest() {
    URL remoteURL = slot.getRemoteURL();

    HttpRequest request;
    switch (slot.getProtocol()) {
      case Selenium:
        request =
            new BasicHttpRequest("POST", remoteURL.toExternalForm()
                                         + "/?cmd=testComplete&sessionId=" + getExternalKey()
                .getKey());
        break;
      case WebDriver:
        String uri = remoteURL.toString() + "/session/" + externalKey;
        request = new BasicHttpRequest("DELETE", uri);
        break;
      default:
        throw new GridException("Error, protocol not implemented.");
    }

    HttpHost host = new HttpHost(remoteURL.getHost(), remoteURL.getPort());

    boolean ok;
    try {
      HttpClient client = getClient();
      HttpResponse response = client.execute(host, request);
      int code = response.getStatusLine().getStatusCode();
      ok = (code >= 200) && (code <= 299);
    } catch (Throwable e) {
      ok = false;
      // corrupted or the something else already sent the DELETE.
      log.severe("Error releasing. Server corrupted ?");
    }
    return ok;
  }


  /**
   * allow to bypass time out for this session. ignore = true => the session will not time out.
   * setIgnoreTimeout(true) also update the lastActivity to now.
   */
  public void setIgnoreTimeout(boolean ignore) {
    if (!ignore) {
      lastActivity = timeSource.currentTimeInMillis();
    }
    this.ignoreTimeout = ignore;

  }

}