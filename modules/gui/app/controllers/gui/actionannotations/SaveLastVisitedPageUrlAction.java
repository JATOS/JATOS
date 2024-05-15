package controllers.gui.actionannotations;

import auth.gui.AuthService;
import daos.common.UserDao;
import general.common.Common;
import general.common.RequestScope;
import models.common.User;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Http.Request;
import play.mvc.Result;
import play.mvc.With;

import javax.inject.Inject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CompletionStage;

import static controllers.gui.actionannotations.SaveLastVisitedPageUrlAction.SaveLastVisitedPageUrl;

/**
 * Annotation definition for Play actions: logging of each action call, e.g.
 * 'gui_access - GET /jatos/19/run (admin)'
 *
 * @author Kristian Lange (2016)
 */
@SuppressWarnings("deprecation")
public class SaveLastVisitedPageUrlAction extends Action<SaveLastVisitedPageUrl> {

    @With(SaveLastVisitedPageUrlAction.class)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface SaveLastVisitedPageUrl {
    }

    private final UserDao userDao;

    @Inject
    SaveLastVisitedPageUrlAction(UserDao userDao) {
        this.userDao = userDao;
    }

    public CompletionStage<Result> call(Http.Context ctx) {
        final Request request = ctx.request();
        User signedinUser = (User) RequestScope.get(AuthService.SIGNEDIN_USER);
        String jatosUrlBasePathRegex = "^" + Common.getJatosUrlBasePath();
        String urlPathWithoutBase = request.path().replaceFirst(jatosUrlBasePathRegex, "");
        signedinUser.setLastVisitedPageUrl(urlPathWithoutBase);
        userDao.update(signedinUser);
        return delegate.call(ctx);
    }

}
