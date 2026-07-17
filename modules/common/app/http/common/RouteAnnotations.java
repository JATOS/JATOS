package http.common;

import play.mvc.Http;
import play.routing.Router;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public final class RouteAnnotations {

    /**
     * Retrieves an annotation of a specific type from the request's matched controller method, falling back to the
     * controller class annotation.
     *
     * Useful for filters and similar code that cannot use Play's Action configuration.
     */
    public static <A extends Annotation> Optional<A> get(Http.RequestHeader requestHeader, Class<A> annotationClass) {
        return requestHeader.attrs().getOptional(Router.Attrs.HANDLER_DEF).flatMap(handlerDef -> {
            try {
                Class<?> controllerClass = Class.forName(handlerDef.controller());
                A classAnn = controllerClass.getAnnotation(annotationClass);

                Optional<A> methodAnn = getMethodAnnotation(controllerClass, handlerDef.method(), annotationClass);

                return methodAnn.isPresent()
                        ? methodAnn
                        : Optional.ofNullable(classAnn);
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    private static <A extends Annotation> Optional<A> getMethodAnnotation(
            Class<?> controllerClass,
            String methodName,
            Class<A> annotationClass) {

        return Arrays.stream(controllerClass.getMethods())
                .filter(method -> method.getName().equals(methodName))
                .map(method -> method.getAnnotation(annotationClass))
                .filter(Objects::nonNull)
                .findFirst();
    }

}
