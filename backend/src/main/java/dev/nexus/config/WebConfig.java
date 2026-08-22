package dev.nexus.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the API and the built frontend from one origin.
 *
 * <p>Two origins would put the SPA on a different site from the API, and the refresh cookie
 * is SameSite=Strict — the browser would withhold it and every session would end at the
 * first reload. Sharing an origin also means no CORS in production.
 *
 * <p>The API is prefixed here rather than through {@code server.servlet.context-path},
 * which would push the static files under /api as well.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String API_PREFIX = "/api";
    private static final String STATIC_LOCATION = "classpath:/static/";
    private static final String SPA_ENTRY_POINT = "static/index.html";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forAnnotation(RestController.class));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_LOCATION)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {

                    /**
                     * Serves a real file where one exists and the SPA entry point otherwise, so
                     * a deep link like /settings/steam/callback survives a full page load.
                     */
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        // Unknown API paths must still 404. Falling back to index.html here
                        // would answer every mistyped endpoint with 200 and a page of HTML.
                        if (path.startsWith("api/")) {
                            return null;
                        }

                        Resource requested = location.createRelative(path);
                        return requested.exists() && requested.isReadable()
                                ? requested
                                : new ClassPathResource(SPA_ENTRY_POINT);
                    }
                });
    }
}
