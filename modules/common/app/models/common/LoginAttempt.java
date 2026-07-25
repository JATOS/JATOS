package models.common;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Objects;

import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * DB entity of a failed login attempt
 */
@Entity
@Table(name = "LoginAttempt")
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    /**
     * username as entered in the login form; might be not corresponded to an actual username
     */
    private String username;

    /**
     * Remote address - origin of the failed login attempt
     */
    private String remoteAddress;

    /**
     * Timestamp of the failed login attempt
     */
    private Timestamp date;

    public LoginAttempt(String username, String remoteAddress) {
        this.username = username;
        this.remoteAddress = remoteAddress;
        this.date = new Timestamp(new Date().getTime());
    }

    @SuppressWarnings("unused")
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

    public String getRemoteAddress() {
        return remoteAddress;
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
        return Objects.hash(getUsername(), getRemoteAddress(), getDate());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoginAttempt that)) return false;
        return Objects.equals(getUsername(), that.getUsername())
                && Objects.equals(getRemoteAddress(), that.getRemoteAddress())
                && Objects.equals(getDate(), that.getDate());
    }
}
