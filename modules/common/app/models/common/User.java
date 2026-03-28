package models.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import models.common.workers.JatosWorker;

import javax.persistence.*;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static utils.common.JsonUtils.JsonForApi;

/**
 * DB entity of a user. Used for JSON marshalling and JPA persistence.
 *
 * @author Kristian Lange
 */
@Entity
@Table(name = "User")
public class User {

    /**
     * Roles are used for authorization within JATOS GUI
     */
    public enum Role {
        NONE, // No role; used as default if no role is set
        USER, // Normal JATOS user. Can view studies and their results and change them.
        VIEWER, // Can only view studies and their results but cannot change them
        ADMIN, // Allows creating/changing/deleting other users (don't confuse role ADMIN with user 'admin')
        SUPERUSER, // Makes this user a member of ALL studies with all rights of a normal member
    }

    /**
     * Possible authentication methods
     */
    public enum AuthMethod {
        DB, LDAP, OAUTH_GOOGLE, OIDC, ORCID, SRAM, CONEXT
    }

    /**
     * Username is used as ID and is the login identifier (unique).
     */
    @Id
    @JsonView({JsonForApi.class})
    private String username;

    /**
     * Secondary ID, mostly used as ID in the API to not reveal the username in the URL
     */
    @Column(insertable = false, updatable = false)
    @JsonView({JsonForApi.class})
    private Long id;

    /**
     * User's name
     */
    @JsonView({JsonForApi.class})
    private String name;

    /**
     * User's email address
     */
    @JsonView({JsonForApi.class})
    private String email;

    /**
     * A list of Roles used for authorization. It has to be fetched eagerly
     * otherwise Hibernate has problems with the Worker's inheritance.
     */
    @JsonView({JsonForApi.class})
    @ElementCollection(targetClass = Role.class, fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    private Set<Role> roleList = EnumSet.of(Role.NONE);

    /**
     * Corresponding JatosWorker. This relationship is bidirectional.
     */
    @JsonIgnore
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "worker_id")
    private JatosWorker worker;

    /**
     * Hash of the user's password
     */
    @JsonIgnore
    private String passwordHash;

    /**
     * Which method to use for authentication
     */
    @JsonView({JsonForApi.class})
    @Enumerated(EnumType.STRING)
    private AuthMethod authMethod;

    /**
     * List of studies this user has access rights to. This relationship is
     * bidirectional.
     */
    @JsonIgnore
    @ManyToMany(mappedBy = "userList", fetch = FetchType.LAZY)
    private Set<Study> studyList = new HashSet<>();

    /**
     * Time of last successful sign-in
     */
    @JsonView({JsonForApi.class})
    @JsonProperty("lastSignin")
    private Timestamp lastLogin;

    /**
     * Time of last action (usually a request)
     */
    @JsonView({JsonForApi.class})
    private Timestamp lastSeen;

    /**
     * A user can be deactivated (by default they are active). If deactivated a user cannot sign in, but their studies
     * can be still run by workers.
     */
    @JsonView({JsonForApi.class})
    private boolean active = true;

    /**
     * URL of the last visited page in JATOS' UI.
     */
    @JsonIgnore
    private String lastVisitedPageUrl;

    public User(String username, String name, String email) {
        this(username, name, email, Role.USER);
    }

    public User(String username, String name, String email, Role role) {
        setUsername(username);
        this.name = name;
        this.email = email;
        updateRoles(role);
    }

    public User() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Normalise username:
     * 1) remove accents
     * 2) turn to lower case
     * 3) trim
     * 4) return the composed form NFKC (combining character sequences are mapped to composites)
     * (see https://stackoverflow.com/a/1598365/1278769)
     */
    public static String normalizeUsername(String username) {
        if (username == null) return null;
        String usernameWithoutAccents = Normalizer.normalize(username, Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        return Normalizer.normalize(usernameWithoutAccents, Normalizer.Form.NFKC).toLowerCase().trim();
    }

    public void setUsername(String username) {
        this.username = normalizeUsername(username);
    }

    public String getUsername() {
        return normalizeUsername(this.username);
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEmail() {
        return this.email;
    }

    public Set<Role> getRoleList() {
        return roleList;
    }

    public void updateRoles(Role role) {
        if (role == null) return;
        switch (role) {
            case NONE:
                roleList.clear();
                roleList.add(Role.NONE);
                return;
            case VIEWER:
                roleList.remove(Role.NONE);
                roleList.remove(Role.USER);
                roleList.add(Role.VIEWER);
                return;
            case USER:
                roleList.remove(Role.NONE);
                roleList.remove(Role.VIEWER);
                roleList.add(Role.USER);
                return;
            case ADMIN:
            case SUPERUSER:
                roleList.remove(Role.NONE);
                roleList.add(role);
                return;
            default:
                throw new IllegalArgumentException("Unknown role: " + role);
        }
    }

    public void removeRole(Role role) {
        if (role == null) return;
        roleList.remove(role);
        if (roleList.isEmpty()) {
            roleList.add(Role.NONE);
        }
    }

    public boolean hasRole(Role role) {
        return roleList.contains(role);
    }

    public boolean hasRole(Set<Role> roles) {
        return roleList.stream().anyMatch(roles::contains);
    }

    public boolean isUser() {
        return hasRole(Role.USER);
    }

    public boolean isViewer() {
        return hasRole(Role.VIEWER);
    }

    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }

    public boolean isSuperuser() {
        return hasRole(Role.SUPERUSER);
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPasswordHash() {
        return this.passwordHash;
    }

    public AuthMethod getAuthMethod() {
        return authMethod;
    }

    public void setAuthMethod(AuthMethod authMethod) {
        this.authMethod = authMethod;
    }

    @JsonIgnore
    public boolean isDb() {
        return this.authMethod == AuthMethod.DB;
    }

    @JsonIgnore
    public boolean isLdap() {
        return this.authMethod == AuthMethod.LDAP;
    }

    @JsonIgnore
    public boolean isOauthGoogle() {
        return this.authMethod == AuthMethod.OAUTH_GOOGLE;
    }

    @JsonIgnore
    public boolean isOidc() {
        return this.authMethod == AuthMethod.OIDC;
    }

    @JsonIgnore
    public boolean isOrcid() {
        return this.authMethod == AuthMethod.ORCID;
    }

    @JsonIgnore
    public boolean isSram() {
        return this.authMethod == AuthMethod.SRAM;
    }

    @JsonIgnore
    public boolean isConext() {
        return this.authMethod == AuthMethod.CONEXT;
    }

    public void setWorker(JatosWorker worker) {
        this.worker = worker;
    }

    public JatosWorker getWorker() {
        return this.worker;
    }

    public Set<Study> getStudyList() {
        return this.studyList;
    }

    // Adding the study is already done in Study.addUser()
    public void addStudy(Study study) {
        if (!studyList.contains(study)) {
            this.studyList.add(study);
        }
    }

    // Removing the study is already done in Study.removeUser()
    public void removeStudy(Study study) {
        if (studyList.contains(study)) {
            this.studyList.remove(study);
        }
    }

    public boolean hasStudy(Study study) {
        return this.studyList.contains(study);
    }

    public Timestamp getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(Timestamp lastLogin) {
        this.lastLogin = lastLogin;
    }

    public Timestamp getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Timestamp lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setLastVisitedPageUrl(String lastVisitedPageUrl) {
        this.lastVisitedPageUrl = lastVisitedPageUrl;
    }

    public String getLastVisitedPageUrl() {
        return this.lastVisitedPageUrl;
    }

    @Override
    public String toString() {
        if (this.getName() != null && !this.getName().trim().isEmpty()) {
            return this.getName() + " (" + this.getUsername() + ")";
        } else {
            return this.getUsername();
        }
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(getUsername());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof User)) return false;
        User other = (User) obj;
        return Objects.equals(getUsername(), other.getUsername());
    }

}
