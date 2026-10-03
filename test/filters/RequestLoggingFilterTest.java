package filters;

import general.common.Common;
import http.common.Http.Context;
import models.common.User;
import org.apache.pekko.stream.Materializer;
import org.junit.Test;
import org.mockito.MockedStatic;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import play.test.Helpers;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RequestLoggingFilterTest {
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void delayedLoggingBindsRequestIdentityAndRestoresCompletionThread() {
        Context previous = new Context(Helpers.fakeRequest("GET", "/unrelated").build());
        Http.Request request = Helpers.fakeRequest("GET", "/jatos/probe").build();
        Context context = new Context(request);
        User user = mock(User.class);
        when(user.getUsername()).thenAnswer(invocation -> {
            assertSame(context, Context.current());
            return "alice";
        });
        context.args().put(SIGNEDIN_USER, user);
        CompletableFuture<Result> gate = new CompletableFuture<>();
        try (MockedStatic<Common> common = mockStatic(Common.class)) {
            common.when(Common::getJatosUrlBasePath).thenReturn("/");
            common.when(Common::getLogsRequestCategories).thenReturn(List.of("all"));
            RequestLoggingFilter filter = new RequestLoggingFilter(mock(Materializer.class));
            var result = filter.apply(header -> gate, request.addAttr(Context.CONTEXT_TYPED_KEY, context));
            Context.setCurrent(previous);
            gate.complete(Results.ok());
            assertEquals(200, result.toCompletableFuture().join().status());
            verify(user).getUsername();
            assertSame(previous, Context.current());
        } finally {
            Context.clear();
        }
    }
}
