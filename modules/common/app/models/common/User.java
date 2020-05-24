package models.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import models.common.workers.JatosWorker;

import javax.persistence.*;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;

/**
 * Domain entity of a user. Used for JSON marshalling and JPA persistance.
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
        USER, // Normal JATOS user
        ADMIN // Allows to create/change/delete other users (don't confuse role ADMIN with user 'admin')
    }

    /**
     * Possible authentication methods
     */
    public enum AuthMethod {
        DB, LDAP, OAUTH_GOOGLE
    }

    /**
     * username is used as ID
     */
    @Id
    private String username;

    /**
     * User's name
     */
    private String name;

    /**
     * A list of Roles used for authorization. It has to be fetched eagerly
     * otherwise Hibernate has problems with the Worker's inheritance.
     */
    @ElementCollection(targetClass = Role.class, fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    private Set<Role> roleList = new HashSet<>();

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
    @JsonIgnore
    @Enumerated(EnumType.STRING)
    private AuthMethod authMethod;

    /**
     * List of studies this user has access rights to. This relationship is
     * bidirectional.
     */
    @ManyToMany(mappedBy = "userList", fetch = FetchType.LAZY)
    private Set<Study> studyList = new HashSet<>();

    public User(String username, String name) {
        setUsername(username);
        this.name = name;
    }

    public User() {
    }

    /**
     * Normalise username:
     * 1) remove accents
     * 2) turn to lower case
     * 3) trim
     * 4) return the the composed form NFKC (combining character sequences are mapped to composites
     *    (see https://stackoverflow.com/a/1598365/1278769)
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

    public Set<Role> getRoleList() {
        return roleList;
    }

    public void setRoleList(Set<Role> roleList) {
        this.roleList = roleList;
    }

    public void addRole(Role role) {
        this.roleList.add(role);
    }

    public void removeRole(Role role) {
        this.roleList.remove(role);
    }

    public boolean hasRole(Role role) {
        return roleList.contains(role);
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

    public void setWorker(JatosWorker worker) {
        this.worker = worker;
    }

    public JatosWorker getWorker() {
        return this.worker;
    }

    public void setStudyList(Set<Study> studyList) {
        this.studyList = studyList;
    }

    public Set<Study> getStudyList() {
        return this.studyList;
    }

    public void addStudy(Study study) {
        this.studyList.add(study);
    }

    public void removeStudy(Study study) {
        this.studyList.remove(study);
    }

    public boolean hasStudy(Study study) {
        return this.studyList.contains(study);
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
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.getUsername() == null) ? 0 : this.getUsername().hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;

        if (obj == null) return false;

        if (!(obj instanceof User)) return false;

        User other = (User) obj;
        if (getUsername() == null) return other.getUsername() == null;
        return getUsername().equals(other.getUsername());
    }

}
