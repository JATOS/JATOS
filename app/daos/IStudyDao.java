package daos;

import java.util.List;

import models.StudyModel;
import models.UserModel;

/**
 * Interface for DAO of StudyModel
 * 
 * @author Kristian Lange
 */
public interface IStudyDao {

	/**
	 * Persist study and add member.
	 */
	public abstract void addStudy(StudyModel study, UserModel loggedInUser);

	/**
	 * Add member to study.
	 */
	public abstract void addMemberToStudy(StudyModel study, UserModel member);

	/**
	 * Update properties of study with properties of updatedStudy.
	 */
	public abstract void updateStudysProperties(StudyModel study,
			StudyModel updatedStudy);

	/**
	 * Update properties of study with properties of updatedStudy (excluding
	 * study's dir name).
	 */
	public abstract void updateStudysPropertiesWODirName(StudyModel study,
			StudyModel updatedStudy);

	/**
	 * Remove study and its components
	 */
	public abstract void removeStudy(StudyModel study);

	public abstract StudyModel findById(Long id);

	public abstract StudyModel findByUuid(String uuid);

	public abstract List<StudyModel> findAll();

	public abstract List<StudyModel> findAllByUser(String memberEmail);

}