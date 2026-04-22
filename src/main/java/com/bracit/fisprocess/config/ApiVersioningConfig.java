package com.bracit.fisprocess.config;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link ApiVersionInterceptor} and exposes supported-version
 * metadata as a Spring bean.
 *
 * <p>The interceptor is applied to all {@code /**} paths. Non-MVC handlers
 * (actuator, static assets, error pages) are gracefully skipped inside the
 * interceptor itself.
 */
@Configuration
public class ApiVersioningConfig implements WebMvcConfigurer {

  private final ApiVersionInterceptor apiVersionInterceptor;
  private final ResponseCachingInterceptor responseCachingInterceptor;

  /**
   * Creates the interceptor with values driven by {@code application.yml}.
   *
   * @param supportedVersions set of supported major versions (default: {@code 1})
   * @param currentVersion    the latest active major version (default: {@code 1})
   */
  public ApiVersioningConfig(
      @Value("${fis.api.version.supported:#{T(java.util.Set).of(1)}}") Set<Integer> supportedVersions,
      @Value("${fis.api.version.current:1}") int currentVersion,
      ResponseCachingInterceptor responseCachingInterceptor) {
    this.apiVersionInterceptor = new ApiVersionInterceptor(supportedVersions, currentVersion);
    this.responseCachingInterceptor = responseCachingInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(apiVersionInterceptor)
        .addPathPatterns("/**")
        .order(0);
    registry.addInterceptor(responseCachingInterceptor)
        .addPathPatterns("/v1/**")
        .order(1);
  }

  /**
   * Exposes the interceptor as a bean so tests can inject and assert on it
   * directly without spinning up the full MVC stack.
   */
  @Bean
  public ApiVersionInterceptor apiVersionInterceptor() {
    return apiVersionInterceptor;
  }
}
