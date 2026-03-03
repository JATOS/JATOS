package exceptions.gui;

import models.gui.ApiEnvelope.ErrorCode;

public class ImportExportException extends JatosException {

	public ImportExportException(String message) {
		super(message, ErrorCode.IMPORT_EXPORT_ERROR);
	}

}
