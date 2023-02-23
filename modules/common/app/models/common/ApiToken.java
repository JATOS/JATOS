package models.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;

/**
 * DB entity of an API token (Personal Access Token, PAT). Used to authenticate/authorize JATOS API.
 *
 * @author Kristian Lange
 */
@Entity
@Table(name = "ApiToken", indexes = {@Index(columnList = "tokenHash")})
public class ApiToken {

    @Id
    @GeneratedValue
    private Long id;

    /**
     * Hash of the token - the actual token is never saved
     */
    @JsonIgnore
    private String tokenHash;

    /**
     * A name that is given by the user to this token to distinguish it from others.
     */
    private String name;

    /**
     * Owning User.
     */
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_username")
    @JsonIgnore
    private User user;

    /**
     * Timestamp of the creation date
     */
    private Timestamp creationDate;

    /**
     * Time in seconds that this token will expire after creation date. Null means no expiration.
     */
    private Integer expires;

    @JsonProperty("expirationDate")
    public Timestamp getExpirationDate() {
        if (expires == null || expires <= 0) return Timestamp.from(Instant.EPOCH);
        return Timestamp.from(creationDate.toInstant().plusSeconds(expires));
    }

    @JsonProperty("isExpired")
    public boolean isExpired() {
        if (expires == null || expires <= 0) return false;
        return Instant.now().isAfter(creationDate.toInstant().plusSeconds(expires));
    }

    @JsonProperty("isAdminToken")
    public boolean isAdminToken() {
        return user.isAdmin();
    }

    /**
     * Is this token active (true) or revoked (false)
     */
    private boolean active = true;

    public ApiToken(String tokenHash, String name, Integer expires, User user) {
        this.tokenHash = tokenHash;
        this.name = name;
        this.expires = expires;
        this.user = user;
        this.creationDate = new Timestamp(new Date().getTime());
    }

    public ApiToken() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Timestamp getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Timestamp creationDate) {
        this.creationDate = creationDate;
    }

    public Integer getExpires() {
        return expires;
    }

    public void setExpires(Integer expires) {
        this.expires = expires;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return this.getTokenHash() + " (" + this.getUser().getUsername() + ")";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getTokenHash() == null) ? 0 : getTokenHash().hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;

        if (obj == null) return false;

        if (!(obj instanceof ApiToken)) return false;

        ApiToken other = (ApiToken) obj;
        if (getTokenHash() == null) return other.getTokenHash() == null;
        return getTokenHash().equals(other.getTokenHash());
    }

}
