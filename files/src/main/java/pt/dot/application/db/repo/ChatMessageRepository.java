package pt.dot.application.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.dot.application.api.dto.chat.ChatMessageResponseDto;
import pt.dot.application.db.entity.ChatMessage;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("""
        SELECT new pt.dot.application.api.dto.chat.ChatMessageResponseDto(
            m.id,
            sender.id,
            sender.displayName,
            m.type,
            m.body,
            m.poiId,
            m.poiName,
            m.poiImage,
            m.createdAt
        )
        FROM ChatMessage m
        JOIN m.sender sender
        WHERE m.conversation.id = :conversationId
        ORDER BY m.createdAt ASC
    """)
    List<ChatMessageResponseDto> findDtosByConversationId(UUID conversationId);
}
