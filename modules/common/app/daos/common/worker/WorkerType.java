package daos.common.worker;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Strings;
import exceptions.common.BadRequestException;
import models.common.workers.*;

/**
 * Enum of all worker types.
 *
 * Do not use WorkerType.valueOf to parse a worker type String - use WorkerType.fromWireValue instead.
 */
public enum WorkerType {
    JATOS(JatosWorker.WORKER_TYPE, JatosWorker.SHORT_WORKER_TYPE, JatosWorker.UI_WORKER_TYPE),
    PERSONAL_SINGLE(PersonalSingleWorker.WORKER_TYPE, PersonalSingleWorker.SHORT_WORKER_TYPE, PersonalSingleWorker.UI_WORKER_TYPE),
    PERSONAL_MULTIPLE(PersonalMultipleWorker.WORKER_TYPE, PersonalMultipleWorker.SHORT_WORKER_TYPE, PersonalMultipleWorker.UI_WORKER_TYPE),
    GENERAL_SINGLE(GeneralSingleWorker.WORKER_TYPE, GeneralSingleWorker.SHORT_WORKER_TYPE, GeneralSingleWorker.UI_WORKER_TYPE),
    GENERAL_MULTIPLE(GeneralMultipleWorker.WORKER_TYPE, GeneralMultipleWorker.SHORT_WORKER_TYPE, GeneralMultipleWorker.UI_WORKER_TYPE),
    MT(MTWorker.WORKER_TYPE, MTWorker.SHORT_WORKER_TYPE, MTWorker.UI_WORKER_TYPE),
    MT_SANDBOX(MTSandboxWorker.WORKER_TYPE, MTSandboxWorker.SHORT_WORKER_TYPE, MTSandboxWorker.UI_WORKER_TYPE),
    NONE(null, null, null);

    private final String value;
    private final String shortValue;
    private final String uiValue;

    WorkerType(String value, String shortValue, String uiValue) {
        this.value = value;
        this.shortValue = shortValue;
        this.uiValue = uiValue;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public String shortValue() {
        return shortValue;
    }

    public String uiValue() {
        return uiValue;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * Parses a stored/transported worker type string (e.g. from StudyLink/StudyResult/IdCookie).
     * Throws BadRequestException for unknown values.
     *
     * Jackson also uses this method for JSON deserialization.
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static WorkerType fromWireValue(String workerType) {
        if (workerType == null) {
            throw new BadRequestException("Unknown worker type");
        }
        for (WorkerType t : values()) {
            if (t.value.equalsIgnoreCase(workerType) || t.shortValue.equalsIgnoreCase(workerType)) {
                return t;
            }
        }
        throw new BadRequestException("Unknown worker type");
    }

    public static WorkerType fromWireValueInclNone(String workerType) {
        if (Strings.isNullOrEmpty(workerType)) {
            return NONE;
        } else {
            return fromWireValue(workerType);
        }
    }
}