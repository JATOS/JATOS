package session.batch

import session.batch.BatchDispatcher.BatchActionMsg

/**
  * Just a basic container for up to three BatchActionMsgs
  *
  * @author Kristian Lange (2017)
  */
class BatchActionMsgBundle(msg1: BatchActionMsg, msg2: BatchActionMsg, msg3: BatchActionMsg) {

  def this(msg1: BatchActionMsg, msg2: BatchActionMsg) = this(msg1, msg2, null)

  def this(msg1: BatchActionMsg) = this(msg1, null, null)

  def getAll: Array[BatchActionMsg] =
    if (msg1 == null) new Array[BatchActionMsg](0)
    else if (msg2 == null) Array(msg1)
    else if (msg3 == null) Array(msg1, msg2)
    else Array(msg1, msg2, msg3)

}
