package services.publix.idcookie.exceptions;

import exceptions.common.JatosException;

import java.io.Serial;

/**
 * Is thrown if the limit of JATOS ID cookies is reached.
 */
public class IdCookieCollectionFullException extends JatosException {

    public IdCookieCollectionFullException(String message) {
        super(message);
    }

}
