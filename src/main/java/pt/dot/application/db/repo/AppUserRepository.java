package pt.dot.application.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.dot.application.db.entity.AppUser;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}