package controllers.gui;

import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import general.common.RequestScope;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

import static controllers.gui.actionannotations.ApiTokenAuthAction.API_TOKEN;
import static controllers.gui.actionannotations.ApiTokenAuthAction.ApiTokenAuth;
import static models.common.User.Role.ADMIN;

/**
 * JATOS API Controller: interface for all requests possible via JATOS' API
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class Api extends Controller {

    private final Admin admin;
    private final Studies studies;
    private final StudyLinks studyLinks;
    private final ImportExport importExport;
    private final StudyResults studyResults;
    private final ComponentResults componentResults;

    @Inject
    Api(Admin admin, Studies studies, StudyLinks studyLinks, ImportExport importExport, StudyResults studyResults,
            ComponentResults componentResults) {
        this.admin = admin;
        this.studies = studies;
        this.studyLinks = studyLinks;
        this.importExport = importExport;
        this.studyResults = studyResults;
        this.componentResults = componentResults;
    }

    @ApiTokenAuth
    public Result testToken() {
        return ok(JsonUtils.asJson(RequestScope.get(API_TOKEN)));
    }

    @Transactional
    @ApiTokenAuth(ADMIN)
    public Result status() {
        return admin.status();
    }

    @Transactional
    @ApiTokenAuth(ADMIN)
    public Result logs(String filename) {
        return admin.logs(filename, -1, false);
    }

    @Transactional
    @ApiTokenAuth
    public Result studyAssetsSize(Long studyId) {
        return admin.studyAssetsSize(studyId);
    }

    @Transactional
    @ApiTokenAuth(ADMIN)
    public Result resultDataSize(Long studyId) {
        return admin.resultDataSize(studyId);
    }

    @Transactional
    @ApiTokenAuth(ADMIN)
    public Result resultFileSize(Long studyId) {
        return admin.resultFileSize(studyId);
    }

    @Transactional
    @ApiTokenAuth
    public Result studyLog(Long studyId) throws ForbiddenException, NotFoundException {
        return studies.studyLog(studyId, -1, true);
    }

    @Transactional
    @ApiTokenAuth
    public Result allWorkersByStudy(Long studyId) throws ForbiddenException, NotFoundException {
        return studies.allWorkers(studyId);
    }

    @Transactional
    @ApiTokenAuth
    public Result getStudyCodes(Long studyId, Long batchId, String type, String comment, Integer amount)
            throws ForbiddenException, NotFoundException, BadRequestException {
        return studyLinks.getStudyCodes(studyId, batchId, type, comment, amount);
    }

    @Transactional
    @ApiTokenAuth
    public Result exportStudy(Long studyId) throws ForbiddenException, NotFoundException {
        return importExport.exportStudy(studyId);
    }

    @Transactional
    @ApiTokenAuth
    public Result importStudy(Http.Request request, boolean overwriteProperties, boolean overwriteDir,
            boolean keepCurrentDirName, boolean renameDir) throws ForbiddenException, NotFoundException, IOException {
        return importExport.importStudyApi(request, overwriteProperties, overwriteDir, keepCurrentDirName, renameDir);
    }

    @Transactional
    @ApiTokenAuth
    public Result removeStudyResults(
            Http.Request request) throws ForbiddenException, BadRequestException, NotFoundException {
        return studyResults.remove(request);
    }

    @Transactional
    @ApiTokenAuth
    public Result removeComponentResults(
            Http.Request request) throws ForbiddenException, BadRequestException, NotFoundException {
        return componentResults.remove(request);
    }

    @Transactional
    @ApiTokenAuth
    public Result exportResultsData(Http.Request request, Boolean asPlainText)
            throws ForbiddenException, BadRequestException, NotFoundException {
        return importExport.exportResultsData(request, asPlainText);
    }

    @Transactional
    @ApiTokenAuth
    public Result exportSingleResultData(Long componentResultId) throws ForbiddenException, NotFoundException {
        return componentResults.exportSingleResultData(componentResultId);
    }

    @Transactional
    @ApiTokenAuth
    public Result exportSingleResultFile(Long componentResultId,
            String filename) throws ForbiddenException, NotFoundException {
        return importExport.exportSingleResultFile(componentResultId, filename);
    }

    @Transactional
    @ApiTokenAuth
    public Result exportResultsFiles(
            Http.Request request) throws IOException, ForbiddenException, BadRequestException, NotFoundException {
        return importExport.exportResultsFiles(request);
    }

    @Transactional
    @ApiTokenAuth
    public Result exportResults(Http.Request request) throws BadRequestException {
        return importExport.exportResults(request);
    }

    @Transactional
    @ApiTokenAuth
    public Result exportResultsMetadata(
            Http.Request request) throws ForbiddenException, BadRequestException, NotFoundException, IOException {
        return importExport.exportResultsMetadata(request);
    }

}
