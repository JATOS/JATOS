package exceptions.gui;

import general.common.ApiEnvelope.ErrorCode;

public class ImportExportException extends JatosException {

	public ImportExportException(String message) {
		super(message, ErrorCode.IMPORT_EXPORT_ERROR);
	}

}
