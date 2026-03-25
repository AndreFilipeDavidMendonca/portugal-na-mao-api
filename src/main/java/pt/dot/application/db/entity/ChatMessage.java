package pt.dot.application.db.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pt.dot.application.db.enums.ChatMessageType;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private AppUser sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private AppUser receiver;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ChatMessageType type = ChatMessageType.TEXT;

    @Column(name = "body", length = 4000)
    private String body;

    @Column(name = "poi_id")
    private Long poiId;

    @Column(name = "poi_name", length = 300)
    private String poiName;

    @Column(name = "poi_image", length = 2000)
    private String poiImage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;
}