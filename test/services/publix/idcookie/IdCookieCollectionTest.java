package services.publix.idcookie;

import static org.fest.assertions.Assertions.assertThat;

import org.junit.Test;

import general.AbstractTest;
import services.publix.idcookie.IdCookieCollection;
import services.publix.idcookie.exception.IdCookieAlreadyExistsException;

/**
 * @author Kristian Lange (2016)
 */
public class IdCookieCollectionTest extends AbstractTest {

	@Override
	public void before() throws Exception {
	}

	@Override
	public void after() throws Exception {
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	private IdCookie createIdCookie(long studyResultId, int index) {
		IdCookie idCookie = new IdCookie();
		idCookie.setStudyResultId(studyResultId);
		idCookie.setIndex(index);
		return idCookie;
	}

	@Test
	public void checkIsFull() {
		IdCookieCollection idCookieCollection = new IdCookieCollection();
		assertThat(!idCookieCollection.isFull());

		for (long i = 0; i <= IdCookieCollection.MAX_ID_COOKIES; i++) {
			idCookieCollection.put(createIdCookie(i, (int) i));
		}
		assertThat(idCookieCollection.isFull());
	}

	@Test
	public void checkAdd() throws IdCookieAlreadyExistsException {
		IdCookieCollection idCookieCollection = new IdCookieCollection();
		idCookieCollection.add(createIdCookie(1l, 0));
		idCookieCollection.add(createIdCookie(2l, 1));
	}

	@Test(expected = IdCookieAlreadyExistsException.class)
	public void checkAddIdCookieAlreadyExistsException()
			throws IdCookieAlreadyExistsException {
		IdCookieCollection idCookieCollection = new IdCookieCollection();
		idCookieCollection.add(createIdCookie(1l, 0));
		idCookieCollection.add(createIdCookie(1l, 1));
	}

	@Test
	public void checkPut() {
		IdCookieCollection idCookieCollection = new IdCookieCollection();
		assertThat(idCookieCollection.size() == 0);
		idCookieCollection.put(createIdCookie(1l, 0));
		assertThat(idCookieCollection.size() == 1);
		idCookieCollection.put(createIdCookie(2l, 1));
		assertThat(idCookieCollection.size() == 2);
		idCookieCollection.put(createIdCookie(2l, 2));
		assertThat(idCookieCollection.size() == 2);
	}

	@Test
	public void checkRemove() {
		IdCookieCollection idCookieCollection = new IdCookieCollection();
		assertThat(idCookieCollection.size() == 0);
		IdCookie idCookie1 = createIdCookie(1l, 0);
		idCookieCollection.put(idCookie1);
		assertThat(idCookieCollection.size() == 1);
		idCookieCollection.remove(idCookie1);
		assertThat(idCookieCollection.size() == 0);
	}

	@Test
	public void checkGetAll() {
		IdCookieCollection idCookieCollection = new IdCookieCollection();
		for (long i = 0; i <= IdCookieCollection.MAX_ID_COOKIES; i++) {
			idCookieCollection.put(createIdCookie(i, (int) i));
		}
		assertThat(idCookieCollection.getAll()
				.size() == IdCookieCollection.MAX_ID_COOKIES);
	}

	@Test
	public void checkGetNextAvailableIdCookieIndex() {
		IdCookieCollection idCookieCollection = new IdCookieCollection();

		for (long i = 0; i < IdCookieCollection.MAX_ID_COOKIES; i++) {
			assertThat(idCookieCollection.getNextAvailableIdCookieIndex() == i);
			idCookieCollection.put(createIdCookie(i, (int) i));
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void checkGetNextAvailableIdCookieIndexIndexOutOfBoundsException() {
		IdCookieCollection idCookieCollection = new IdCookieCollection();

		// One more than allowed
		for (long i = 0; i <= IdCookieCollection.MAX_ID_COOKIES; i++) {
			assertThat(idCookieCollection.getNextAvailableIdCookieIndex() == i);
			idCookieCollection.put(createIdCookie(i, (int) i));
		}
	}

	@Test
	public void checkFindWithStudyResultId() {
		IdCookieCollection idCookieCollection = new IdCookieCollection();
		assertThat(idCookieCollection.findWithStudyResultId(1l) == null);
		
		IdCookie idCookie = createIdCookie(1l, 0);
		idCookieCollection.put(idCookie);
		assertThat(
				idCookieCollection.findWithStudyResultId(1l).equals(idCookie));
	}

}
