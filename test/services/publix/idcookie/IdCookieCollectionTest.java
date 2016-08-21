package services.publix.idcookie;

import static org.fest.assertions.Assertions.assertThat;

import org.junit.Test;

import services.publix.idcookie.exception.IdCookieAlreadyExistsException;
import services.publix.idcookie.exception.IdCookieCollectionFullException;

/**
 * @author Kristian Lange (2016)
 */
public class IdCookieCollectionTest {

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
	public void checkIsFull() throws IdCookieCollectionFullException {
		IdCookieCollection idCookieCollection = new IdCookieCollection();
		assertThat(idCookieCollection.isFull()).isFalse();

		for (long i = 0; i < IdCookieCollection.MAX_ID_COOKIES; i++) {
			idCookieCollection.put(createIdCookie(i, (int) i));
		}
		assertThat(idCookieCollection.isFull()).isTrue();
	}

	@Test
	public void checkAdd() throws IdCookieAlreadyExistsException {
		IdCookieCollection idCookieCollection = new IdCookieCollection();
		idCookieCollection.add(createIdCookie(1l, 0));
		idCookieCollection.add(createIdCookie(2l, 1));
		assertThat(idCookieCollection.size()).isEqualTo(2);
	}

	@Test(expected = IdCookieAlreadyExistsException.class)
	public void checkAddIdCookieAlreadyExistsException()
			throws IdCookieAlreadyExistsException {
		IdCookieCollection idCookieCollection = new IdCookieCollection();
		idCookieCollection.add(createIdCookie(1l, 0));
		idCookieCollection.add(createIdCookie(1l, 1));
	}

	@Test
	public void checkPut() throws IdCookieCollectionFullException {
		IdCookieCollection idCookieCollection = new IdCookieCollection();
		assertThat(idCookieCollection.size()).isEqualTo(0);
		idCookieCollection.put(createIdCookie(1l, 0));
		assertThat(idCookieCollection.size()).isEqualTo(1);
		idCookieCollection.put(createIdCookie(2l, 1));
		assertThat(idCookieCollection.size()).isEqualTo(2);
		idCookieCollection.put(createIdCookie(2l, 2));
		assertThat(idCookieCollection.size()).isEqualTo(2);
	}
	
	@Test
	public void checkPutOverwrite() throws IdCookieCollectionFullException {
		IdCookieCollection idCookieCollection = new IdCookieCollection();
		assertThat(idCookieCollection.size()).isEqualTo(0);
		idCookieCollection.put(createIdCookie(1l, 0));
		assertThat(idCookieCollection.size()).isEqualTo(1);
		idCookieCollection.put(createIdCookie(1l, 0));
		assertThat(idCookieCollection.size()).isEqualTo(1);
	}

	@Test(expected = IdCookieCollectionFullException.class)
	public void checkPutWithIdCookieCollectionFullException()
			throws IdCookieCollectionFullException {
		IdCookieCollection idCookieCollection = new IdCookieCollection();
		for (long i = 0; i <= IdCookieCollection.MAX_ID_COOKIES; i++) {
			idCookieCollection.put(createIdCookie(i, (int) i));
		}
	}

	@Test
	public void checkRemove() throws IdCookieCollectionFullException {
		IdCookieCollection idCookieCollection = new IdCookieCollection();
		assertThat(idCookieCollection.size()).isEqualTo(0);
		IdCookie idCookie1 = createIdCookie(1l, 0);
		idCookieCollection.put(idCookie1);
		assertThat(idCookieCollection.size()).isEqualTo(1);
		idCookieCollection.remove(idCookie1);
		assertThat(idCookieCollection.size()).isEqualTo(0);
	}

	@Test
	public void checkGetAll() throws IdCookieCollectionFullException {
		IdCookieCollection idCookieCollection = new IdCookieCollection();
		for (long i = 0; i < IdCookieCollection.MAX_ID_COOKIES; i++) {
			idCookieCollection.put(createIdCookie(i, (int) i));
		}
		assertThat(idCookieCollection.getAll().size())
				.isEqualTo(IdCookieCollection.MAX_ID_COOKIES);
	}

	@Test
	public void checkGetNextAvailableIdCookieIndex()
			throws IdCookieCollectionFullException {
		IdCookieCollection idCookieCollection = new IdCookieCollection();

		for (long i = 0; i < IdCookieCollection.MAX_ID_COOKIES; i++) {
			assertThat(idCookieCollection.getNextAvailableIdCookieIndex())
					.isEqualTo((int) i);
			idCookieCollection.put(createIdCookie(i, (int) i));
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void checkGetNextAvailableIdCookieIndexIndexOutOfBoundsException()
			throws IdCookieCollectionFullException {
		IdCookieCollection idCookieCollection = new IdCookieCollection();

		// One more than allowed
		for (long i = 0; i <= IdCookieCollection.MAX_ID_COOKIES; i++) {
			assertThat(idCookieCollection.getNextAvailableIdCookieIndex())
					.isEqualTo((int) i);
			idCookieCollection.put(createIdCookie(i, (int) i));
		}
	}

	@Test
	public void checkFindWithStudyResultId()
			throws IdCookieCollectionFullException {
		IdCookieCollection idCookieCollection = new IdCookieCollection();
		assertThat(idCookieCollection.findWithStudyResultId(1l)).isNull();

		IdCookie idCookie = createIdCookie(1l, 0);
		idCookieCollection.put(idCookie);
		assertThat(idCookieCollection.findWithStudyResultId(1l))
				.isEqualTo(idCookie);
	}

}
