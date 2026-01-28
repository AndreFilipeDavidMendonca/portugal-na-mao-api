package pt.dot.application.db.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "app_user")
public class AppUser {

    @Setter
    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Setter
    @Column(nullable = false, unique = true)
    private String email;

    @Setter
    @Column(name = "first_name", length = 120)
    private String firstName;

    @Setter
    @Column(name = "last_name", length = 120)
    private String lastName;

    @Setter
    @Column(name = "age")
    private Integer age;

    @Setter
    @Column(name = "nationality", length = 120)
    private String nationality;

    @Setter
    @Column(name = "phone", length = 50)
    private String phone;

    @Setter
    @Column(name = "display_name")
    private String displayName;

    @Setter
    @Column(name = "avatar_url", columnDefinition = "text")
    private String avatarUrl;

    @Setter
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private UserRole role = UserRole.USER;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public AppUser() {}

    @PreUpdate
    public void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppUser appUser)) return false;
        return Objects.equals(id, appUser.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}