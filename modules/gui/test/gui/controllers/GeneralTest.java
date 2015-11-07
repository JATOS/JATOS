package gui.controllers;

import static org.fest.assertions.Assertions.assertThat;
import general.common.Common;
import gui.AbstractTest;

import org.junit.Test;

import play.twirl.api.Html;

/**
 * @author Kristian Lange
 */
public class GeneralTest extends AbstractTest {

	@Override
	public void before() throws Exception {
		// Nothing additional to AbstractGuiTest to to do before test
	}

	@Override
	public void after() throws Exception {
		// Nothing additional to AbstractGuiTest to to do after test
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void renderTemplate() {
		Html html = views.html.publix.confirmationCode.render("test");
		assertThat(html.contentType()).isEqualTo("text/html");
		assertThat(html.body()).contains("Confirmation code:");
	}

	@Test
	public void databaseH2Mem() {
		assertThat(Common.IN_MEMORY_DB);
	}

}
