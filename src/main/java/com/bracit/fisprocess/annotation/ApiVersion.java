package com.bracit.fisprocess.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.jspecify.annotations.Nullable;

/**
 * Marks a controller (or specific handler method) with its API version.
 *
 * <p>The {@link #value()} determines the logical version of the endpoint. The
 * {@link ApiVersionInterceptor} reads this annotation at request time and
 * attaches deprecation/sunset headers when {@link #deprecated()} is {@code true}.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @RestController
 * @RequestMapping("/v1/accounts")
 * @ApiVersion(1)
 * public class AccountController { ... }
 *
 * // Deprecated endpoint — will be retired after the sunset date
 * @PostMapping("/legacy-export")
 * @ApiVersion(value = 1, deprecated = true, sunset = "2027-06-30", successor = "/v2/accounts/export")
 * public ResponseEntity<Void> legacyExport() { ... }
 * }</pre>
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li><b>Active</b> — normal operation, no extra headers.</li>
 *   <li><b>Deprecated</b> — response includes {@code Deprecation: true},
 *       {@code Sunset: <date>}, and {@code Link: <successor>} headers.</li>
 *   <li><b>Retired</b> — interceptor returns HTTP 410 Gone after sunset date
 *       has passed.</li>
 * </ul>
 *
 * @see com.bracit.fisprocess.config.ApiVersionInterceptor
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {

  /**
   * The API version number (e.g. {@code 1} for {@code /v1/}).
   *
   * @return positive version number
   */
  int value();

  /**
   * Whether this endpoint is deprecated.
   *
   * <p>When {@code true}, every response will carry:
   * <ul>
   *   <li>{@code Deprecation: true}</li>
   *   <li>{@code Sunset: <sunset date in RFC 1123>}</li>
   *   <li>{@code Link: <successor>; rel="successor-version"}</li>
   * </ul>
   *
   * @return {@code true} if the endpoint is deprecated
   */
  boolean deprecated() default false;

  /**
   * The date after which this endpoint will return HTTP 410 Gone.
   *
   * <p>Format: {@code yyyy-MM-dd}. Must be set when {@link #deprecated()} is
   * {@code true}. Ignored otherwise.
   *
   * @return sunset date string, or empty string if not applicable
   */
  String sunset() default "";

  /**
   * The path of the successor endpoint that consumers should migrate to.
   *
   * <p>Used in the {@code Link} response header when deprecated.
   *
   * @return successor endpoint path, or empty string if none
   */
  String successor() default "";
}
