package pt.dot.application.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.dot.application.api.dto.friendship.FriendRequestResponseDto;
import pt.dot.application.db.entity.Friendship;
import pt.dot.application.db.enums.FriendshipStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    @Query("""
        SELECT f
        FROM Friendship f
        WHERE (f.requester.id = :a AND f.receiver.id = :b)
           OR (f.requester.id = :b AND f.receiver.id = :a)
    """)
    Optional<Friendship> findBetweenUsers(UUID a, UUID b);

    @Query("""
        SELECT f
        FROM Friendship f
        JOIN FETCH f.requester
        JOIN FETCH f.receiver
        WHERE f.status = :status
          AND (f.requester.id = :userId OR f.receiver.id = :userId)
        ORDER BY f.updatedAt DESC
    """)
    List<Friendship> findForUserByStatusWithUsers(UUID userId, FriendshipStatus status);

    @Query("""
        SELECT new pt.dot.application.api.dto.friendship.FriendRequestResponseDto(
            f.id,
            requester.email,
            requester.displayName,
            f.createdAt
        )
        FROM Friendship f
        JOIN f.requester requester
        WHERE f.receiver.id = :receiverId
          AND f.status = :status
        ORDER BY f.createdAt DESC
    """)
    List<FriendRequestResponseDto> findPendingDtosByReceiverIdAndStatus(UUID receiverId, FriendshipStatus status);
}