package controllers.publix;

import models.UserModel;
import models.workers.MAWorker;

import com.google.common.net.MediaType;

import exceptions.BadRequestPublixException;

/**
 * Special Retriever for studies or components started via MechArg's UI.
 * 
 * @author madsen
 */
public class MARetriever extends Retriever<MAWorker> {

	private MAErrorMessages errorMessages;

	public MARetriever(MAErrorMessages errorMessages) {
		super(errorMessages);
		this.errorMessages = errorMessages;
	}

	@Override
	public MAWorker retrieveWorker() throws Exception {
		return retrieveWorker(MediaType.HTML_UTF_8);
	}

	@Override
	public MAWorker retrieveWorker(MediaType errorMediaType) throws Exception {
		String email = Publix.getLoggedInUserEmail();
		if (email == null) {
			throw new BadRequestPublixException(errorMessages.noUserLoggedIn(),
					errorMediaType);
		}
		UserModel loggedInUser = UserModel.findByEmail(email);
		if (loggedInUser == null) {
			throw new BadRequestPublixException(errorMessages.userNotExists(),
					errorMediaType);
		}
		return loggedInUser.getWorker();
	}

}
