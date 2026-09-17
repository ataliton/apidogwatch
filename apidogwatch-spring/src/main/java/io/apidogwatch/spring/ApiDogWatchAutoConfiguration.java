package io.apidogwatch.spring;

import io.apidogwatch.ApiDogWatchConfig;
import io.apidogwatch.ApiDogWatchEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "apidogwatch", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ApiDogWatchProperties.class)
public class ApiDogWatchAutoConfiguration {

    @Bean
    public ApiDogWatchEngine apiDogWatchEngine(ApiDogWatchProperties properties,
                                               ResourceLoader resourceLoader,
                                               Environment environment) {
        String openApiLocation = OpenApiLocationResolver.resolve(properties, resourceLoader, environment);
        ApiDogWatchConfig config = ApiDogWatchConfig.builder()
                .enabled(properties.isEnabled())
                .openApiLocation(openApiLocation)
                .uiPathPrefix(properties.getPath())
                .maxBodyChars(properties.getMaxBodyChars())
                .excludedPathPrefixes(properties.getExcludePaths())
                .build();
        return ApiDogWatchEngine.create(openApiLocation, config);
    }

    @Bean
    public ApiDogWatchInterceptor apiDogWatchInterceptor(ApiDogWatchEngine engine) {
        return new ApiDogWatchInterceptor(engine);
    }

    @Bean
    public ApiDogWatchContentFilter apiDogWatchContentFilter(ApiDogWatchEngine engine) {
        return new ApiDogWatchContentFilter(engine);
    }

    @Bean
    public FilterRegistrationBean<ApiDogWatchContentFilter> apiDogWatchFilterRegistration(
            ApiDogWatchContentFilter filter) {
        FilterRegistrationBean<ApiDogWatchContentFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 50);
        registration.setName("apiDogWatchContentFilter");
        return registration;
    }

    @Bean
    public ApiDogWatchDashboardController apiDogWatchDashboardController(ApiDogWatchEngine engine) {
        return new ApiDogWatchDashboardController(engine);
    }

    @Bean
    public WebMvcConfigurer apiDogWatchWebMvcConfigurer(ApiDogWatchInterceptor interceptor,
                                                        ApiDogWatchProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/**")
                        .excludePathPatterns(properties.getPath() + "/**");
            }
        };
    }
}
