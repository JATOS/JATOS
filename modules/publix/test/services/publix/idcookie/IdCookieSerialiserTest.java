package services.publix.idcookie;

import controllers.publix.workers.JatosPublix.JatosRun;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for IdCookieSerialiser
 */
public class IdCookieSerialiserTest {

    @Test
    public void asCookieValueString_buildsInExpectedOrder() {
        IdCookieModel m = new IdCookieModel();
        m.setBatchId(1L);
        m.setComponentId(2L);
        m.setComponentPosition(3);
        m.setComponentResultId(4L);
        m.setCreationTime(5L);
        m.setStudyAssets("studyAssetsDir");
        m.setUrlBasePath("/jatos");
        m.setJatosRun(JatosRun.RUN_STUDY);
        m.setStudyId(6L);
        m.setStudyResultId(7L);
        m.setStudyResultUuid("uuid-123");
        m.setWorkerId(8L);
        m.setWorkerType("GeneralSingle");

        IdCookieSerialiser s = new IdCookieSerialiser();
        String cookie = s.asCookieValueString(m);

        String expected = "batchId=1&" +
                "componentId=2&" +
                "componentPos=3&" +
                "componentResultId=4&" +
                "creationTime=5&" +
                "studyAssets=studyAssetsDir&" +
                "urlBasePath=/jatos&" +
                "jatosRun=RUN_STUDY&" +
                "studyId=6&" +
                "studyResultId=7&" +
                "studyResultUuid=uuid-123&" +
                "workerId=8&" +
                "workerType=GeneralSingle";

        assertEquals(expected, cookie);
    }
}
