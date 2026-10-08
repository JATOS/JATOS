package group

import group.GroupProtocol.TellWhom.TellWhom
import play.api.libs.json.JsObject

/**
 * Wire definitions and internal message routing for the group channel.
 */
object GroupProtocol {

  /**
   * Internal routing metadata; not serialized into the wire message.
   */
  object TellWhom extends Enumeration {
    type TellWhom = Value
    val All, AllButSender, SenderOnly, Unknown = Value
  }


  /**
   * All possible group actions a group action message can have. They are
   * used as values in the JSON message's action field.
   */
  //noinspection TypeAnnotation
  object GroupAction extends Enumeration {
    type GroupAction = Value
    val Ready = Value("READY") // jatos.js signals that the group channel is ready (comes before OPENED)
    val Joined = Value("JOINED") // Signals to every group member that a new member joined
    val Left = Value("LEFT") // Signals to every member that a member left
    val Opened = Value("OPENED") // Signals to the sender that its group channel opened
    val Closed = Value("CLOSED") // Signals to the recipient that its group channel was closed by JATOS
    val ChannelOpened = Value("CHANNEL_OPENED") // Signals that another member's group channel opened
    val ChannelClosed = Value("CHANNEL_CLOSED") // Signals that another member's group channel closed
    val Session = Value("SESSION") // Signals this message contains a group session update
    val SessionGet = Value("SESSION_GET") // Requests full state after a persistent patch gap
    val SessionAck = Value("SESSION_ACK") // Signals that the session update was successful
    val SessionFail = Value("SESSION_FAIL") // Signals that the session update failed
    val Fixed = Value("FIXED") // Signals that this group is now fixed (no new members)
    val Error = Value("ERROR") // Used to send an error back to the sender
  }

  /**
   * Strings used as keys in the group action JSON
   */
  //noinspection TypeAnnotation
  object GroupActionJsonKey extends Enumeration {
    // Action (mandatory for an action GroupMsg)
    val Action = Value("action")
    // Recipient of a group msg
    val Recipient = Value("recipient")
    // Group result ID
    val GroupResultId = Value("groupResultId")
    // GroupState
    val GroupState = Value("groupState")
    // Group member ID (which is equal to the study result ID)
    val MemberId = Value("memberId")
    // All active members of the group defined by their study result ID
    val Members = Value("members")
    // All open group channels defined by their study result ID
    val Channels = Value("channels")
    // Session data (must be accompanied with a session version)
    val SessionData = Value("sessionData")
    // Session patches (must be accompanied with a session version)
    val SessionPatches = Value("sessionPatches")
    // Identifier of a session action (mandatory for patch requests and their responses)
    val SessionActionId = Value("sessionActionId")
    // Group session version (mandatory for session data or patches)
    val SessionVersion = Value("sessionVersion")
    // Defines if we check the version before applying the patch
    val SessionVersioning = Value("sessionVersioning")
    // Error message
    val ErrorMsg = Value("errorMsg")
    // Machine-readable error code
    val ErrorCode = Value("errorCode")
  }

  /**
   * Stable wire codes used by clients to classify failures.
   */
  object GroupErrorCode {
    val SessionVersionConflict = "SESSION_VERSION_CONFLICT"
    val SessionPatchFailed = "SESSION_PATCH_FAILED"
    val SessionUpdateRetriesExhausted = "SESSION_UPDATE_RETRIES_EXHAUSTED"
    val UnknownAction = "UNKNOWN_ACTION"
    val GroupNotFound = "GROUP_NOT_FOUND"
    val SessionWriteForbidden = "SESSION_WRITE_FORBIDDEN"
    val MessageDeliveryFailed = "MESSAGE_DELIVERY_FAILED"
  }

  /**
   * Message format used for communication in the group channel between the GroupDispatcher and
   * the group members. A GroupMsg contains a JSON node. If the JSON has an 'action' key, it is a group action
   * message. With 'tellWhom' the recipient can be specified.
   */
  case class GroupMsg(json: JsObject, tellWhom: TellWhom = TellWhom.Unknown)

}
