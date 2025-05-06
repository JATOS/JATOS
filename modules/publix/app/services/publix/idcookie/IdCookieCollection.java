package services.publix.idcookie;

import general.common.Common;
import services.publix.PublixErrorMessages;
import services.publix.idcookie.exception.IdCookieAlreadyExistsException;
import services.publix.idcookie.exception.IdCookieCollectionFullException;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wrapper around a collection of JATOS ID cookies. Adds some useful methods. The
 * number of ID cookies is limited by the jatos.idCookies.limit value in the jatos.conf.
 *
 * @author Kristian Lange
 */
public class IdCookieCollection {

    /**
     * Internally we use a map to store the cookies: Each IdCookieModel has a unique
     * study result ID. We map the study result ID to the actual cookie.
     */
    private final HashMap<Long, IdCookieModel> idCookieMap = new HashMap<>();

    protected boolean isFull() {
        return size() >= Common.getIdCookiesLimit();
    }

    protected int size() {
        return idCookieMap.size();
    }

    /**
     * Stores the given ID cookie. If an IdCookieModel with the same study result ID
     * is already stored an IdCookieAlreadyExistsException is thrown. If the max
     * number of cookies is reached an IdCookieCollectionFullException is
     * thrown.
     */
    protected IdCookieModel add(IdCookieModel idCookie)
            throws IdCookieAlreadyExistsException {
        if (idCookieMap.containsKey(idCookie.getStudyResultId())) {
            throw new IdCookieAlreadyExistsException(PublixErrorMessages
                    .idCookieExistsAlready(idCookie.getStudyResultId()));
        }
        return idCookieMap.put(idCookie.getStudyResultId(), idCookie);
    }

    /**
     * Stores the given IdCookieModel. If an ID cookie with the same study result ID
     * is already stored it gets overwritten. If the max number of cookies is
     * reached an IdCookieCollectionFullException is thrown.
     */
    protected IdCookieModel put(IdCookieModel idCookie)
            throws IdCookieCollectionFullException {
        if (isFull() && !idCookieMap.containsKey(idCookie.getStudyResultId())) {
            throw new IdCookieCollectionFullException(
                    PublixErrorMessages.IDCOOKIE_COLLECTION_FULL);
        }
        return idCookieMap.put(idCookie.getStudyResultId(), idCookie);
    }

    protected IdCookieModel remove(IdCookieModel idCookie) {
        return idCookieMap.remove(idCookie.getStudyResultId());
    }

    protected Collection<IdCookieModel> getAll() {
        return idCookieMap.values();
    }

    /**
     * Returns the ID cookie's index, a number from 0 to the limit set in jatos.conf.
     * It iterates through the ID cookies and returns the first index that isn't used.
     * Index refers here to the index of the ID cookie which is the suffix of its name.
     * If this IdCookieCollection is full a IndexOutOfBoundsException will be thrown.
     */
    protected int getNextAvailableIdCookieIndex() {
        if (isFull()) {
            throw new IndexOutOfBoundsException(PublixErrorMessages.IDCOOKIE_COLLECTION_INDEX_OUT_OF_BOUND);
        }
        List<Integer> existingIndices = idCookieMap.values().stream()
                .map(IdCookieModel::getIndex).collect(Collectors.toList());
        for (int i = 0; i < Common.getIdCookiesLimit(); i++) {
            if (!existingIndices.contains(i)) {
                return i;
            }
        }
        throw new IndexOutOfBoundsException(PublixErrorMessages.IDCOOKIE_COLLECTION_INDEX_OUT_OF_BOUND);
    }

    /**
     * Returns the ID cookie to which the specified study result ID is mapped, or
     * null if nothing maps to the ID.
     */
    protected IdCookieModel findWithStudyResultId(long studyResultId) {
        return idCookieMap.get(studyResultId);
    }

    @Override
    public String toString() {
        return idCookieMap.keySet().stream().map(Object::toString)
                .collect(Collectors.joining(", "));
    }

}
