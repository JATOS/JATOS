package models.common.workers;

import daos.common.worker.WorkerType;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class WorkerTypeConverter implements AttributeConverter<WorkerType, String> {

    @Override
    public String convertToDatabaseColumn(WorkerType attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public WorkerType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : WorkerType.fromWireValue(dbData);
    }
}
