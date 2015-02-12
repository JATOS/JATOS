package daos;

import java.util.List;

import models.StudyModel;
import models.UserModel;

/**
 * Interface for DAO of StudyModel
 * 
 * @author Kristian Lange
 */
public interface IStudyDao extends IAbstractDao<StudyModel>{

	/**
	 * Persist study and add member.
	 */
	public abstract void create(StudyModel study, UserModel loggedInUser);

	/**
	 * Add member to study.
	 */
	public abstract void addMember(StudyModel study, UserModel member);

	public abstract void update(StudyModel study);

	/**
	 * Update properties of study with properties of updatedStudy.
	 */
	public abstract void updateProperties(StudyModel study,
			StudyModel updatedStudy);

	/**
	 * Update properties of study with properties of updatedStudy (excluding
	 * study's dir name).
	 */
	public abstract void updatePropertiesWODirName(StudyModel study,
			StudyModel updatedStudy);

	/**
	 * Remove study and its components
	 */
	public abstract void remove(StudyModel study);

	public abstract StudyModel findById(Long id);

	public abstract StudyModel findByUuid(String uuid);

	public abstract List<StudyModel> findAll();

	public abstract List<StudyModel> findAllByUser(String memberEmail);

}