package group

import javax.inject.{Inject, Singleton}

import daos.common.{GroupResultDao, StudyResultDao}
import models.common.GroupResult.GroupState
import models.common.{Batch, GroupResult, Study, StudyResult}
import play.db.jpa.JPAApi

import scala.collection.JavaConverters._
import scala.compat.java8.FunctionConverters.asJavaSupplier

/**
  * Handles groups, e.g. joining or leaving a GroupResult. Members of a
  * GroupResult are StudyResults, which represents a particular study run.
  *
  * All GroupResult members exchange messages via WebSockets that are called
  * group channels in JATOS. The message dispatching system is implemented with
  * Akka.
  *
  * @author Kristian Lange (2015, 2017)
  */
@Singleton
class GroupAdministration @Inject()(groupChannelService: GroupChannelService,
                                    studyResultDao: StudyResultDao,
                                    groupResultDao: GroupResultDao,
                                    jpa: JPAApi) {

  /**
    * Checks whether this StudyResult has an history GroupResult, means it was
    * a member in a group in the past and it tries to run a second group study.
    */
  @throws[GroupException]
  def checkHistoryGroupResult(studyResult: StudyResult): Unit = {
    val groupResult = studyResult.getHistoryGroupResult
    if (groupResult != null)
      throw new GroupException("It's not possible to run a group study twice.")
  }

  /**
    * Joins the a GroupResult or create a new one. Persists changes.
    *
    * Looks in the database whether we have an incomplete GroupResult (state
    * STARTED, maxActiveMember not reached, maxTotalMembers not reached). If
    * there are more than one, return the one with the most active members. If
    * there is none, create a new GroupResult.
    */
  def join(studyResult: StudyResult, batch: Batch): GroupResult = {
    val groupResultList: List[GroupResult] = groupResultDao.findAllMaxNotReached(batch).asScala.toList
    var groupResult =
      if (groupResultList.isEmpty) groupResultDao.create(new GroupResult(batch))
      else findGroupResultWithMostActiveMembers(groupResultList)
    groupResult.addActiveMember(studyResult)
    studyResult.setActiveGroupResult(groupResult)
    groupResultDao.update(groupResult)
    studyResultDao.update(studyResult)
    groupResult
  }

  /**
    * Leaves the group that this studyResult is member of.
    */
  def leave(studyResult: StudyResult): Unit = {
    val groupResult = studyResult.getActiveGroupResult
    if (groupResult == null) return
    groupResult.removeActiveMember(studyResult)
    studyResult.setActiveGroupResult(null)
    checkAndFinishGroup(groupResult)

    // We need this transaction here because later on in the GroupDispatcher
    // the updated data are needed
    jpa.withTransaction(asJavaSupplier(() => {
      groupResultDao.update(groupResult)
      studyResultDao.update(studyResult)
    }))
  }

  /**
    * Reassigns this StudyResult to a different GroupResult if possible.
    * Persists changes in it's own transaction.
    *
    * Looks in the database whether we have other incomplete GroupResult (state
    * STARTED, maxActiveMember not reached, maxTotalMembers not reached). If
    * there are more than one, it returns the one with the most active members.
    * If there is no other GroupResult it throws a NoContentPublixException.
    */
  @throws[GroupException]
  def reassign(studyResult: StudyResult, batch: Batch): GroupResult = {
    val currentGroupResult = studyResult.getActiveGroupResult
    if (currentGroupResult == null)
      throw new GroupException(s"The study result with ID ${studyResult.getId} isn't member in any group.")

    val groupResultList = groupResultDao.findAllMaxNotReached(batch).asScala
    groupResultList -= currentGroupResult
    if (groupResultList.isEmpty)
    // No other possible group result found
      throw new GroupException(s"Couldn't reassign the study result with ID ${studyResult.getId} to any other group.")

    // Found another possible group
    val differentGroupResult = findGroupResultWithMostActiveMembers(groupResultList.toList)
    currentGroupResult.removeActiveMember(studyResult)
    differentGroupResult.addActiveMember(studyResult)
    studyResult.setActiveGroupResult(differentGroupResult)
    checkAndFinishGroup(currentGroupResult)

    // We need this transaction here because later on in the GroupDispatcher
    // the updated data are needed
    jpa.withTransaction(asJavaSupplier(() => {
      groupResultDao.update(currentGroupResult)
      groupResultDao.update(differentGroupResult)
      studyResultDao.update(studyResult)
    }))
    differentGroupResult
  }

  /**
    * Moves the given StudyResult in its group into history and closes the
    * group channel which includes sending a left message to all group members.
    */
  def finishStudyResultInGroup(studyResult: StudyResult): Unit = {
    val groupResult = studyResult.getActiveGroupResult
    val study = studyResult.getStudy
    if (study.isGroupStudy && groupResult != null) {
      moveToHistory(studyResult)
      checkAndFinishGroup(groupResult)
      groupChannelService.closeGroupChannel(studyResult, groupResult)
      groupChannelService.sendLeftMsg(studyResult, groupResult)
    }
  }

  /**
    * Moves the given StudyResult in its group to the history member list. This
    * should happen when a study run is done (StudyResult's state is in
    * FINISHED, FAILED, ABORTED).
    */
  private def moveToHistory(studyResult: StudyResult) = {
    val groupResult = studyResult.getActiveGroupResult
    groupResult.removeActiveMember(studyResult)
    groupResult.addHistoryMember(studyResult)
    studyResult.setActiveGroupResult(null)
    studyResult.setHistoryGroupResult(groupResult)
    groupResultDao.update(groupResult)
    studyResultDao.update(studyResult)
  }

  /**
    * Finds the group result with the most active members. Assumes the given
    * list is not empty.
    */
  private def findGroupResultWithMostActiveMembers(list: List[GroupResult]): GroupResult =
    list match {
      case List(x: GroupResult) => x
      case x :: y :: rest =>
        findGroupResultWithMostActiveMembers(
          (if (x.getActiveMemberList.size > y.getActiveMemberList.size) x else y)
            :: rest)
    }

  /**
    * Checks if a GroupResult should be put in state FINISHED and does it.
    * There are there reasons to do this:
    * All three have in common that the group does have no more active members.
    *
    * 1. The group is in state FIXED (no new members are allowed to join)
    * 2. The max number of workers is reached in the batch
    * 3. There is more than one group in state STARTED (one open group is
    * enough - more than one makes assembling new groups difficult)
    */
  private def checkAndFinishGroup(groupResult: GroupResult): Unit = {
    if (groupResult.getActiveMemberList.isEmpty)
      return
    val batch = groupResult.getBatch
    if (GroupState.FIXED == groupResult.getGroupState) {
      finishGroupResult(groupResult)
      return
    }
    if (batch.getMaxTotalWorkers != null && batch.getWorkerList.size >= batch.getMaxTotalWorkers) {
      finishGroupResult(groupResult)
      return
    }
    val startedGroupList = groupResultDao.findAllStartedByBatch(batch)
    if (startedGroupList.size > 1) {
      finishGroupResult(groupResult)
      return
    }
  }

  private def finishGroupResult(groupResult: GroupResult) = {
    groupResult.setGroupState(GroupState.FINISHED)
    // All session data are temporarily and have to be deleted when the
    // group is finished
    groupResult.setGroupSessionData(null)
    groupResultDao.update(groupResult)
  }

}
