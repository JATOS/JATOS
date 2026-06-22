package exceptions.common;

import general.common.ApiEnvelope.ErrorCode;

/**
 * Runtime Exception for errors during import/export operations.
 */
public class ImportExportException extends JatosException {

	public ImportExportException(String message) {
		super(message, ErrorCode.IMPORT_EXPORT_ERROR);
	}

}
