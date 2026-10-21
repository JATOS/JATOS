package auth.gui;

import general.common.Common;
import models.common.User;
import org.junit.Test;
import java.util.EnumSet;
import static models.common.User.Role.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AuthRoleTest {
    @Test
    public void annotationRoleGateHonorsSuperuserConfigurationWithoutGrantingAdmin() {
        User user = new User("super", "Super", "super@example.org", VIEWER);
        user.updateRoles(SUPERUSER);
        try (var common = mockStatic(Common.class)) {
            common.when(Common::isUserRoleAllowSuperuser).thenReturn(true);
            assertTrue(AuthAction.hasAllowedRole(user, EnumSet.of(USER, SUPERUSER)));
            assertFalse(AuthAction.hasAllowedRole(user, EnumSet.of(USER)));
            assertFalse(AuthAction.hasAllowedRole(user, EnumSet.of(ADMIN)));
            common.when(Common::isUserRoleAllowSuperuser).thenReturn(false);
            assertFalse(AuthAction.hasAllowedRole(user, EnumSet.of(USER, SUPERUSER)));
            assertTrue(AuthAction.hasAllowedRole(user, EnumSet.of(VIEWER, USER, SUPERUSER)));
            user.updateRoles(USER);
            assertTrue(AuthAction.hasAllowedRole(user, EnumSet.of(USER, SUPERUSER)));
        }
    }
}
