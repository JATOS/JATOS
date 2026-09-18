package cluster

import java.util.UUID
import javax.inject.Singleton

/**
 * Generates and identifies one running incarnation of a JATOS node.
 */
@Singleton
class NodeIdentity {
  val id: String = UUID.randomUUID().toString
}
