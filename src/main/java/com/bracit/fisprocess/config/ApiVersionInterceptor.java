package com.bracit.fisprocess.config;

import com.bracit.fisprocess.annotation.ApiVersion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/**
 * Intercepts every MVC request to enforce API versioning policy.
 *
 * <p>Version resolution order:
 * <ol>
 *   <li>{@code API-Version} request header (highest priority)</li>
 *   <li>URL path segment {@code /v{number}/} (fallback)</li>
 * </ol>
 *
 * <p>If the resolved version is not in the {@code supported-versions} set the
 * interceptor returns HTTP 406 Not Acceptable.
 *
 * <p>When the handler carries a deprecated {@link ApiVersion} annotation the
 * response is enriched with {@code Deprecation}, {@code Sunset}, and {@code Link}
 * headers. Requests to endpoints past their sunset date receive HTTP 410 Gone.
 *
 * @see ApiVersion
 * @see ApiVersioningConfig
 */
@Slf4j
public class ApiVersionInterceptor implements HandlerInterceptor {

  private static final String HEADER_API_VERSION = "API-Version";
  private static final String HEADER_DEPRECATION = "Deprecation";
  private static final String HEADER_SUNSET = "Sunset";
  private static final String HEADER_LINK = "Link";
  private static final String URL_VERSION_PATTERN = "^/v(\\d+)/.*$";

  private final Set<Integer> supportedVersions;
  private final int currentVersion;

  /**
   * @param supportedVersions set of supported major version numbers
   * @param currentVersion    the latest active major version
   */
  public ApiVersionInterceptor(
      @Value("${fis.api.version.supported:1}") Set<Integer> supportedVersions,
      @Value("${fis.api.version.current:1}") int currentVersion) {
    this.supportedVersions = supportedVersions.isEmpty() ? Set.of(1) : supportedVersions;
    this.currentVersion = currentVersion;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler) throws Exception {

    if (!(handler instanceof HandlerMethod handlerMethod)) {
      // Static resources, error endpoints, etc. — skip version checks.
      return true;
    }

    Integer resolvedVersion = resolveVersion(request, handlerMethod);
    if (resolvedVersion == null) {
      // No version information available — let the request through as-is
      // (e.g. actuator endpoints, swagger, etc.)
      return true;
    }

    // Reject unsupported versions with 406
    if (!supportedVersions.contains(resolvedVersion)) {
      response.setStatus(HttpStatus.NOT_ACCEPTABLE.value());
      response.setContentType("application/problem+json");
      response.getWriter().write(
          "{\"status\":406,\"error\":\"Not Acceptable\","
              + "\"message\":\"API version " + resolvedVersion
              + " is not supported. Supported versions: " + supportedVersions + "\"}");
      return false;
    }

    // Check for deprecated / retired endpoints
    ApiVersion versionAnnotation = resolveAnnotation(handlerMethod);
    if (versionAnnotation != null && versionAnnotation.deprecated()) {
      String sunsetDate = versionAnnotation.sunset();

      // If sunset has passed, return 410 Gone
      if (sunsetDate != null && !sunsetDate.isBlank() && isPastSunset(sunsetDate)) {
        response.setStatus(HttpStatus.GONE.value());
        response.setContentType("application/problem+json");
        response.getWriter().write(
            "{\"status\":410,\"error\":\"Gone\","
                + "\"message\":\"This API version has been retired on " + sunsetDate
                + ". Migrate to " + versionAnnotation.successor() + "\"}");
        return false;
      }

      // Add deprecation headers
      addDeprecationHeaders(response, sunsetDate, versionAnnotation.successor());
    }

    // Always echo the resolved version back
    response.setHeader(HEADER_API_VERSION, String.valueOf(resolvedVersion));

    // Store version as a request attribute for downstream filters
    request.setAttribute("apiVersion", resolvedVersion);

    return true;
  }

  // ---------------------------------------------------------------------------
  // Resolution helpers
  // ---------------------------------------------------------------------------

  /**
   * Resolves the API version from the request header first, then URL path.
   */
  private @Nullable Integer resolveVersion(
      HttpServletRequest request, HandlerMethod handlerMethod) {

    // 1. Header takes precedence
    String headerVersion = request.getHeader(HEADER_API_VERSION);
    if (headerVersion != null && !headerVersion.isBlank()) {
      try {
        return Integer.parseInt(headerVersion.trim());
      } catch (NumberFormatException e) {
        log.warn("Malformed API-Version header: {}", headerVersion);
        return null;
      }
    }

    // 2. Fall back to @ApiVersion annotation on the handler
    ApiVersion annotation = resolveAnnotation(handlerMethod);
    if (annotation != null) {
      return annotation.value();
    }

    // 3. Parse from URL path: /v{number}/...
    String path = request.getRequestURI();
    return parseVersionFromPath(path);
  }

  /**
   * Extracts version from URI path pattern {@code /v{number}/...}.
   */
  private @Nullable Integer parseVersionFromPath(String uri) {
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile(URL_VERSION_PATTERN).matcher(uri);
    if (matcher.find()) {
      return Integer.parseInt(matcher.group(1));
    }
    return null;
  }

  /**
   * Returns the {@link ApiVersion} annotation from method first, then class.
   */
  private @Nullable ApiVersion resolveAnnotation(HandlerMethod handlerMethod) {
    ApiVersion methodAnnotation = handlerMethod.getMethodAnnotation(ApiVersion.class);
    if (methodAnnotation != null) {
      return methodAnnotation;
    }
    return handlerMethod.getBeanType().getAnnotation(ApiVersion.class);
  }

  // ---------------------------------------------------------------------------
  // Deprecation header helpers
  // ---------------------------------------------------------------------------

  private void addDeprecationHeaders(
      HttpServletResponse response,
      @Nullable String sunsetDate,
      @Nullable String successor) {

    response.setHeader(HEADER_DEPRECATION, "true");

    if (sunsetDate != null && !sunsetDate.isBlank()) {
      try {
        LocalDate date = LocalDate.parse(sunsetDate, DateTimeFormatter.ISO_LOCAL_DATE);
        String rfc1123 =
            DateTimeFormatter.RFC_1123_DATE_TIME.format(date.atStartOfDay(ZoneOffset.UTC));
        response.setHeader(HEADER_SUNSET, rfc1123);
      } catch (DateTimeParseException e) {
        log.warn("Invalid sunset date format: {}", sunsetDate);
      }
    }

    if (successor != null && !successor.isBlank()) {
      response.setHeader(HEADER_LINK, "<" + successor + ">; rel=\"successor-version\"");
    }
  }

  private boolean isPastSunset(String sunsetDate) {
    try {
      LocalDate sunset = LocalDate.parse(sunsetDate, DateTimeFormatter.ISO_LOCAL_DATE);
      return LocalDate.now(ZoneOffset.UTC).isAfter(sunset);
    } catch (DateTimeParseException e) {
      log.warn("Invalid sunset date format: {}", sunsetDate);
      return false;
    }
  }
}
