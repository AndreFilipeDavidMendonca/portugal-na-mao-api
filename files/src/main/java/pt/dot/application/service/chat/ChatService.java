package pt.dot.application.service.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.dot.application.api.dto.chat.ChatMessageResponseDto;
import pt.dot.application.api.dto.chat.SendChatMessageDto;
import pt.dot.application.db.entity.AppUser;
import pt.dot.application.db.entity.ChatConversation;
import pt.dot.application.db.entity.ChatMessage;
import pt.dot.application.db.entity.Friendship;
import pt.dot.application.db.enums.ChatMessageType;
import pt.dot.application.db.enums.FriendshipStatus;
import pt.dot.application.db.repo.AppUserRepository;
import pt.dot.application.db.repo.ChatConversationRepository;
import pt.dot.application.db.repo.ChatMessageRepository;
import pt.dot.application.db.repo.FriendshipRepository;
import pt.dot.application.db.repo.PoiRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatService {

    private final ChatConversationRepository chatConversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final FriendshipRepository friendshipRepository;
    private final AppUserRepository appUserRepository;
    private final PoiRepository poiRepository;

    public UUID getOrCreateConversation(UUID currentUserId, UUID friendUserId) {
        if (currentUserId.equals(friendUserId)) {
            throw new IllegalArgumentException("Não podes iniciar chat contigo próprio.");
        }

        Friendship friendship = friendshipRepository.findBetweenUsers(currentUserId, friendUserId)
                .orElseThrow(() -> new IllegalStateException("Só podes iniciar chat com amigos."));

        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new IllegalStateException("Só podes iniciar chat com amigos.");
        }

        UUID userAId = orderA(currentUserId, friendUserId);
        UUID userBId = orderB(currentUserId, friendUserId);

        return chatConversationRepository.findByOrderedUsers(userAId, userBId)
                .map(ChatConversation::getId)
                .orElseGet(() -> {
                    AppUser userA = appUserRepository.findById(userAId)
                            .orElseThrow(() -> new IllegalArgumentException("Utilizador não encontrado."));
                    AppUser userB = appUserRepository.findById(userBId)
                            .orElseThrow(() -> new IllegalArgumentException("Utilizador não encontrado."));

                    ChatConversation conversation = new ChatConversation();
                    conversation.setUserA(userA);
                    conversation.setUserB(userB);

                    return chatConversationRepository.save(conversation).getId();
                });
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponseDto> getMessages(UUID currentUserId, UUID conversationId) {
        ChatConversation conversation = chatConversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada."));

        validateMembership(currentUserId, conversation);
        return chatMessageRepository.findDtosByConversationId(conversationId);
    }

    public void sendMessage(UUID currentUserId, UUID conversationId, SendChatMessageDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Payload da mensagem em falta.");
        }

        ChatConversation conversation = chatConversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada."));

        validateMembership(currentUserId, conversation);

        AppUser sender = appUserRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("Utilizador não encontrado."));

        AppUser receiver = resolveOtherParticipant(conversation, currentUserId);

        ChatMessageType type = resolveType(dto);
        Instant now = Instant.now();

        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setType(type);
        message.setCreatedAt(now);

        if (type == ChatMessageType.TEXT) {
            String normalizedBody = normalizeTextBody(dto.getBody());
            message.setBody(normalizedBody);
            message.setPoiId(null);
            message.setPoiName(null);
            message.setPoiImage(null);

            conversation.setLastMessageType(ChatMessageType.TEXT);
            conversation.setLastMessageBody(normalizedBody);
            conversation.setLastPoiId(null);
            conversation.setLastPoiName(null);
            conversation.setLastPoiImage(null);
        } else {
            Long poiId = dto.getPoiId();
            String poiName = normalizePoiName(dto.getPoiName());
            String poiImage = normalizeOptional(dto.getPoiImage());

            poiRepository.findById(poiId)
                    .orElseThrow(() -> new IllegalArgumentException("POI não encontrado."));

            message.setBody(null);
            message.setPoiId(poiId);
            message.setPoiName(poiName);
            message.setPoiImage(poiImage);

            conversation.setLastMessageType(ChatMessageType.POI_SHARE);
            conversation.setLastMessageBody(null);
            conversation.setLastPoiId(poiId);
            conversation.setLastPoiName(poiName);
            conversation.setLastPoiImage(poiImage);
        }

        conversation.setLastMessageAt(now);
        conversation.setUpdatedAt(now);

        chatMessageRepository.save(message);
        chatConversationRepository.save(conversation);
    }

    private ChatMessageType resolveType(SendChatMessageDto dto) {
        ChatMessageType requestedType = dto.getType();

        boolean hasPoiPayload = dto.getPoiId() != null || hasText(dto.getPoiName()) || hasText(dto.getPoiImage());
        boolean hasTextBody = hasText(dto.getBody());

        ChatMessageType inferredType = hasPoiPayload ? ChatMessageType.POI_SHARE : ChatMessageType.TEXT;
        ChatMessageType finalType = requestedType != null ? requestedType : inferredType;

        if (finalType == ChatMessageType.TEXT) {
            if (!hasTextBody) {
                throw new IllegalArgumentException("A mensagem de texto não pode estar vazia.");
            }
            if (hasPoiPayload) {
                throw new IllegalArgumentException("Uma mensagem TEXT não pode conter dados de POI.");
            }
            return ChatMessageType.TEXT;
        }

        if (dto.getPoiId() == null) {
            throw new IllegalArgumentException("Uma mensagem POI precisa de poiId.");
        }
        if (!hasText(dto.getPoiName())) {
            throw new IllegalArgumentException("Uma mensagem POI precisa de poiName.");
        }
        if (hasTextBody) {
            throw new IllegalArgumentException("Uma mensagem POI não deve conter body.");
        }

        return ChatMessageType.POI_SHARE;
    }

    private String normalizeTextBody(String body) {
        String normalized = normalizeOptional(body);
        if (normalized == null) {
            throw new IllegalArgumentException("A mensagem de texto não pode estar vazia.");
        }
        return normalized;
    }

    private String normalizePoiName(String poiName) {
        String normalized = normalizeOptional(poiName);
        if (normalized == null) {
            throw new IllegalArgumentException("poiName é obrigatório.");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return normalizeOptional(value) != null;
    }

    private AppUser resolveOtherParticipant(ChatConversation conversation, UUID currentUserId) {
        if (conversation.getUserA().getId().equals(currentUserId)) {
            return conversation.getUserB();
        }
        if (conversation.getUserB().getId().equals(currentUserId)) {
            return conversation.getUserA();
        }
        throw new IllegalStateException("Sem permissões para aceder a esta conversa.");
    }

    private void validateMembership(UUID currentUserId, ChatConversation conversation) {
        boolean belongs =
                conversation.getUserA().getId().equals(currentUserId) ||
                        conversation.getUserB().getId().equals(currentUserId);

        if (!belongs) {
            throw new IllegalStateException("Sem permissões para aceder a esta conversa.");
        }
    }

    private UUID orderA(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) <= 0 ? a : b;
    }

    private UUID orderB(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) <= 0 ? b : a;
    }
}
