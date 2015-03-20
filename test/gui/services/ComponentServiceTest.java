package gui.services;

import static org.fest.assertions.Assertions.assertThat;
import gui.AbstractGuiTest;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import models.ComponentModel;
import models.StudyModel;

import org.junit.Test;

import services.gui.ComponentService;

import common.Global;

/**
 * Tests ComponentService
 * 
 * @author Kristian Lange
 */
public class ComponentServiceTest extends AbstractGuiTest {

	private ComponentService componentService;

	@Override
	public void before() throws Exception {
		componentService = Global.INJECTOR.getInstance(ComponentService.class);
	}

	@Override
	public void after() throws Exception {
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void checkUpdateComponentAfterEdit()
			throws NoSuchAlgorithmException, IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		ComponentModel component = study.getFirstComponent();
		ComponentModel clone = componentService.clone(component);
		clone.setActive(false);
		clone.setComments("Changed comments");
		clone.setHtmlFilePath("changed path");
		clone.setJsonData("{}");
		clone.setReloadable(false);
		clone.setTitle("Changed title");
		clone.setUuid("UUID should never be changed");
		clone.setStudy(null);
		clone.setId(0l);

		componentService.updateComponentAfterEdit(component, clone);
		ComponentModel updatedComponent = componentDao.findByUuid(
				component.getUuid(), study);

		// Unchanged stuff
		assertThat(updatedComponent.isActive() == component.isActive())
				.isTrue();
		assertThat(updatedComponent.getId().equals(component.getId())).isTrue();
		// TODO study always null: weird!
		// assertThat(updatedComponent.getStudy().equals(study)).isTrue();
		assertThat(updatedComponent.getUuid().equals(component.getUuid()))
				.isTrue();

		// Changed stuff
		assertThat(updatedComponent.getComments())
				.isEqualTo(clone.getComments());
		assertThat(updatedComponent.getHtmlFilePath())
				.isEqualTo(clone.getHtmlFilePath());
		assertThat(updatedComponent.getJsonData()).isEqualTo("{ }");
		assertThat(updatedComponent.getTitle()).isEqualTo(clone.getTitle());
		assertThat(updatedComponent.isReloadable() == clone.isReloadable()).isTrue();

		// Clean-up
		removeStudy(study);
	}

}
