package group

import daos.common.{GroupResultDao, StudyResultDao}
import jakarta.persistence.EntityManager
import models.common.GroupResult.GroupState
import models.common.{Batch, GroupResult, StudyResult}
import play.api.Logger

import java.sql.Timestamp
import java.util.Date
import java.util.function.Consumer
import javax.inject.{Inject, Singleton}

/**
 * This class handles the joining, leaving, and reassigning of group members. A group's state is stored in a
 * GroupResult. Group dispatchers manage group channels (the handlers of the WebSockets). Members of a group are
 * identified by the study result ID (which represents a particular study run).
 */
@Singleton
class GroupAdministration @Inject()(groupDispatcher: GroupDispatcher,
                                    studyResultDao: StudyResultDao,
                                    groupResultDao: GroupResultDao) {

  private val logger: Logger = Logger(this.getClass)

  /**
   * Joins a group or creates a new group.
   *
   * It looks in the database whether we have an incomplete GroupResult (state STARTED, maxActiveMember not reached,
   * maxTotalMembers not reached). If there is none, create a new GroupResult.
   */
  def join(studyResult: StudyResult, batch: Batch): GroupResult = {
    val groupResult = groupResultDao.withNewTransaction((_: EntityManager) => {
      val managedStudyResult = studyResultDao.findById(studyResult.getId)
      val groupMaxNotReached = groupResultDao.findFirstMaxNotReachedForUpdate(batch)
        .orElseGet(() => groupResultDao.persist(new GroupResult(batch)))

      groupMaxNotReached.addActiveMember(managedStudyResult)
      managedStudyResult.setActiveGroupResult(groupMaxNotReached)
      groupResultDao.merge(groupMaxNotReached)
      studyResultDao.merge(managedStudyResult)

      studyResult.setActiveGroupResult(groupMaxNotReached)
      groupMaxNotReached
    })

    sendJoinedMsg(studyResult.getId, groupResult.getId)

    groupResult
  }

  def leave(studyResultId: Long): Unit = {
    val studyResult = studyResultDao.findById(studyResultId)
    leave(studyResult)
  }

  /**
   * Leaves the group that this study result is a member of. Moves the study result in its group result into history.
   * Closes the group channel. Finishes a group if necessary.
   */
  def leave(studyResult: StudyResult): Unit = {
    val groupResult = studyResult.getActiveGroupResult
    if (groupResult == null) return

    groupResultDao.withNewTransaction((_ => {
      moveActiveMemberToHistory(studyResult.getId)
      checkAndFinishGroup(groupResult.getId)
    }): Consumer[EntityManager])

    studyResult.setActiveGroupResult(null)
    studyResult.setHistoryGroupResult(groupResult)

    sendLeftMsg(studyResult.getId, groupResult.getId)
    closeGroupChannel(studyResult.getId, groupResult.getId)
  }

  /**
   * Closes the group channel for the study result specified by its ID that belongs to the group dispatcher specified
   * by its group result ID.
   */
  def closeGroupChannel(studyResultId: Long, groupResultId: Long): Unit = {
    groupDispatcher.poisonChannel(groupResultId, studyResultId)
  }

  /**
   * Reassigns this study result to a different group if possible. It moves the study result to a different group result
   * and the group channel to a different group dispatcher (corresponding to the different group result).
   *
   * @return returns true if the study result was reassigned, false if not.
   */
  def reassign(studyResult: StudyResult, batch: Batch): Boolean = {
    val originalGroupResult = studyResult.getActiveGroupResult
    if (originalGroupResult == null) return false

    val differentGroupResultOption: Option[GroupResult] = reassignGroupResult(studyResult.getId, batch)
    if (differentGroupResultOption.isDefined) {
      val differentGroupResult = differentGroupResultOption.get
      studyResult.setActiveGroupResult(differentGroupResult)
      reassignGroupChannel(studyResult.getId, originalGroupResult.getId, differentGroupResult.getId)
      logger.info(s".reassign: studyResult ${studyResult.getId} reassigned from group" +
        s" ${originalGroupResult.getId} to group ${differentGroupResult.getId}")
      true
    } else {
      false
    }
  }

  /**
   * Reassigns a study result to a different GroupResult. It looks in the database whether we have another incomplete
   * GroupResult. If there is more than one, it assigns to the one with the most active members.
   */
  private def reassignGroupResult(studyResultId: Long, batch: Batch): Option[GroupResult] = {
    groupResultDao.withNewTransaction(_ => {
      val studyResult = studyResultDao.findById(studyResultId)
      if (studyResult == null || studyResult.getActiveGroupResult == null) {
        logger.info(s".reassignGroupResult: The study result with ID $studyResultId isn't member in any group.")
        return None
      }

      val currentGroupResult = studyResult.getActiveGroupResult
      val differentGroupResultOption = groupResultDao.findFirstDifferentMaxNotReachedForUpdate(batch, currentGroupResult)
      if (!differentGroupResultOption.isPresent) {
        logger.info(s"reassignGroupResult: Couldn't reassign the study result with ID ${studyResult.getId} to any other group.")
        return None
      }

      // Found a possible group: put into active members of a new group - do not put into history members of the old group
      val differentGroupResult = differentGroupResultOption.get
      currentGroupResult.removeActiveMember(studyResult)
      differentGroupResult.addActiveMember(studyResult)
      studyResult.setActiveGroupResult(differentGroupResult)

      groupResultDao.merge(currentGroupResult)
      groupResultDao.merge(differentGroupResult)
      studyResultDao.merge(studyResult)

      checkAndFinishGroup(currentGroupResult.getId)

      Option(differentGroupResult)
    })
  }

  /**
   * Sends a message to each member of the group. This message tells that this member has joined the GroupResult.
   */
  private def sendJoinedMsg(studyResultId: Long, groupResultId: Long): Unit = {
    groupDispatcher.joined(groupResultId, studyResultId)
  }

  /**
   * Sends a message to each member of the GroupResult that this member (specified by StudyResult)
   * has left the GroupResult.
   */
  private def sendLeftMsg(studyResultId: Long, groupResultId: Long): Unit = {
    groupDispatcher.left(groupResultId, studyResultId)
  }

  /**
   * Reassigns the given group channel that belongs to the given StudyResult. It moves the group channel from
   * the current GroupDispatcher to the different one.
   */
  private def reassignGroupChannel(studyResultId: Long,
                                   currentGroupResultId: Long,
                                   differentGroupResultId: Long): Unit = {
    groupDispatcher.reassignChannel(studyResultId, currentGroupResultId, differentGroupResultId)
  }

  /**
   * Moves the given StudyResult in its group to the history member list. This should happen when a study run is done
   * (StudyResult's state is in FINISHED, FAILED, ABORTED).
   */
  private def moveActiveMemberToHistory(studyResultId: Long): Unit = {
    val studyResult = studyResultDao.findById(studyResultId)
    val groupResult = studyResult.getActiveGroupResult
    if (groupResult != null) {
      groupResult.removeActiveMember(studyResult)
      groupResult.addHistoryMember(studyResult)
      studyResult.setActiveGroupResult(null)
      studyResult.setHistoryGroupResult(groupResult)
      groupResultDao.merge(groupResult)
      studyResultDao.merge(studyResult)
    }
  }

  /**
   * Checks if a GroupResult should be put in state FINISHED and does it. A group is finished if it has no
   * more active members and the max number of members is reached.
   */
  private def checkAndFinishGroup(groupResultId: Long): Unit = {
    val groupResult = groupResultDao.findById(groupResultId)
    if (groupResult.getActiveMemberCount > 0) return

    val batch = groupResult.getBatch
    if (batch.getMaxTotalMembers != null && groupResult.getHistoryMemberCount >= batch.getMaxTotalMembers) {
      groupResult.setGroupState(GroupState.FINISHED)
      groupResult.setEndDate(new Timestamp(new Date().getTime))
      // All session data are temporary and have to be deleted when the group is finished
      groupResult.setGroupSessionData(null)
      groupResultDao.merge(groupResult)
    }
  }

}
