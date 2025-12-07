package controllers.gui.actionannotations;

import auth.gui.AuthService;
import daos.common.UserDao;
import general.common.Common;
import models.common.User;
import play.mvc.Action;
import play.mvc.Http;
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
 * Annotation definition for Play actions: save the last visited page URL in the database
 *
 * @author Kristian Lange
 */
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

    public CompletionStage<Result> call(Http.Request request) {
        User signedinUser = request.attrs().get(AuthService.SIGNEDIN_USER);
        String jatosUrlBasePathRegex = "^" + Common.getJatosUrlBasePath();
        String urlPathWithoutBase = request.path().replaceFirst(jatosUrlBasePathRegex, "");
        signedinUser.setLastVisitedPageUrl(urlPathWithoutBase);
        userDao.update(signedinUser);
        return delegate.call(request);
    }

}
