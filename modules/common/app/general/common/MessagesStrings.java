package general.common;

import javax.inject.Singleton;

/**
 * Helper class with methods that generate error strings.
 */
@Singleton
public class MessagesStrings {

    // General
    public static final String MISSING_TITLE = "Missing title";
    public static final String TITLE_TOO_LONG = "Title too long";
    public static final String MISSING_NAME = "Missing name";
    public static final String NAME_TOO_LONG = "Name too long";
    public static final String MISSING_USERNAME = "Missing username";
    public static final String USERNAME_TOO_LONG = "Username too long";
    public static final String USERNAME_INVALID = "Username invalid (only alphabetic characters, numbers and -_@"
            + ".+&'=~)";
    public static final String MISSING_DIR_NAME = "Missing directory name";
    public static final String DIR_NAME_TOO_LONG = "Directory name too long";
    public static final String COMPONENT_INVALID = "Component is invalid";

    // Edit HTML forms
    public static final String INVALID_JSON_FORMAT = "Invalid JSON format";
    public static final String NO_HTML_ALLOWED = "No HTML allowed";
    public static final String PATH_TOO_LONG = "Path too long";
    public static final String COMMENT_TOO_LONG = "Comment too long";
    public static final String STUDY_ENTRY_MSG_TOO_LONG = "Study entry message too long";
    public static final String NOT_A_VALID_PATH_YOU_CAN_LEAVE_IT_EMPTY = "Not a valid path or filename. Remember to "
            + "use '/' as folder separator, and to include the file extension. You can leave it empty for now.";
    // User
    public static final String INVALID_USER_OR_PASSWORD = "Invalid user or password";
    public static final String FAILED_THREE_TIMES = "You have only three sign-in attempts per minute.";
    public static final String PASSWORDS_DONT_MATCH = "Passwords don't match";
    public static final String PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS = "Passwords shouldn't be empty strings";
    public static final String COULDNT_OPEN_LOG = "Error: Could not open log file";
    public static final String LOG_CUT = "--- Log is cut here. Download it to get the whole file. ---";
    public static final String ADMIN_NOT_ALLOWED_TO_REMOVE_HIS_OWN_ADMIN_ROLE = "Sorry, it's not possible to remove "
            + "your own admin rights. Although you can ask another admin to remove them for you.";
    public static final String NOT_ALLOWED_REMOVE_ADMINS_ADMIN_RIGHTS = "It's not possible to remove 'admin's admin "
            + "rights.";

    // Export / import
    public static final String FILE_MISSING = "File missing";
    public static final String FILE_NOT_EXIST_OR_DIR = "File doesn't exists or is a directory";

    // Other
    public static final String COMPONENT_DELETED_BUT_FILES_NOT = "Component deleted, but all files (e.g. its HTML "
            + "file) in study assets remain untouched.";

    public static String studyAssetsOverwritten(String studyAssetsName, Long studyId, String studyTitle) {
        return "Assets \"" + studyAssetsName + "\" of study \"" + studyTitle + "\" (ID " + studyId
                + ") were overwritten. " + goToThisStudyLink(studyId);
    }

    public static String studysPropertiesOverwritten(Long studyId, String studyTitle) {
        return "Properties of of study \"" + studyTitle + "\" (ID " + studyId + ") were overwritten. "
                + goToThisStudyLink(studyId);
    }

    public static String importedNewStudy(String studyAssetsName, Long studyId, String studyTitle) {
        return "Newly imported study \"" + studyTitle + "\" (ID " + studyId + ") with study assets \"" + studyAssetsName
                + "\". " + goToThisStudyLink(studyId);
    }

    public static String goToThisStudyLink(Long studyId) {
        return "<a href=\"" + Common.getJatosUrlBasePath() + "jatos/" + studyId + "\">Go to this study.</a>";
    }

    public static String userPasswordMinLength(int minLength) {
        return "Passwords should be at least " + minLength + " characters long.";
    }

    public static String componentCloneHtmlNotCloned(String htmlFilePath) {
        return "Successfully cloned component but couldn't copy HTML file \"" + htmlFilePath + "\".";
    }

    public static String htmlFilePathEmpty(Long componentId) {
        return "Component " + componentId + "'s HTML file path is empty.";
    }

    public static String htmlFilePathNotExist(String studyDirName, String htmlFilePath) {
        return "HTML file '" + htmlFilePath + "' in study assets '" + studyDirName + "' couldn't be found. "
                + "Please change this in the component's properties.";
    }

    public static String couldntGeneratePathToFileOrDir(String fileStr) {
        return "Couldn't generate path to file or directory \"" + fileStr + "\".";
    }

    public static String pathNotAbsolute(String path) {
        return "Path \"" + path + "\" should be absolute.";
    }

    public static String pathNotRelative(String path) {
        return "Path \"" + path + "\" should be relative.";
    }

    public static String filePathIsntFile(String fileName) {
        return "File path \"" + fileName + "\" doesn't lead to a file.";
    }

    public static String dirPathIsntDir(String dirName) {
        return "Directory path \"" + dirName + "\" doesn't lead to a directory.";
    }

    public static String studyAssetsDirNotCreatedBecauseExists(String dirName) {
        return "Study assets' directory (" + dirName + ") couldn't be created because it already exists.";
    }

    public static String studyAssetsNotRenamedBecauseExists(String oldDirName, String newDirName) {
        return "Study assets directory \"" + oldDirName + "\" couldn't be renamed to \"" + newDirName
                + "\" because it already exists.";
    }

    public static String htmlFileNotRenamedBecauseExists(String oldFilePath, String newFilePath) {
        return "HTML file \"" + oldFilePath + "\" couldn't be renamed to \"" + newFilePath
                + "\" because it already exists.";
    }

}
