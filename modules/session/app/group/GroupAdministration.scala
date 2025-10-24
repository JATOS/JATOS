package group

import daos.common.{GroupResultDao, StudyResultDao}
import models.common.GroupResult.GroupState
import models.common.{Batch, GroupResult, StudyResult}
import play.api.Logger
import play.db.jpa.JPAApi

import java.sql.Timestamp
import java.util.Date
import javax.inject.{Inject, Singleton}
import scala.compat.java8.FunctionConverters.asJavaSupplier
import scala.jdk.CollectionConverters._

/**
 * This class handles the joining, leaving, and reassigning of group members. A group's state is stored in a
 * GroupResult. Group channels (the handlers of the WebSockets) are managed by group dispatchers. Members of a group are
 * identified by the study result ID (which represents a particular study run).
 *
 * @author Kristian Lange
 */
@Singleton
class GroupAdministration @Inject()(groupDispatcherRegistry: GroupDispatcherRegistry,
                                    studyResultDao: StudyResultDao,
                                    groupResultDao: GroupResultDao,
                                    jpa: JPAApi) {

  private val logger: Logger = Logger(this.getClass)

  /**
   * Checks if the given study result belongs to a group study and then leaves the group
   */
  def leaveGroup(studyResult: StudyResult): Unit = {
    val groupResult = studyResult.getActiveGroupResult
    val study = studyResult.getStudy
    if (study.isGroupStudy && groupResult != null) {
      leave(studyResult)
    }
  }

  /**
   * Joins a group or creates a new group.
   *
   * It looks in the database whether we have an incomplete GroupResult (state STARTED, maxActiveMember not reached,
   * maxTotalMembers not reached). If there is none, create a new GroupResult.
   */
  def join(studyResult: StudyResult, batch: Batch): GroupResult = {
    jpa.withTransaction(asJavaSupplier(() => {
      val allGroupMaxNotReached = groupResultDao.findAllMaxNotReached(batch)
      val groupMaxNotReached =
        if (allGroupMaxNotReached.isEmpty) groupResultDao.create(new GroupResult(batch))
        else allGroupMaxNotReached.get(0)

      groupMaxNotReached.addActiveMember(studyResult)
      studyResult.setActiveGroupResult(groupMaxNotReached)
      groupResultDao.update(groupMaxNotReached)
      studyResultDao.update(studyResult)

      sendJoinedMsg(studyResult)

      groupMaxNotReached
    }))
  }

  /**
   * Leaves the group that this study result is a member of. Moves the study result in its group result into history.
   * Closes the group channel. Finishes a group if necessary. We don't need a JPA transaction here because the transaction is already started by the
   * calling methods.
   */
  def leave(studyResult: StudyResult): Unit = {
    val groupResult = studyResult.getActiveGroupResult
    if (groupResult == null || !studyResult.getStudy.isGroupStudy) return

    moveActiveMemberToHistory(studyResult)
    checkAndFinishGroup(groupResult)

    closeGroupChannel(studyResult.getId, groupResult.getId)
    sendLeftMsg(studyResult, groupResult)
  }

  /**
   * Closes the group channel for the study result specified by its ID that belongs to the group dispatcher specified
   * by its group result ID.
   */
  def closeGroupChannel(studyResultId: Long, groupResultId: Long): Unit = {
    val groupDispatcherOption = groupDispatcherRegistry.get(groupResultId)
    if (groupDispatcherOption.isDefined) {
      groupDispatcherOption.get.poisonChannel(studyResultId)
    }
  }

  /**
   * Reassigns this study result to a different group if possible. It moves the study result to a different group result
   * and the group channel to a different group dispatcher (corresponding to the different group result).
   *
   * @return returns true if the study result was reassigned, false if not.
   */
  def reassign(studyResult: StudyResult, batch: Batch): Boolean = {
    val differentGroupResultOption: Option[GroupResult] = reassignGroupResult(studyResult, batch)
    if (differentGroupResultOption.isDefined) {
      val currentGroupResult = studyResult.getActiveGroupResult
      val differentGroupResult = differentGroupResultOption.get
      reassignGroupChannel(studyResult, currentGroupResult, differentGroupResult)
      logger.info(s".reassign: studyResult ${studyResult.getId} reassigned from group" +
        s" ${currentGroupResult.getId} to group ${differentGroupResult.getId}")
      true
    } else {
      false
    }
  }

  /**
   * Reassigns a study result to a different GroupResult. It looks in the database whether we have another incomplete
   * GroupResult. If there is more than one, it assigns to the one with the most active members.
   */
  private def reassignGroupResult(studyResult: StudyResult, batch: Batch): Option[GroupResult] = {
    jpa.withTransaction(asJavaSupplier(() => {
      val currentGroupResult = studyResult.getActiveGroupResult
      if (currentGroupResult == null) {
        logger.info(s".reassignGroupResult: The study result with ID ${studyResult.getId} isn't member in any group.")
        return None
      }

      val allGroupMaxNotReached = groupResultDao.findAllMaxNotReached(batch).asScala
      // Don't reassign to the same group again
      allGroupMaxNotReached -= currentGroupResult
      if (allGroupMaxNotReached.isEmpty) {
        // No other possible group result found
        logger.info(s"reassignGroupResult: Couldn't reassign the study result with ID ${studyResult.getId} to any other group.")
        return None
      }

      // Found a possible group: put into active members of a new group - do not put into history members of the old group
      val differentGroupResult = allGroupMaxNotReached.head
      currentGroupResult.removeActiveMember(studyResult)
      differentGroupResult.addActiveMember(studyResult)
      studyResult.setActiveGroupResult(differentGroupResult)

      groupResultDao.update(currentGroupResult)
      groupResultDao.update(differentGroupResult)
      studyResultDao.update(studyResult)

      checkAndFinishGroup(currentGroupResult)

      Option(differentGroupResult)
    }))
  }

  /**
   * Sends a message to each member of the group. This message tells that this member has joined the GroupResult.
   */
  private def sendJoinedMsg(studyResult: StudyResult): Unit = {
    val groupResult = studyResult.getActiveGroupResult
    if (groupResult != null) {
      val groupDispatcherOption = groupDispatcherRegistry.get(groupResult.getId)
      if (groupDispatcherOption.isDefined)
        groupDispatcherOption.get.joined(studyResult.getId)
    }
  }

  /**
   * Sends a message to each member of the GroupResult that this member (specified by StudyResult)
   * has left the GroupResult.
   */
  private def sendLeftMsg(studyResult: StudyResult, groupResult: GroupResult): Unit = {
    if (groupResult != null) {
      val groupDispatcherOption = groupDispatcherRegistry.get(groupResult.getId)
      if (groupDispatcherOption.isDefined)
        groupDispatcherOption.get.left(studyResult.getId)
    }
  }

  /**
   * Reassigns the given group channel that belongs to the given StudyResult. It moves the group channel from
   * the current GroupDispatcher to the different one.
   */
  private def reassignGroupChannel(studyResult: StudyResult,
                                   currentGroupResult: GroupResult,
                                   differentGroupResult: GroupResult): Unit = {
    val currentDispatcher = groupDispatcherRegistry.get(currentGroupResult.getId).get
    // Get or create, because if the dispatcher was empty, it was shutdown and has to be recreated
    val differentDispatcher = groupDispatcherRegistry.getOrRegister(differentGroupResult.getId)
    currentDispatcher.reassignChannel(studyResult.getId, differentDispatcher)
  }

  /**
   * Moves the given StudyResult in its group to the history member list. This should happen when a study run is done
   * (StudyResult's state is in FINISHED, FAILED, ABORTED).
   */
  private def moveActiveMemberToHistory(studyResult: StudyResult): Unit = {
    val groupResult = studyResult.getActiveGroupResult
    groupResult.removeActiveMember(studyResult)
    groupResult.addHistoryMember(studyResult)
    studyResult.setActiveGroupResult(null)
    studyResult.setHistoryGroupResult(groupResult)
    groupResultDao.update(groupResult)
    studyResultDao.update(studyResult)
  }

  /**
   * Checks if a GroupResult should be put in state FINISHED and does it. A group is finished if it has no
   * more active members and the max number of members is reached.
   */
  private def checkAndFinishGroup(groupResult: GroupResult): Unit = {
    if (groupResult.getActiveMemberCount > 0) return

    val batch = groupResult.getBatch
    if (batch.getMaxTotalMembers != null && groupResult.getHistoryMemberCount >= batch.getMaxTotalMembers) {
      groupResult.setGroupState(GroupState.FINISHED)
      groupResult.setEndDate(new Timestamp(new Date().getTime))
      // All session data are temporary and have to be deleted when the group is finished
      groupResult.setGroupSessionData(null)
      groupResultDao.update(groupResult)
    }
  }

}
