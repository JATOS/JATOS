package controllers.gui.api;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import auth.gui.AuthAction.Auth;
import com.fasterxml.jackson.databind.JsonNode;
import exceptions.common.BadRequestException;
import exceptions.common.InternalServerErrorException;
import exceptions.common.JatosException;
import exceptions.common.NotFoundException;
import general.common.ApiEnvelope;
import http.common.Http.Context;
import http.common.HttpUtils;
import json.common.DirectoryStructureToJson;
import models.common.Study;
import models.common.User;
import play.Logger;
import play.core.utils.HttpHeaderParameterEncoding;
import play.libs.Files.TemporaryFile;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Result;
import services.gui.ApiService;
import services.gui.AuthorizationService;
import services.gui.StudyService;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static auth.gui.AuthAction.AuthMethod.Type.SESSION;
import static auth.gui.AuthAction.AuthMethod.Type.TOKEN;
import static auth.gui.AuthAction.SIGNEDIN_USER;
import static general.common.ApiEnvelope.ErrorCode.FILE_ALREADY_EXISTS;
import static general.common.ApiEnvelope.ErrorCode.MISSING_FILE;
import static models.common.User.Role.USER;

@Singleton
public class StudyAssetsApi extends Controller {

    private static final Logger.ALogger LOGGER = Logger.of(StudyAssetsApi.class);

    private final StudyService studyService;
    private final AuthorizationService authorizationService;
    private final ApiService apiService;
    private final IOUtils ioUtils;

    @Inject
    public StudyAssetsApi(StudyService studyService,
                          AuthorizationService authorizationService,
                          ApiService apiService,
                          IOUtils ioUtils) {
        this.studyService = studyService;
        this.authorizationService = authorizationService;
        this.apiService = apiService;
        this.ioUtils = ioUtils;
    }

    /**
     * Gets the study assets directory structure as JSON
     *
     * @param id      Study's ID or UUID
     * @param flatten Flag, if set to `true` the returned JSON will be a flat list of files (no tree, no directories).
     *                If `false`, the returned JSON will have a tree-like structure and include directories. Default is
     *                `false`.
     * @return JSON with study assets directory structure
     */
    @Async(Executor.STUDY_ASSETS)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    public Result getStudyAssetsStructure(String id, boolean flatten) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        Path base;
        try {
            base = ioUtils.getStudyAssetsDir(study.getDirName());
        } catch (IOException e) {
            throw new NotFoundException("Study assets directory couldn't be found");
        }
        JsonNode structure = DirectoryStructureToJson.get(base, flatten);
        return ok(ApiEnvelope.wrap(structure).asJsonNode());
    }

    /**
     * Upload a file to a study assets folder.
     *
     * @param id       Study's ID or UUID
     * @param filepath Supposed path of the uploaded file in the study assets directory. If it is null, "", "/" or ", ."
     *                 it will be ignored and the uploaded file saved in the top-level of the assets under the uploaded
     *                 file's name. If it is a directory, the filename is taken from the uploaded file. If it ends with
     *                 a filename, the uploaded file will be renamed to this name. All non-existing subdirectories will
     *                 be created. Existing files will be overwritten. The path can be URL encoded but doesn't have to
     *                 be.
     */
    @Async(Executor.STUDY_ASSETS)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    public Result uploadStudyAssetsFile(play.mvc.Http.Request request, String id, String filepath) {
        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser, true);

        MultipartFormData<TemporaryFile> body = request.body().asMultipartFormData();
        if (body == null) {
            throw new BadRequestException("File missing", MISSING_FILE);
        }
        MultipartFormData.FilePart<TemporaryFile> filePart = body.getFile("studyAssetsFile");
        if (filePart == null) {
            throw new BadRequestException("File missing", MISSING_FILE);
        }
        Path uploadedFile = filePart.getRef().path();

        try {
            Path assetsFilePath = apiService.getAssetsFilePath(filepath, filePart.getFilename(), study);
            Path parent = assetsFilePath.getParent();
            if (parent != null) {
                // Make sure the directory that will contain the uploaded file exists.
                Files.createDirectories(parent);
            }

            boolean overwritten = IOUtils.moveFileAndDetectOverwrite(uploadedFile, assetsFilePath);
            String msg = overwritten ? "File overwritten successfully" : "File uploaded successfully";
            JsonNode envelope = ApiEnvelope.wrap(msg).asJsonNode();
            return overwritten ? ok(envelope) : created(envelope);
        } catch (FileAlreadyExistsException e) {
            throw new BadRequestException("File already exists but is of a different type", FILE_ALREADY_EXISTS);
        } catch (IOException e) {
            throw new JatosException(e.getMessage(), e, ApiEnvelope.ErrorCode.IO_ERROR);
        }
    }

    /**
     * Download a file from a study assets folder.
     *
     * @param id       Study's ID or UUID
     * @param filepath Path to the file in the study assets directory that is supposed to be downloaded. The path can be
     *                 URL encoded but doesn't have to be. Directories cannot be downloaded.
     */
    @Async(Executor.STUDY_ASSETS)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    public Result downloadStudyAssetsFile(String id, String filepath) throws IOException {
        String filepathUrlDecoded = HttpUtils.urlDecode(filepath);
        String finalFilepath = filepathUrlDecoded.startsWith("/")
                ? filepathUrlDecoded.substring(1)
                : filepathUrlDecoded;

        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        Path file = ioUtils.getFileInStudyAssetsDir(study.getDirName(), finalFilepath);
        if (!Files.isRegularFile(file)) throw new NotFoundException("File '" + finalFilepath + "' couldn't be found.");
        String cdHeader = "attachment; " + HttpHeaderParameterEncoding.encode("filename", file.getFileName().toString());
        Context.current().response().setHeader(CONTENT_DISPOSITION, cdHeader);
        // todo put in .withHeader
        return ok().sendPath(file);
    }

    /**
     * Deletes a file in the study assets directory.
     *
     * @param id       Study's ID or UUID
     * @param filepath Path to the file in the study assets directory that is supposed to be deleted. The path can be
     *                 URL encoded but doesn't have to be. Directories cannot be deleted.
     */
    @Async(Executor.STUDY_ASSETS)
    @Auth(roles = USER, types = {TOKEN, SESSION})
    public Result deleteStudyAssetsFile(String id, String filepath) {
        String filepathUrlDecoded = HttpUtils.urlDecode(filepath);
        String finalFilepath = filepathUrlDecoded.startsWith("/")
                ? filepathUrlDecoded.substring(1)
                : filepathUrlDecoded;

        Study study = studyService.getStudyFromIdOrUuid(id);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser, true);

        try {
            Path file = ioUtils.getFileInStudyAssetsDir(study.getDirName(), finalFilepath);
            if (Files.isDirectory(file)) {
                throw new JatosException("Directories can't be deleted.", ApiEnvelope.ErrorCode.IO_ERROR);
            }
            Files.delete(file);
        } catch (NoSuchFileException e) {
            throw new NotFoundException("File '" + finalFilepath + "' couldn't be found.");
        } catch (IOException e) {
            LOGGER.info(".deleteStudyAssetsFile: " + e.getLocalizedMessage());
            throw new InternalServerErrorException("Error writing file");
        }
        return ok(ApiEnvelope.wrap("File deleted successfully").asJsonNode());
    }

}
