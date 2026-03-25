package pt.dot.application.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.dot.application.db.entity.ChatConversation;

import java.util.Optional;
import java.util.UUID;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {

    @Query("""
        SELECT c
        FROM ChatConversation c
        WHERE c.userA.id = :userAId
          AND c.userB.id = :userBId
    """)
    Optional<ChatConversation> findByOrderedUsers(UUID userAId, UUID userBId);
}