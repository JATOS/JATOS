package controllers.publix;

import java.util.List;

import models.ComponentModel;
import models.StudyModel;
import models.results.ComponentResult;
import models.results.StudyResult;
import models.results.StudyResult.StudyState;
import models.workers.Worker;

import com.google.common.net.MediaType;

import exceptions.BadRequestPublixException;
import exceptions.ForbiddenPublixException;
import exceptions.NotFoundPublixException;

/**
 * Helper class for classes that extend Publix (implement MechArg's public API).
 * It offers methods that retrieve data (study, component models or their result
 * models) under certain constrains.
 * 
 * @author madsen
 */
public abstract class Retriever<T extends Worker> {

	protected ErrorMessages<T> errorMessages;

	public Retriever(ErrorMessages<T> errorMessages) {
		this.errorMessages = errorMessages;
	}

	public abstract T retrieveWorker() throws Exception;

	public abstract T retrieveWorker(MediaType errorMediaType) throws Exception;

	public StudyResult retrieveWorkersStartedStudyResult(T worker,
			StudyModel study) throws ForbiddenPublixException {
		return retrieveWorkersStartedStudyResult(worker, study,
				MediaType.HTML_UTF_8);
	}

	public StudyResult retrieveWorkersStartedStudyResult(T worker,
			StudyModel study, MediaType errorMediaType)
			throws ForbiddenPublixException {
		for (StudyResult studyResult : worker.getStudyResultList()) {
			if (studyResult.getStudy().getId() == study.getId()
					&& studyResult.getStudyState() == StudyState.STARTED) {
				// Since there is only one study result of the same study
				// allowed to be in STARTED, return the first one
				return studyResult;
			}
		}
		// Worker never started the study
		throw new ForbiddenPublixException(
				errorMessages.workerNeverStartedStudy(worker, study.getId()),
				errorMediaType);
	}

	public StudyResult retrieveWorkersLastStudyResult(T worker, StudyModel study)
			throws ForbiddenPublixException {
		return retrieveWorkersLastStudyResult(worker, study,
				MediaType.HTML_UTF_8);
	}

	public StudyResult retrieveWorkersLastStudyResult(T worker,
			StudyModel study, MediaType errorMediaType)
			throws ForbiddenPublixException {

		StudyResult studyResult;
		for (int i = worker.getStudyResultList().size() - 1; i == 0; i--) {
			studyResult = worker.getStudyResultList().get(i);
			if (studyResult.getStudy().getId() == study.getId()) {
				return studyResult;
			}
		}
		throw new ForbiddenPublixException(errorMessages.workerNeverDidStudy(
				worker, study.getId()), errorMediaType);
	}

	public ComponentResult retrieveComponentResult(ComponentModel component,
			StudyResult studyResult) {
		for (ComponentResult componentResult : studyResult
				.getComponentResultList()) {
			if (componentResult.getComponent().getId() == component.getId()) {
				return componentResult;
			}
		}
		return null;
	}

	public ComponentModel retrieveLastComponent(StudyResult studyResult) {
		List<ComponentResult> componentResultList = studyResult
				.getComponentResultList();
		if (componentResultList.size() > 0) {
			return componentResultList.get(componentResultList.size() - 1)
					.getComponent();
		}
		return null;
	}

	public ComponentModel retrieveFirstComponent(StudyModel study)
			throws BadRequestPublixException {
		ComponentModel component = study.getFirstComponent();
		if (component == null) {
			throw new BadRequestPublixException(
					errorMessages.studyHasNoComponents(study.getId()));
		}
		return component;
	}

	public ComponentModel retrieveNextComponent(StudyResult studyResult)
			throws NotFoundPublixException {
		ComponentModel currentComponent = retrieveLastComponent(studyResult);
		ComponentModel nextComponent = studyResult.getStudy().getNextComponent(
				currentComponent);
		if (nextComponent == null) {
			throw new NotFoundPublixException(errorMessages.noMoreComponents());
		}
		return nextComponent;
	}

	public ComponentModel retrieveComponent(StudyModel study, Long componentId)
			throws Exception {
		return retrieveComponent(study, componentId, MediaType.HTML_UTF_8);
	}

	public ComponentModel retrieveComponent(StudyModel study, Long componentId,
			MediaType errorMediaType) throws Exception {
		ComponentModel component = ComponentModel.findById(componentId);
		if (component == null) {
			throw new BadRequestPublixException(
					errorMessages.componentNotExist(study.getId(), componentId),
					errorMediaType);
		}
		if (!component.getStudy().getId().equals(study.getId())) {
			throw new BadRequestPublixException(
					errorMessages.componentNotBelongToStudy(study.getId(),
							componentId), errorMediaType);
		}
		return component;
	}

	public StudyModel retrieveStudy(Long studyId) throws Exception {
		return retrieveStudy(studyId, MediaType.HTML_UTF_8);
	}

	public StudyModel retrieveStudy(Long studyId, MediaType errorMediaType)
			throws Exception {
		StudyModel study = StudyModel.findById(studyId);
		if (study == null) {
			throw new BadRequestPublixException(
					errorMessages.studyNotExist(studyId), errorMediaType);
		}
		return study;
	}

}
