package services.gui;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.common.Batch;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.common.workers.Worker;
import models.gui.Breadcrumbs;
import play.libs.Json;
import utils.common.JsonUtils;

import javax.inject.Singleton;

/**
 * Provides breadcrumbs for different views of JATOS' GUI.
 *
 * @author Kristian Lange
 */
@Singleton
public class BreadcrumbsService {

    public static final String HOME = "Home";
    public static final String WORKERS = "Workers";
    public static final String WORKER_SETUP = "Worker Setup";
    public static final String MECHANICAL_TURK_HIT_LAYOUT_SOURCE_CODE =
            "Mechanical Turk HIT Layout Source Code";
    public static final String RESULTS = "Results";
    public static final String BATCH_MANAGER = "Batch Manager";
    public static final String USER_MANAGER = "User Manager";

    public String generateForHome() {
        return generateForHome(null);
    }

    public String generateForHome(String last) {
        Breadcrumbs breadcrumbs = new Breadcrumbs();
        if (last != null) {
            breadcrumbs.addBreadcrumb(HOME,
                    controllers.gui.routes.Home.home().url());
            breadcrumbs.addBreadcrumb(last, "");
        } else {
            breadcrumbs.addBreadcrumb(HOME, "");
        }
        return asJson(breadcrumbs);
    }

    public String generateForUser(User user) {
        return generateForUser(user, null);
    }

    public String generateForUser(User user, String last) {
        Breadcrumbs breadcrumbs = new Breadcrumbs();
        breadcrumbs.addBreadcrumb(HOME,
                controllers.gui.routes.Home.home().url());
        if (last != null) {
            breadcrumbs.addBreadcrumb(user.toString(),
                    controllers.gui.routes.Users.profile(user.getEmail())
                            .url());
            breadcrumbs.addBreadcrumb(last, "");
        } else {
            breadcrumbs.addBreadcrumb(user.toString(), "");
        }
        return asJson(breadcrumbs);
    }

    public String generateForStudy(Study study) {
        return generateForStudy(study, null);
    }

    public String generateForStudy(Study study, String last) {
        Breadcrumbs breadcrumbs = new Breadcrumbs();
        breadcrumbs.addBreadcrumb(HOME,
                controllers.gui.routes.Home.home().url());
        if (last != null) {
            breadcrumbs.addBreadcrumb(study.getTitle(),
                    controllers.gui.routes.Studies.study(study.getId()).url());
            breadcrumbs.addBreadcrumb(last, "");
        } else {
            breadcrumbs.addBreadcrumb(study.getTitle(), "");
        }
        return asJson(breadcrumbs);
    }

    public String generateForBatch(Study study, Batch batch, String last) {
        Breadcrumbs breadcrumbs = new Breadcrumbs();
        breadcrumbs.addBreadcrumb(HOME,
                controllers.gui.routes.Home.home().url());
        breadcrumbs.addBreadcrumb(study.getTitle(),
                controllers.gui.routes.Studies.study(study.getId()).url());
        breadcrumbs.addBreadcrumb(BATCH_MANAGER, controllers.gui.routes.Batches
                .batchManager(study.getId()).url());
        breadcrumbs.addBreadcrumb(batch.getTitle(), "");
        if (last != null) {
            breadcrumbs.addBreadcrumb(last, "");
        }
        return asJson(breadcrumbs);
    }

    public String generateForWorker(Worker worker) {
        return generateForWorker(worker, null);
    }

    public String generateForWorker(Worker worker, String last) {
        Breadcrumbs breadcrumbs = new Breadcrumbs();
        breadcrumbs.addBreadcrumb(HOME,
                controllers.gui.routes.Home.home().url());
        breadcrumbs.addBreadcrumb("Worker " + worker.getId(), "");
        breadcrumbs.addBreadcrumb(last, "");
        return asJson(breadcrumbs);
    }

    public String generateForComponent(Study study, Component component,
            String last) {
        Breadcrumbs breadcrumbs = new Breadcrumbs();
        breadcrumbs.addBreadcrumb(HOME,
                controllers.gui.routes.Home.home().url());
        breadcrumbs.addBreadcrumb(study.getTitle(),
                controllers.gui.routes.Studies.study(study.getId()).url());
        breadcrumbs.addBreadcrumb(component.getTitle(), "");
        breadcrumbs.addBreadcrumb(last, "");
        return asJson(breadcrumbs);
    }

    private String asJson(Breadcrumbs breadcrumbs) {
        ArrayNode arrayNode = Json.mapper().createArrayNode();
        for (Breadcrumbs.Breadcrumb breadcrumb : breadcrumbs.getBreadcrumbs()) {
            ObjectNode node = Json.mapper().createObjectNode();
            node.put("name", breadcrumb.name);
            node.put("url", breadcrumb.url);
            arrayNode.add(node);
        }
        return JsonUtils.asJson(arrayNode);
    }

}
