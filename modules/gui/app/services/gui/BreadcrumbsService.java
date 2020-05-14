package services.gui;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.gui.routes;
import models.common.*;
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
    public static final String RESULTS = "Results";
    public static final String WORKER_AND_BATCH_MANAGER = "Worker & Batch Manager";
    public static final String USER_MANAGER = "User Manager";

    public String generateForHome() {
        return generateForHome(null);
    }

    public String generateForHome(String last) {
        Breadcrumbs breadcrumbs = new Breadcrumbs();
        if (last != null) {
            breadcrumbs.addBreadcrumb(HOME, routes.Home.home().url());
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
        breadcrumbs.addBreadcrumb(HOME, routes.Home.home().url());
        if (last != null) {
            breadcrumbs.addBreadcrumb(user.toString(), routes.Users.profile(user.getUsername()).url());
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
        breadcrumbs.addBreadcrumb(HOME, routes.Home.home().url());
        if (last != null) {
            breadcrumbs.addBreadcrumb(study.getTitle(), routes.Studies.study(study.getId()).url());
            breadcrumbs.addBreadcrumb(last, "");
        } else {
            breadcrumbs.addBreadcrumb(study.getTitle(), "");
        }
        return asJson(breadcrumbs);
    }

    public String generateForBatch(Study study, Batch batch, String last) {
        Breadcrumbs breadcrumbs = new Breadcrumbs();
        breadcrumbs.addBreadcrumb(HOME, routes.Home.home().url());
        breadcrumbs.addBreadcrumb(study.getTitle(), routes.Studies.study(study.getId()).url());
        breadcrumbs.addBreadcrumb(batch.getTitle(),
                routes.Batches.workerAndBatchManager(study.getId()).url());
        if (last != null) {
            breadcrumbs.addBreadcrumb(last, "");
        }
        return asJson(breadcrumbs);
    }

    public String generateForGroup(Study study, Batch batch, GroupResult groupResult, String last) {
        Breadcrumbs breadcrumbs = new Breadcrumbs();
        breadcrumbs.addBreadcrumb(HOME, routes.Home.home().url());
        breadcrumbs.addBreadcrumb(study.getTitle(), routes.Studies.study(study.getId()).url());
        breadcrumbs.addBreadcrumb(batch.getTitle(),
                routes.Batches.workerAndBatchManager(study.getId()).url());
        breadcrumbs.addBreadcrumb("Group " + groupResult.getId(), "");
        if (last != null) {
            breadcrumbs.addBreadcrumb(last, "");
        }
        return asJson(breadcrumbs);
    }

    public String generateForWorker(Worker worker, String last) {
        Breadcrumbs breadcrumbs = new Breadcrumbs();
        breadcrumbs.addBreadcrumb(HOME, routes.Home.home().url());
        breadcrumbs.addBreadcrumb("Worker " + worker.getId(), "");
        breadcrumbs.addBreadcrumb(last, "");
        return asJson(breadcrumbs);
    }

    public String generateForComponent(Study study, Component component, String last) {
        Breadcrumbs breadcrumbs = new Breadcrumbs();
        breadcrumbs.addBreadcrumb(HOME, routes.Home.home().url());
        breadcrumbs.addBreadcrumb(study.getTitle(), routes.Studies.study(study.getId()).url());
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
