package services.publix

import daos.common.StudyResultDao
import group.GroupDispatcher
import org.apache.pekko.actor.ActorSystem
import org.junit.{After, Before, Test}
import org.mockito.Mockito.{mock, never, verify, when}
import play.api.inject.ApplicationLifecycle
import play.db.jpa.JPAApi

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class GroupCleanerTest {

  private var actorSystem: ActorSystem = _
  private var groupDispatcher: GroupDispatcher = _
  private var publixUtils: PublixUtils = _
  private var cleaner: GroupCleaner = _

  @Before
  def setUp(): Unit = {
    actorSystem = ActorSystem("group-cleaner-test")
    groupDispatcher = mock(classOf[GroupDispatcher])
    publixUtils = mock(classOf[PublixUtils])
    cleaner = new GroupCleaner(
      actorSystem,
      mock(classOf[ApplicationLifecycle]),
      groupDispatcher,
      mock(classOf[StudyResultDao]),
      publixUtils,
      mock(classOf[JPAApi]))
  }

  @After
  def tearDown(): Unit = Await.ready(actorSystem.terminate(), 10.seconds)

  @Test
  def cleanInactiveGroupMembers_keepsMemberWhoseChannelExistsOnAnotherNode(): Unit = {
    when(groupDispatcher.hasChannelInCluster(10L)).thenReturn(Future.successful(true))

    Await.result(cleaner.cleanInactiveGroupMembers(Seq(10L)), 2.seconds)

    verify(publixUtils, never()).finishStudyRun(
      org.mockito.ArgumentMatchers.anyLong(),
      org.mockito.ArgumentMatchers.anyBoolean(),
      org.mockito.ArgumentMatchers.anyString(),
      org.mockito.ArgumentMatchers.anyString())
  }

  @Test
  def cleanInactiveGroupMembers_finishesMemberWithNoChannelInCluster(): Unit = {
    when(groupDispatcher.hasChannelInCluster(10L)).thenReturn(Future.successful(false))
    when(publixUtils.finishStudyRun(10L, false,
      "Inactive group member was forced to leave its group.",
      "Inactive group member was forced to leave its group. Finished study run."))
      .thenReturn("confirmation")

    Await.result(cleaner.cleanInactiveGroupMembers(Seq(10L)), 2.seconds)

    verify(publixUtils).finishStudyRun(10L, false,
      "Inactive group member was forced to leave its group.",
      "Inactive group member was forced to leave its group. Finished study run.")
  }
}
