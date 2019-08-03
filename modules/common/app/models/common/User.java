package models.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import models.common.workers.JatosWorker;

import javax.persistence.*;
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
     * Email address is used as ID. Emails are stored in lower case.
     */
    @Id
    private String email;

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
     * List of studies this user has access rights to. This relationship is
     * bidirectional.
     */
    @ManyToMany(mappedBy = "userList", fetch = FetchType.LAZY)
    private Set<Study> studyList = new HashSet<>();

    public User(String email, String name, String passwordHash) {
        this.email = email.toLowerCase();
        this.name = name;
        this.passwordHash = passwordHash;
    }

    public User(String email, String name) {
        this.email = email.toLowerCase();
        this.name = name;
    }

    public User() {
    }

    public void setEmail(String email) {
        this.email = email.toLowerCase();
    }

    public String getEmail() {
        return this.email.toLowerCase();
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
            return this.getName() + " (" + this.getEmail() + ")";
        } else {
            return this.getEmail();
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.getEmail() == null) ? 0 : this.getEmail().hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;

        if (obj == null) return false;

        if (!(obj instanceof User)) return false;

        User other = (User) obj;
        if (getEmail() == null) return other.getEmail() == null;
        return getEmail().equals(other.getEmail());
    }

}
