package batch

import batch.BatchProtocol.TellWhom.TellWhom
import play.api.libs.json.JsObject

/**
 * Wire definitions and internal message routing for the batch channel.
 */
object BatchProtocol {

  /**
   * Internal routing metadata; not serialized into the wire message.
   */
  object TellWhom extends Enumeration {
    type TellWhom = Value
    val All, SenderOnly, Unknown = Value
  }

  /**
   * Strings used as keys in the batch action JSON
   */
  //noinspection TypeAnnotation
  object BatchActionJsonKey extends Enumeration {
    // Action (mandatory for a BatchMsg)
    val Action = Value("action")
    // Session data (must be accompanied by a session version)
    val SessionData = Value("data")
    // Session patches (must be accompanied by a session version)
    val SessionPatches = Value("patches")
    // Identifier of a session action (mandatory)
    val SessionActionId = Value("id")
    // Batch session version (mandatory for session data or patches)
    val SessionVersion = Value("version")
    // Defines if we check the version before applying the patch
    val SessionVersioning = Value("versioning")
    // Error message
    val ErrorMsg = Value("errorMsg")
    // Machine-readable error code
    val ErrorCode = Value("errorCode")
  }

  /**
   * Stable wire codes used by clients to classify failures.
   */
  object BatchErrorCode {
    val SessionVersionConflict = "SESSION_VERSION_CONFLICT"
    val SessionPatchFailed = "SESSION_PATCH_FAILED"
    val SessionUpdateRetriesExhausted = "SESSION_UPDATE_RETRIES_EXHAUSTED"
    val UnknownAction = "UNKNOWN_ACTION"
    val BatchNotFound = "BATCH_NOT_FOUND"
  }

  /**
   * All possible batch actions a batch action message can have. They are
   * used as values in the JSON message's action field.
   */
  //noinspection TypeAnnotation
  object BatchAction extends Enumeration {
    type BatchAction = Value
    val Ready = Value("READY") // jatos.js signals that the batch channel is ready (comes before OPENED)
    val Opened = Value("OPENED") // Signals that the batch channel was opened
    val Closed = Value("CLOSED") // Signals that the batch channel was closed
    val Session = Value("SESSION") // Signals this message contains a batch session update
    val SessionGet = Value("SESSION_GET") // Signals a request for the complete session
    val SessionAck = Value("SESSION_ACK") // Signals that the session update was successful
    val SessionFail = Value("SESSION_FAIL") // Signals that the session update failed
    val Error = Value("ERROR") // Used to send an error back to the sender
  }

  /**
   * Message used for an action message. It has a JSON object, and the JSON
   * contains an 'action' field. Additionally, it can be addressed with TellWhom.
   */
  case class BatchMsg(json: JsObject, tellWhom: TellWhom = TellWhom.Unknown)

}
