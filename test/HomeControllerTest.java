import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.callAction;
import static play.test.Helpers.charset;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.contentType;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.status;

import org.junit.Test;

import play.mvc.Result;
import services.Breadcrumbs;
import controllers.Users;

/**
 * Testing actions of controller.Home.
 * 
 * @author Kristian Lange
 */
public class HomeControllerTest extends AControllerTest {

	@Test
	public void callHome() throws Exception {
		Result result = callAction(controllers.routes.ref.Home.home(),
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(charset(result)).isEqualTo("utf-8");
		assertThat(contentType(result)).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains(Breadcrumbs.HOME);
	}

}
