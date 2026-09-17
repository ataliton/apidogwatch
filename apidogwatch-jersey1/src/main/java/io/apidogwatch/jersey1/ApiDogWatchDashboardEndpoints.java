package io.apidogwatch.jersey1;

import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Ready-to-register dashboard at {@code /apidogwatch/ui}.
 * <p>
 * Tries to read the signed-in user locale reflectively ({@code idioma} / {@code getIdioma()})
 * so hosts like Systêxtil work without a compile-time dependency.
 */
@Path("/apidogwatch")
public class ApiDogWatchDashboardEndpoints extends ApiDogWatchDashboardResource {

    @Context
    private SecurityContext securityContext;

    @Override
    protected String resolveUserLanguage() {
        try {
            if (securityContext == null || securityContext.getUserPrincipal() == null) {
                return null;
            }
            Object principal = securityContext.getUserPrincipal();
            try {
                Field field = principal.getClass().getField("idioma");
                Object value = field.get(principal);
                return value == null ? null : String.valueOf(value);
            } catch (ReflectiveOperationException ignored) {
                // try getter
            }
            try {
                Method getter = principal.getClass().getMethod("getIdioma");
                Object value = getter.invoke(principal);
                return value == null ? null : String.valueOf(value);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        } catch (Exception ignored) {
            return null;
        }
    }
}
