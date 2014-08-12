package controllers.publix;

import java.io.StringWriter;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import models.ComponentModel;
import models.results.ComponentResult;
import models.results.ComponentResult.ComponentState;
import models.results.StudyResult;
import models.results.StudyResult.StudyState;
import models.workers.Worker;

import org.w3c.dom.Document;

import play.Logger;
import play.db.jpa.JPA;
import play.mvc.Http.RequestBody;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.net.MediaType;

import exceptions.ForbiddenPublixException;

/**
 * Utilility class with functions that are common for all classes that extend
 * Publix and don't belong in a controller.
 * 
 * @author madsen
 */
public class PublixUtils {

	private static final String CLASS_NAME = PublixUtils.class.getSimpleName();

	private ErrorMessages<? extends Worker> errorMessages;
	private Retriever<? extends Worker> retriever;
	private Persistance persistance = new Persistance();

	public PublixUtils(ErrorMessages<? extends Worker> errorMessages,
			Retriever<? extends Worker> retriever, Persistance persistance) {
		this.errorMessages = errorMessages;
		this.retriever = retriever;
		this.persistance = persistance;
	}

	public ComponentResult startComponent(ComponentModel component, StudyResult studyResult)
			throws ForbiddenPublixException {
		return startComponent(component, studyResult,
				MediaType.HTML_UTF_8);
	}

	public ComponentResult startComponent(ComponentModel component,
			StudyResult studyResult, MediaType errorMediaType)
			throws ForbiddenPublixException {
		ComponentResult componentResult = retriever.retrieveComponentResult(
				component, studyResult);
		if (componentResult != null) {
			// Only one component of the same kind can be done in the same study
			// by the same worker. Exception: If a component is reloadable,
			// the old component result will be deleted and a new one generated.
			if (component.isReloadable()) {
				studyResult.removeComponentResult(componentResult);
			} else {
				// Worker tried to reload a non-reloadable component -> end
				// study with fail
				finishStudy(false, studyResult);
				// Since an exception triggers a transaction rollback we have
				// to commit the transaction manually.
				JPA.em().flush();
				JPA.em().getTransaction().commit();
				throw new ForbiddenPublixException(
						errorMessages.componentNotAllowedToReload(studyResult
								.getStudy().getId(), component.getId()));
			}
		}
		// Only one ComponentResult can be in state started at the same time.
		// To start a new ComponentResult, finish all other ones.
		finishAllComponentResults(studyResult);
		return persistance.createComponentResult(studyResult, component);
	}

	public String finishStudy(Boolean successful, StudyResult studyResult) {
		finishAllComponentResults(studyResult);
		String confirmationCode;
		if (successful) {
			confirmationCode = studyResult.generateConfirmationCode();
			studyResult.setStudyState(StudyState.FINISHED);
		} else {
			confirmationCode = "fail";
			studyResult.setStudyState(StudyState.FAIL);
		}
		studyResult.merge();
		return confirmationCode;
	}

	public void finishAllComponentResults(StudyResult studyResult) {
		for (ComponentResult componentResult : studyResult
				.getComponentResultList()) {
			if (componentResult.getComponentState() != ComponentState.FINISHED
					|| componentResult.getComponentState() != ComponentState.FAIL) {
				componentResult.setComponentState(ComponentState.FINISHED);
				componentResult.merge();
			}
		}
	}

	public static String getRequestBodyAsString(RequestBody requestBody) {
		String text = requestBody.asText();
		if (text != null) {
			return text;
		}

		JsonNode json = requestBody.asJson();
		if (json != null) {
			return json.toString();
		}

		Document xml = requestBody.asXml();
		if (xml != null) {
			return asString(xml);
		}

		return null;
	}

	/**
	 * Convert XML-Document to String
	 */
	protected static String asString(Document doc) {
		try {
			DOMSource domSource = new DOMSource(doc);
			StringWriter writer = new StringWriter();
			StreamResult result = new StreamResult(writer);
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.transform(domSource, result);
			return writer.toString();
		} catch (TransformerException e) {
			Logger.info(CLASS_NAME + ".asString: XML to String conversion: ", e);
			return null;
		}
	}

}
