package com.bracit.fisprocess.config;

import com.bracit.fisprocess.annotation.ApiVersion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.method.HandlerMethod;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ApiVersionInterceptor Tests")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiVersionInterceptorTest {

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private HandlerMethod handlerMethod;

  private ApiVersionInterceptor interceptor;
  private StringWriter responseWriter;

  @BeforeEach
  void setUp() {
    interceptor = new ApiVersionInterceptor(Set.of(1, 2), 2);
    responseWriter = new StringWriter();
  }

  // ---------------------------------------------------------------------------
  // Header-based version resolution
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("should accept request with supported API-Version header")
  void shouldAcceptSupportedHeaderVersion() throws Exception {
    when(request.getHeader("API-Version")).thenReturn("2");
    when(request.getRequestURI()).thenReturn("/v1/accounts");
    doReturn(VersionedController.class).when(handlerMethod).getBeanType();
    when(handlerMethod.getMethodAnnotation(ApiVersion.class)).thenReturn(null);

    boolean result = interceptor.preHandle(request, response, handlerMethod);

    assertThat(result).isTrue();
    verify(response).setHeader("API-Version", "2");
    verify(response, never()).setStatus(anyInt());
  }

  @Test
  @DisplayName("should reject unsupported API-Version header with 406")
  void shouldRejectUnsupportedHeaderVersion() throws Exception {
    when(request.getHeader("API-Version")).thenReturn("99");
    when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));

    boolean result = interceptor.preHandle(request, response, handlerMethod);

    assertThat(result).isFalse();
    verify(response).setStatus(406);
    assertThat(responseWriter.toString()).contains("406");
  }

  @Test
  @DisplayName("should fall back to URL path when header is absent")
  void shouldFallBackToUrlPath() throws Exception {
    when(request.getHeader("API-Version")).thenReturn(null);
    when(request.getRequestURI()).thenReturn("/v1/accounts/123");
    doReturn(VersionedController.class).when(handlerMethod).getBeanType();
    when(handlerMethod.getMethodAnnotation(ApiVersion.class)).thenReturn(null);

    boolean result = interceptor.preHandle(request, response, handlerMethod);

    assertThat(result).isTrue();
    verify(response).setHeader("API-Version", "1");
  }

  @Test
  @DisplayName("should fall back to @ApiVersion annotation when header and URL lack version")
  void shouldFallBackToAnnotation() throws Exception {
    when(request.getHeader("API-Version")).thenReturn(null);
    when(request.getRequestURI()).thenReturn("/accounts");
    doReturn(VersionedController.class).when(handlerMethod).getBeanType();
    when(handlerMethod.getMethodAnnotation(ApiVersion.class)).thenReturn(null);

    boolean result = interceptor.preHandle(request, response, handlerMethod);

    assertThat(result).isTrue();
    verify(response).setHeader("API-Version", "1");
  }

  // ---------------------------------------------------------------------------
  // Deprecation headers
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("should add deprecation headers when endpoint is deprecated")
  void shouldAddDeprecationHeaders() throws Exception {
    when(request.getHeader("API-Version")).thenReturn(null);
    when(request.getRequestURI()).thenReturn("/v1/legacy");

    ApiVersion annotation = DeprecatedController.class.getAnnotation(ApiVersion.class);
    doReturn(DeprecatedController.class).when(handlerMethod).getBeanType();
    when(handlerMethod.getMethodAnnotation(ApiVersion.class)).thenReturn(annotation);

    boolean result = interceptor.preHandle(request, response, handlerMethod);

    assertThat(result).isTrue();
    verify(response).setHeader("Deprecation", "true");
    verify(response).setHeader(eq("Sunset"), anyString());
    verify(response).setHeader("Link", "</v2/legacy>; rel=\"successor-version\"");
  }

  @Test
  @DisplayName("should return 410 Gone when sunset date has passed")
  void shouldReturn410WhenSunsetPassed() throws Exception {
    when(request.getHeader("API-Version")).thenReturn(null);
    when(request.getRequestURI()).thenReturn("/v1/expired");
    when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));

    ApiVersion annotation = ExpiredController.class.getAnnotation(ApiVersion.class);
    doReturn(ExpiredController.class).when(handlerMethod).getBeanType();
    when(handlerMethod.getMethodAnnotation(ApiVersion.class)).thenReturn(annotation);

    boolean result = interceptor.preHandle(request, response, handlerMethod);

    assertThat(result).isFalse();
    verify(response).setStatus(410);
    assertThat(responseWriter.toString()).contains("410");
    assertThat(responseWriter.toString()).contains("retired");
  }

  // ---------------------------------------------------------------------------
  // Edge cases
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("should pass through non-HandlerMethod requests (static resources)")
  void shouldPassNonHandlerMethod() throws Exception {
    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isTrue();
    verify(response, never()).setHeader(anyString(), anyString());
  }

  @Test
  @DisplayName("should tolerate malformed API-Version header")
  void shouldTolerateMalformedHeader() throws Exception {
    when(request.getHeader("API-Version")).thenReturn("abc");
    when(request.getRequestURI()).thenReturn("/v1/accounts");
    doReturn(VersionedController.class).when(handlerMethod).getBeanType();
    when(handlerMethod.getMethodAnnotation(ApiVersion.class)).thenReturn(null);

    // Should fall through to URL path parsing since header is unparseable
    boolean result = interceptor.preHandle(request, response, handlerMethod);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("should store resolved version as request attribute")
  void shouldStoreVersionAsRequestAttribute() throws Exception {
    when(request.getHeader("API-Version")).thenReturn("1");
    doReturn(VersionedController.class).when(handlerMethod).getBeanType();
    when(handlerMethod.getMethodAnnotation(ApiVersion.class)).thenReturn(null);

    interceptor.preHandle(request, response, handlerMethod);

    verify(request).setAttribute("apiVersion", 1);
  }

  @Test
  @DisplayName("method-level @ApiVersion should override class-level")
  void methodLevelOverridesClassLevel() throws Exception {
    when(request.getHeader("API-Version")).thenReturn(null);
    when(request.getRequestURI()).thenReturn("/v1/override");

    // Simulate a method with its own @ApiVersion annotation
    Method method = OverrideController.class.getDeclaredMethods()[0];
    ApiVersion methodAnnotation = method.getAnnotation(ApiVersion.class);
    when(handlerMethod.getMethodAnnotation(ApiVersion.class)).thenReturn(methodAnnotation);
    doReturn(OverrideController.class).when(handlerMethod).getBeanType();

    boolean result = interceptor.preHandle(request, response, handlerMethod);

    assertThat(result).isTrue();
    // Method annotation has value=2
    verify(response).setHeader("API-Version", "2");
  }

  // ---------------------------------------------------------------------------
  // Test fixtures — controllers with @ApiVersion annotations
  // ---------------------------------------------------------------------------

  @ApiVersion(1)
  @SuppressWarnings("unused")
  static class VersionedController {
    public void handle() {
    }
  }

  @ApiVersion(value = 1, deprecated = true, sunset = "2099-12-31", successor = "/v2/legacy")
  @SuppressWarnings("unused")
  static class DeprecatedController {
    public void handle() {
    }
  }

  @ApiVersion(value = 1, deprecated = true, sunset = "2020-01-01", successor = "/v2/expired")
  @SuppressWarnings("unused")
  static class ExpiredController {
    public void handle() {
    }
  }

  @ApiVersion(1)
  @SuppressWarnings("unused")
  static class OverrideController {
    @ApiVersion(2)
    public void handle() {
    }
  }
}
