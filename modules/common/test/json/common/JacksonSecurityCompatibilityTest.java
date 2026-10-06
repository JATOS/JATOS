package json.common;

import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import exceptions.common.JatosException;
import jakarta.persistence.OneToMany;
import org.hibernate.collection.spi.PersistentSet;
import org.junit.Test;
import play.libs.Json;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Security limits must also preserve ordinary study data and unloaded Hibernate 6 associations.
 */
public class JacksonSecurityCompatibilityTest {

    private final DefaultJson json = new DefaultJson();

    public static class LazyAssociation {
        @OneToMany
        public Set<String> association;
    }

    @Test
    @SuppressWarnings("deprecation") // Exercise the mapper Play uses, as well as the application mapper.
    public void rejectsExcessiveNesting() {
        String nested = "[".repeat(1001) + "0" + "]".repeat(1001);
        JatosException error = assertThrows(JatosException.class, () -> json.jsonAsJsonNode(nested));
        assertTrue(error.getCause() instanceof StreamConstraintsException);
        assertThrows(StreamConstraintsException.class, () -> Json.newDefaultMapper().readTree(nested));
        assertThrows(StreamConstraintsException.class,
                () -> new StrictJson(json.mapper()).mapper().readTree(nested));
    }

    @Test
    public void rejectsExcessivelyLongNumbers() {
        String number = "1".repeat(1001);
        JatosException error = assertThrows(JatosException.class, () -> json.jsonAsJsonNode(number));
        assertTrue(error.getCause() instanceof StreamConstraintsException);
    }

    @Test
    public void retainsLargeStringsAndStudyDataPrecision() {
        String text = "Grüße 日本語 😀".repeat(100000);
        String encoded = json.objAsJson(Map.of("text", text));
        assertEquals(text, json.jsonAsJsonNode(encoded).get("text").asText());
        assertEquals(9007199254740993L, json.jsonAsJsonNode("9007199254740993").longValue());
    }

    @Test
    public void serializesUninitializedHibernate6CollectionWithoutLoadingIt() {
        PersistentSet<String> collection = new PersistentSet<>();
        assertFalse(collection.wasInitialized());
        LazyAssociation entity = new LazyAssociation();
        entity.association = collection;
        assertEquals("{\"association\":null}", json.objAsJson(entity));
        assertFalse(collection.wasInitialized());
    }
}
