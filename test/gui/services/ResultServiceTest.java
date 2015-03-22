package gui.services;

import static org.fest.assertions.Assertions.assertThat;
import exceptions.BadRequestException;
import exceptions.NotFoundException;
import gui.AbstractGuiTest;

import java.util.List;

import org.fest.assertions.Fail;
import org.junit.Test;

import services.gui.MessagesStrings;
import services.gui.ResultService;

import common.Global;

/**
 * Tests ResultService
 * 
 * @author Kristian Lange
 */
public class ResultServiceTest extends AbstractGuiTest {

	private ResultService resultService;

	@Override
	public void before() throws Exception {
		resultService = Global.INJECTOR.getInstance(ResultService.class);
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
	public void checkExtractResultIds() {
		List<Long> resultIdList = null;
		try {
			resultIdList = resultService.extractResultIds("1,2,3");
		} catch (BadRequestException e) {
			Fail.fail();
		}
		checkForProperResultIdList(resultIdList);

		try {
			resultIdList = resultService
					.extractResultIds(" , ,, 1 ,2    ,  3    , ");
		} catch (BadRequestException e) {
			Fail.fail();
		}
		checkForProperResultIdList(resultIdList);

		try {
			resultIdList = resultService.extractResultIds("1,b,3");
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.resultIdMalformed("b"));
		}

		try {
			resultIdList = resultService.extractResultIds("");
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.NO_RESULTS_SELECTED);
		}
	}

	private void checkForProperResultIdList(List<Long> resultIdList) {
		assertThat(resultIdList.size() == 3);
		assertThat(resultIdList.contains(1l));
		assertThat(resultIdList.contains(2l));
		assertThat(resultIdList.contains(3l));
	}

	@Test
	public void checkGetAllComponentResults() throws BadRequestException {
		List<Long> idList = resultService.extractResultIds("1,2,3");
		try {
			resultService.getAllComponentResults(idList);
			Fail.fail();
		} catch (NotFoundException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.componentResultNotExist(1l));
		}
	}

}
