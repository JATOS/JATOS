package services.gui;

import static org.fest.assertions.Assertions.assertThat;

import org.fest.assertions.Fail;
import org.junit.Test;

import utils.common.HashUtils;

/**
 * Tests HashUtils
 * 
 * @author Kristian Lange
 */
public class HashUtilsTest {

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void checkGetHashMDFive() {
		String hash = null;
		try {
			hash = HashUtils.getHashMDFive("bla");
		} catch (RuntimeException e) {
			Fail.fail();
		}
		assertThat(hash).isEqualTo("128ecf542a35ac5270a87dc740918404");
	}

}
