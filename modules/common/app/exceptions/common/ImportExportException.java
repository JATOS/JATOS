package exceptions.common;

import general.common.ApiEnvelope.ErrorCode;

import java.io.Serial;

/**
 * Runtime Exception for errors during import/export operations.
 */
public class ImportExportException extends JatosException {

	@Serial
	private static final long serialVersionUID = 1L;

	public ImportExportException(String message) {
		super(message, ErrorCode.IMPORT_EXPORT_ERROR);
	}

}
