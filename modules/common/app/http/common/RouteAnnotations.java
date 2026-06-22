package http.common;

import play.mvc.Http;
import play.routing.Router;
import scala.jdk.CollectionConverters;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

public final class RouteAnnotations {

    /**
     * Retrieves an annotation of a specific type from the request's matched controller
     * method, falling back to the controller class annotation.
     *
     * Useful for filters and similar code that cannot use Play's Action configuration.
     */
    public static <A extends Annotation> Optional<A> get(Http.RequestHeader requestHeader, Class<A> annotationClass) {
        return requestHeader.attrs().getOptional(Router.Attrs.HANDLER_DEF).flatMap(handlerDef -> {
            try {
                Class<?> controllerClass = Class.forName(handlerDef.controller());
                A classAnn = controllerClass.getAnnotation(annotationClass);

                List<Class<?>> paramTypes = CollectionConverters.SeqHasAsJava(handlerDef.parameterTypes()).asJava();
                Method method = controllerClass.getMethod(handlerDef.method(), paramTypes.toArray(new Class[0]));
                A methodAnn = method.getAnnotation(annotationClass);

                return Optional.ofNullable(methodAnn != null ? methodAnn : classAnn);
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }
}
