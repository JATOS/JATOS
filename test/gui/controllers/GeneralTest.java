package gui.controllers;
import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.contentType;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;

import javax.persistence.EntityManager;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import play.db.jpa.JPA;
import play.db.jpa.JPAPlugin;
import play.mvc.Content;
import play.test.FakeApplication;
import play.test.Helpers;
import play.test.WithApplication;
import scala.Option;

import common.Common;

/**
 * Testing controller.Studies
 * 
 * @author Kristian Lange
 */
public class GeneralTest extends WithApplication {

	private static FakeApplication app;
	private static EntityManager em;
	
	@BeforeClass
	public static void setUp() throws UnsupportedEncodingException,
			NoSuchAlgorithmException {
		app = Helpers.fakeApplication();
		Helpers.start(app);
		
		Option<JPAPlugin> jpaPlugin = app.getWrappedApplication().plugin(
				JPAPlugin.class);
		em = jpaPlugin.get().em("default");
		JPA.bindForCurrentThread(em);
	}

	@AfterClass
	public static void tearDown() {
		em.close();
		JPA.bindForCurrentThread(null);
		Helpers.stop(app);
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void renderTemplate() {
		Content html = views.html.publix.confirmationCode.render("test");
		assertThat(contentType(html)).isEqualTo("text/html");
		assertThat(contentAsString(html)).contains("Confirmation code:");
	}

	@Test
	public void databaseH2Mem() {
		assertThat(Common.IN_MEMORY_DB);
	}

}
