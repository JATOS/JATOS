package group

import java.sql.Timestamp
import java.util.Date

import daos.common.{GroupResultDao, StudyResultDao}
import javax.inject.{Inject, Singleton}
import models.common.GroupResult.GroupState
import models.common.{Batch, GroupResult, StudyResult}
import play.db.jpa.JPAApi

import scala.collection.JavaConverters._
import scala.compat.java8.FunctionConverters.asJavaSupplier

/**
  * Administrates groups, e.g. joining or leaving. A group's state is stored in a GroupResult. Members of a group are
  * identified by the StudyResult's ID (which represents a particular study run).
  *
  * All group members exchange messages via WebSockets that are called group channels in JATOS. The message dispatching
  * system is implemented with Akka.
  *
  * @author Kristian Lange (2015 - 2019)
  */
@Singleton
class GroupAdministration @Inject()(studyResultDao: StudyResultDao,
                                    groupResultDao: GroupResultDao,
                                    jpa: JPAApi) {

  /**
    * Joins the a GroupResult or create a new one. Persists changes.
    *
    * Looks in the database whether we have an incomplete GroupResult (state STARTED, maxActiveMember not reached,
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
      groupMaxNotReached
    }))
  }

  /**
    * Leaves the group that this studyResult is member of. Moves the given StudyResult in its group result into history.
    */
  def leave(studyResult: StudyResult): Unit = {
    val groupResult = studyResult.getActiveGroupResult
    if (groupResult == null || !studyResult.getStudy.isGroupStudy) return

    // We need this transaction here because later on in the GroupDispatcher the updated data are needed
    jpa.withTransaction(asJavaSupplier(() => {
      moveActiveMemberToHistory(studyResult)
      checkAndFinishGroup(groupResult)
    }))
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
    * Reassigns this StudyResult to a different GroupResult if possible.
    *
    * Looks in the database whether we have other incomplete GroupResult. If there are more than one, it returns the
    * one with the most active members. If there is no other GroupResult it returns an error msg.
    *
    * @return Either with String if error or a GroupResult if success
    */
  def reassign(studyResult: StudyResult, batch: Batch): Either[String, GroupResult] = {
    val currentGroupResult = studyResult.getActiveGroupResult
    if (currentGroupResult == null) {
      return Left(s"The study result with ID ${studyResult.getId} isn't member in any group.")
    }

    // We need this transaction here because later on in the GroupDispatcher the updated data are needed
    jpa.withTransaction(asJavaSupplier(() => {
      val allGroupMaxNotReached = groupResultDao.findAllMaxNotReached(batch).asScala
      // Don't reassign to the same group again
      allGroupMaxNotReached -= currentGroupResult
      if (allGroupMaxNotReached.isEmpty) {
        // No other possible group result found
        return Left(s"Couldn't reassign the study result with ID ${studyResult.getId} to any other group.")
      }

      // Found another possible group
      val differentGroupResult = allGroupMaxNotReached.head
      currentGroupResult.removeActiveMember(studyResult)
      differentGroupResult.addActiveMember(studyResult)
      studyResult.setActiveGroupResult(differentGroupResult)
      checkAndFinishGroup(currentGroupResult)

      groupResultDao.update(currentGroupResult)
      groupResultDao.update(differentGroupResult)
      studyResultDao.update(studyResult)
      Right(differentGroupResult)
    }))
  }

  /**
    * Checks if a GroupResult should be put in state FINISHED and does it.
    * There are three reasons to do this and all three have in common that the group does have no
    * more active members:
    * 1. Group empty && group is in state FIXED (no new members are allowed to join)
    * 2. Group empty && max number of members is reached in the group
    * makes assembling new groups difficult)
    */
  private def checkAndFinishGroup(groupResult: GroupResult): Unit = {
    if (!groupResult.getActiveMemberList.isEmpty) return

    // 1. Group empty && group is in state FIXED (no new members are allowed to join)
    if (GroupState.FIXED == groupResult.getGroupState) {
      finishGroupResult(groupResult)
      return
    }

    // 2. Group empty && max number of members is reached in the group
    val batch = groupResult.getBatch
    if (batch.getMaxTotalMembers != null && groupResult.getHistoryMemberList.size() >= batch.getMaxTotalMembers) {
      finishGroupResult(groupResult)
      return
    }
  }

  private def finishGroupResult(groupResult: GroupResult): Unit = {
    groupResult.setGroupState(GroupState.FINISHED)
    groupResult.setEndDate(new Timestamp(new Date().getTime))
    // All session data are temporarily and have to be deleted when the group is finished
    groupResult.setGroupSessionData(null)
    groupResultDao.update(groupResult)
  }

}
