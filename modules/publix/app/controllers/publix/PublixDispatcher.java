package controllers.publix;

import controllers.publix.workers.*;
import daos.common.worker.WorkerType;
import exceptions.common.BadRequestException;
import models.common.workers.*;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the correct Publix implementation for a given worker type.
 */
@Singleton
public class PublixDispatcher {

    private final Map<WorkerType, Provider<? extends IPublix>> publixByWorkerType;

    @Inject
    public PublixDispatcher(Provider<JatosPublix> jatosPublix,
                            Provider<PersonalSinglePublix> personalSinglePublix,
                            Provider<PersonalMultiplePublix> personalMultiplePublix,
                            Provider<GeneralSinglePublix> generalSinglePublix,
                            Provider<GeneralMultiplePublix> generalMultiplePublix,
                            Provider<MTPublix> mtPublix) {

        Map<WorkerType, Provider<? extends IPublix>> map = new HashMap<>();
        map.put(WorkerType.JATOS, jatosPublix);
        map.put(WorkerType.PERSONAL_SINGLE, personalSinglePublix);
        map.put(WorkerType.PERSONAL_MULTIPLE, personalMultiplePublix);
        map.put(WorkerType.GENERAL_SINGLE, generalSinglePublix);
        map.put(WorkerType.GENERAL_MULTIPLE, generalMultiplePublix);

        // Treat MTWorker like MTSandboxWorker (same implementation)
        map.put(WorkerType.MT, mtPublix);
        map.put(WorkerType.MT_SANDBOX, mtPublix);

        this.publixByWorkerType = Collections.unmodifiableMap(map);
    }

    public IPublix forWorkerType(WorkerType workerType) {
        Provider<? extends IPublix> provider = publixByWorkerType.get(workerType);
        if (provider == null) {
            throw new BadRequestException("Unknown worker type");
        }
        return provider.get();
    }

}