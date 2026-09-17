package io.apidogwatch.jersey1;

import com.sun.jersey.api.model.AbstractMethod;
import com.sun.jersey.spi.container.ResourceFilter;
import com.sun.jersey.spi.container.ResourceFilterFactory;

import javax.ws.rs.ext.Provider;
import java.util.Collections;
import java.util.List;

/**
 * Applies {@link ApiDogWatchResourceFilter} to every resource method.
 * Register via {@link ApiDogWatchJersey#singletons()} — no per-class {@code @ResourceFilters}.
 */
@Provider
public final class ApiDogWatchResourceFilterFactory implements ResourceFilterFactory {

    private static final List<ResourceFilter> FILTERS =
            Collections.singletonList(new ApiDogWatchResourceFilter());

    @Override
    public List<ResourceFilter> create(AbstractMethod am) {
        if (!ApiDogWatchJersey.isEnabled()) {
            return Collections.emptyList();
        }
        return FILTERS;
    }
}
