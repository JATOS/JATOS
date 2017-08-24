package group

import javax.inject.{Inject, Singleton}

import daos.common.{GroupResultDao, StudyResultDao}
import models.common.GroupResult.GroupState
import models.common.{Batch, GroupResult, StudyResult}
import play.db.jpa.JPAApi

import scala.collection.JavaConverters._
import scala.compat.java8.FunctionConverters.asJavaSupplier

/**
  * Administrates groups, e.g. joining or leaving. A group's state is stored in a GroupResult.
  * Members of a group are identified by the StudyResult's ID (which represents a particular
  * study run).
  *
  * All group members exchange messages via WebSockets that are called group channels in JATOS.
  * The message dispatching system is implemented with Akka.
  *
  * @author Kristian Lange (2015, 2017)
  */
@Singleton
class GroupAdministration @Inject()(studyResultDao: StudyResultDao,
                                    groupResultDao: GroupResultDao,
                                    jpa: JPAApi) {

  /**
    * Joins the a GroupResult or create a new one. Persists changes.
    *
    * Looks in the database whether we have an incomplete GroupResult (state STARTED,
    * maxActiveMember not reached, maxTotalMembers not reached). If there are more than one,
    * return the one with the most active members. If there is none, create a new GroupResult.
    */
  def join(studyResult: StudyResult, batch: Batch): GroupResult = {
    val groupResultList: List[GroupResult] =
      groupResultDao.findAllMaxNotReached(batch).asScala.toList
    val groupResult =
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

    // We need this transaction here because later on in the GroupDispatcher the updated data are
    // needed
    jpa.withTransaction(asJavaSupplier(() => {
      groupResultDao.update(groupResult)
      studyResultDao.update(studyResult)
    }))
  }

  /**
    * Reassigns this StudyResult to a different GroupResult if possible.
    *
    * Looks in the database whether we have other incomplete GroupResult. If there are more than
    * one, it returns the one with the most active members. If there is no other GroupResult it
    * returns an error msg.
    *
    * @return Either with String if error or a GroupResult if success
    */
  def reassign(studyResult: StudyResult, batch: Batch): Either[String, GroupResult] = {
    val currentGroupResult = studyResult.getActiveGroupResult
    if (currentGroupResult == null)
      return Left(s"The study result with ID ${studyResult.getId} isn't member in any group.")

    val groupResultList = groupResultDao.findAllMaxNotReached(batch).asScala
    groupResultList -= currentGroupResult
    if (groupResultList.isEmpty)
    // No other possible group result found
      return Left(s"Couldn't reassign the study result with ID ${studyResult.getId} to any other " +
        s"group.")

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
    Right(differentGroupResult)
  }

  /**
    * Moves the given StudyResult in its group to the history member list. This should happen
    * when a study run is done (StudyResult's state is in FINISHED, FAILED, ABORTED).
    */
  def moveToHistory(studyResult: StudyResult): Unit = {
    val groupResult = studyResult.getActiveGroupResult
    groupResult.removeActiveMember(studyResult)
    groupResult.addHistoryMember(studyResult)
    studyResult.setActiveGroupResult(null)
    studyResult.setHistoryGroupResult(groupResult)
    groupResultDao.update(groupResult)
    studyResultDao.update(studyResult)
  }

  /**
    * Finds the group result with the most active members. Assumes the given list is not empty.
    */
  private def findGroupResultWithMostActiveMembers(list: List[GroupResult]): GroupResult =
    list match {
      // Do some recursive magic
      case List(x: GroupResult) => x
      case x :: y :: rest =>
        findGroupResultWithMostActiveMembers(
          (if (x.getActiveMemberList.size > y.getActiveMemberList.size) x else y) :: rest
        )
    }

  /**
    * Checks if a GroupResult should be put in state FINISHED and does it.
    * There are three reasons to do this and all three have in common that the group does have no
    * more active members:
    * 1. The group is in state FIXED (no new members are allowed to join)
    * 2. The max number of workers is reached in the batch
    * 3. There is more than one group in state STARTED (one open group is enough - more than one
    * makes assembling new groups difficult)
    */
  def checkAndFinishGroup(groupResult: GroupResult): Unit = {

    // 1. The group is in state FIXED (no new members are allowed to join)
    if (groupResult.getActiveMemberList.isEmpty
      && GroupState.FIXED == groupResult.getGroupState) {
      finishGroupResult(groupResult)
      return
    }

    // 2. The max number of workers is reached in the batch
    val batch = groupResult.getBatch
    if (groupResult.getActiveMemberList.isEmpty &&
      batch.getMaxTotalWorkers != null &&
      batch.getWorkerList.size >= batch.getMaxTotalWorkers) {
      finishGroupResult(groupResult)
      return
    }

    // 3. There is more than one group in state STARTED (one open group is enough - more than one
    // makes assembling new groups difficult)
    val startedGroupList = groupResultDao.findAllStartedByBatch(batch)
    if (groupResult.getActiveMemberList.isEmpty &&
      startedGroupList.size > 1) {
      finishGroupResult(groupResult)
      return
    }
  }

  private def finishGroupResult(groupResult: GroupResult) = {
    groupResult.setGroupState(GroupState.FINISHED)
    // All session data are temporarily and have to be deleted when the group is finished
    groupResult.setGroupSessionData(null)
    groupResultDao.update(groupResult)
  }

}
