package general.common;

import org.junit.Test;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigException;
import play.mvc.Http;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.Mockito.mock;
import play.libs.Json;

import static org.junit.Assert.*;

public class CommonTest {

    @Test
    public void parsesBareDurationsUsingTheRequestedDefaultUnit() {
        assertEquals(Duration.ofMinutes(12), duration("12", ChronoUnit.MINUTES));
        assertEquals(Duration.ofSeconds(12), duration("\" 12 \"", ChronoUnit.SECONDS));
        assertEquals(Duration.ofMillis(-2), duration("\"-2\"", ChronoUnit.MILLIS));
        assertEquals(Duration.ZERO, duration("0", ChronoUnit.SECONDS));
    }

    @Test
    public void explicitDurationUnitsOverrideTheDefault() {
        assertEquals(Duration.ofMillis(1500), duration("1500ms", ChronoUnit.MINUTES));
        assertEquals(Duration.ofSeconds(90), duration("1.5m", ChronoUnit.SECONDS));
        assertThrows(ConfigException.class, () -> duration("\"invalid\"", ChronoUnit.SECONDS));
    }

    private Duration duration(String value, ChronoUnit unit) {
        return Common.getDurationWithDefaultUnit(ConfigFactory.parseString("value = " + value), "value", unit);
    }

    @Test
    public void acceptsNativeListsAndEnvironmentStyleArrayStrings() throws Exception {
        assertEquals(List.of("openid", "profile"), parse("getStringList", "[openid, profile]"));
        assertEquals(List.of("openid", "profile"), parse("getStringList", "\"[openid, profile]\""));
        assertEquals(List.of(), parse("getStringList", "[]"));
        assertThrows(ConfigException.class, () -> parse("getStringList", "42"));
    }

    @Test
    public void normalizesAbsolutePathsAndExpandsTheHomeDirectory() throws Exception {
        var root = java.nio.file.Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();
        String path = root.resolve("studies/../assets").toString();
        assertEquals(root.resolve("assets").toString(), parse("obtainPath", Json.stringify(Json.toJson(path))));
        assertEquals(java.nio.file.Path.of(System.getProperty("user.home"), "assets").normalize().toString(),
                parse("obtainPath", "\"~/studies/../assets\""));
    }

    @Test
    public void validatesPkcePolicies() throws Exception {
        for (String mode : List.of("off", "auto", "required")) {
            assertEquals(mode, parse("readPkceMode", mode));
        }
        assertThrows(IllegalArgumentException.class, () -> parse("readPkceMode", "disabled"));
        assertThrows(IllegalArgumentException.class, () -> parse("readPkceMode", "AUTO"));
    }

    // Exercise configuration helpers without initializing Common's process-wide static configuration.
    private Object parse(String methodName, String value) throws Exception {
        var method = Common.class.getDeclaredMethod(methodName, Config.class, String.class);
        method.setAccessible(true);
        try {
            return method.invoke(mock(Common.class), ConfigFactory.parseString("value = " + value), "value");
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            throw e;
        }
    }

    @Test
    public void parsesSameSiteCaseInsensitivelyAndIgnoresUnknownValues() throws Exception {
        var method = Common.class.getDeclaredMethod("fillIdCookiesSameSite", Config.class);
        method.setAccessible(true);
        for (var mode : Http.Cookie.SameSite.values()) {
            assertEquals(mode, method.invoke(mock(Common.class), ConfigFactory.parseString(
                    "jatos.idCookies.sameSite = " + mode.name().toLowerCase(java.util.Locale.ROOT))));
        }
        assertNull(method.invoke(mock(Common.class), ConfigFactory.parseString("jatos.idCookies.sameSite = unknown")));
    }
}
