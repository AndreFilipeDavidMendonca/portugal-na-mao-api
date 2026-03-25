package pt.dot.application.service.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.dot.application.api.dto.chat.ChatMessageResponseDto;
import pt.dot.application.db.entity.AppUser;
import pt.dot.application.db.entity.ChatConversation;
import pt.dot.application.db.entity.ChatMessage;
import pt.dot.application.db.entity.Friendship;
import pt.dot.application.db.enums.FriendshipStatus;
import pt.dot.application.db.repo.AppUserRepository;
import pt.dot.application.db.repo.ChatConversationRepository;
import pt.dot.application.db.repo.ChatMessageRepository;
import pt.dot.application.db.repo.FriendshipRepository;

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

    public void sendMessage(UUID currentUserId, UUID conversationId, String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("A mensagem não pode estar vazia.");
        }

        ChatConversation conversation = chatConversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada."));

        validateMembership(currentUserId, conversation);

        AppUser sender = appUserRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("Utilizador não encontrado."));

        AppUser receiver = resolveOtherParticipant(conversation, currentUserId);

        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setBody(body.trim());

        chatMessageRepository.save(message);
        conversation.touchUpdatedAt();
        chatConversationRepository.save(conversation);
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