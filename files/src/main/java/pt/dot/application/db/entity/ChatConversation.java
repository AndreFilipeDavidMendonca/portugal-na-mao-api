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
@Table(
        name = "chat_conversation",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_conversation_user_a_user_b",
                        columnNames = {"user_a_id", "user_b_id"}
                )
        }
)
public class ChatConversation {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_a_id", nullable = false)
    private AppUser userA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_b_id", nullable = false)
    private AppUser userB;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_message_type", length = 20)
    private ChatMessageType lastMessageType;

    @Column(name = "last_message_body", length = 4000)
    private String lastMessageBody;

    @Column(name = "last_poi_id")
    private Long lastPoiId;

    @Column(name = "last_poi_name", length = 300)
    private String lastPoiName;

    @Column(name = "last_poi_image", length = 2000)
    private String lastPoiImage;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }
}
