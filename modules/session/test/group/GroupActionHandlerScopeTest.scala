package group

import org.junit.Assert._
import org.junit._
import play.api.libs.json.Json

/**
 * Unit tests for GroupActionHandler.isPatchWithinMemberScope, the authorization check that (when
 * jatos.groupSession.memberScopedWrites is on) restricts a member's group session patch to its own
 * "/<studyResultId>" subtree.
 */
class GroupActionHandlerScopeTest {

  private val groupActionHandler = new GroupActionHandler(null, null, null)

  private val memberId = 123L

  private def within(patchJson: String): Boolean =
    groupActionHandler.isPatchWithinMemberScope(Json.parse(patchJson), memberId)

  @Test
  def add_toOwnMemberRoot_isAllowed(): Unit = {
    // This is exactly what jatos.groupSession.set(jatos.groupMemberId, value) sends.
    assertTrue(within("""[{"op":"add","path":"/123","value":{"x":1}}]"""))
  }

  @Test
  def add_toOwnSubtree_isAllowed(): Unit = {
    assertTrue(within("""[{"op":"add","path":"/123/score","value":5}]"""))
    assertTrue(within("""[{"op":"replace","path":"/123/nested/deep","value":true}]"""))
    assertTrue(within("""[{"op":"remove","path":"/123/score"}]"""))
  }

  @Test
  def add_toSharedRoot_isAllowed(): Unit = {
    assertTrue(within("""[{"op":"add","path":"/shared","value":{"global":true}}]"""))
  }

  @Test
  def write_toSharedSubtree_isAllowed(): Unit = {
    assertTrue(within("""[{"op":"add","path":"/shared/score","value":5}]"""))
    assertTrue(within("""[{"op":"replace","path":"/shared/nested/deep","value":true}]"""))
    assertTrue(within("""[{"op":"remove","path":"/shared/score"}]"""))
  }

  @Test
  def write_toAnotherMember_isRejected(): Unit = {
    assertFalse(within("""[{"op":"add","path":"/999","value":{"forged":true}}]"""))
    assertFalse(within("""[{"op":"replace","path":"/999","value":{"forged":true}}]"""))
    assertFalse(within("""[{"op":"remove","path":"/999"}]"""))
  }

  @Test
  def prefixConfusion_isRejected(): Unit = {
    // "/1234" must NOT count as inside member 123's subtree.
    assertFalse(within("""[{"op":"replace","path":"/1234","value":1}]"""))
    // "/shared123" must NOT count as inside the shared subtree.
    assertFalse(within("""[{"op":"replace","path":"/shared123","value":1}]"""))
  }

  @Test
  def clearWholeSession_isRejected(): Unit = {
    assertFalse(within("""[{"op":"remove","path":"/"}]"""))
  }

  @Test
  def mixedBatch_withOneOutOfScopeOp_isRejected(): Unit = {
    // A single out-of-scope op in an otherwise-valid batch fails the whole patch.
    assertFalse(within(
      """[{"op":"add","path":"/123/a","value":1},{"op":"add","path":"/999/b","value":2}]"""))
  }

  @Test
  def move_requiresBothPathAndFromInScope(): Unit = {
    assertTrue(within("""[{"op":"move","from":"/123/a","path":"/123/b"}]"""))
    assertTrue(within("""[{"op":"move","from":"/shared/a","path":"/shared/b"}]"""))
    assertTrue(within("""[{"op":"move","from":"/123/a","path":"/shared/b"}]"""))
    assertTrue(within("""[{"op":"move","from":"/shared/a","path":"/123/b"}]"""))
    // Moving another member's data out (deletes their "/999/a") is a write to their subtree.
    assertFalse(within("""[{"op":"move","from":"/999/a","path":"/123/b"}]"""))
    assertFalse(within("""[{"op":"move","from":"/123/a","path":"/999/b"}]"""))
    assertFalse(within("""[{"op":"move","from":"/999/a","path":"/shared/b"}]"""))
    assertFalse(within("""[{"op":"move","from":"/shared/a","path":"/999/b"}]"""))
  }

  @Test
  def copy_sourceMayBeAnywhere_targetMustBeInScope(): Unit = {
    // copy only reads 'from', so reading another member's data into your own subtree is allowed.
    assertTrue(within("""[{"op":"copy","from":"/999/a","path":"/123/a"}]"""))
    assertTrue(within("""[{"op":"copy","from":"/999/a","path":"/shared/a"}]"""))
    assertFalse(within("""[{"op":"copy","from":"/123/a","path":"/999/a"}]"""))
  }

  @Test
  def test_op_isReadOnly_andAllowedAnywhere(): Unit = {
    assertTrue(within("""[{"op":"test","path":"/999","value":{"x":1}}]"""))
  }

  @Test
  def unknownOp_isRejected(): Unit = {
    assertFalse(within("""[{"op":"bogus","path":"/123"}]"""))
  }

  @Test
  def nonArrayPatch_isRejected(): Unit = {
    assertFalse(within("""{"op":"add","path":"/123","value":1}"""))
  }

  @Test
  def emptyPatch_isAllowed(): Unit = {
    // Nothing is written, so there is nothing to authorize.
    assertTrue(within("""[]"""))
  }
}
