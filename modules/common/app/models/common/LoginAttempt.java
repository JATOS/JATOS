package models.common;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Objects;

/**
 * DB entity of a failed login attempt
 *
 * @author Kristian Lange
 */
@Entity
@Table(name = "LoginAttempt")
public class LoginAttempt {

    @Id
    @GeneratedValue
    private Long id;

    /**
     * username as entered in the login form
     */
    private String username;

    /**
     * Timestamp of the failed login attempt
     */
    private Timestamp date;

    public LoginAttempt(String username) {
        this.username = username;
        this.date = new Timestamp(new Date().getTime());
    }

    public LoginAttempt() {
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return this.id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Timestamp getDate() {
        return date;
    }

    public void setDate(Timestamp date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return this.getUsername() + " (" + this.getDate().toString() + ")";
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, date);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LoginAttempt that = (LoginAttempt) o;
        return getUsername().equals(that.getUsername()) && getDate().equals(that.getDate());
    }
}
