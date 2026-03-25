package pt.dot.application.service.friendship;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.dot.application.api.dto.friendship.FriendDto;
import pt.dot.application.api.dto.friendship.FriendRequestDto;
import pt.dot.application.api.dto.friendship.FriendRequestResponseDto;
import pt.dot.application.db.entity.AppUser;
import pt.dot.application.db.entity.Friendship;
import pt.dot.application.db.enums.FriendshipStatus;
import pt.dot.application.db.repo.AppUserRepository;
import pt.dot.application.db.repo.FriendshipRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final AppUserRepository appUserRepository;

    public void sendRequest(UUID currentUserId, FriendRequestDto dto) {
        if (dto == null || dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email inválido.");
        }

        AppUser requester = appUserRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("Utilizador autenticado não encontrado."));

        String targetEmail = dto.getEmail().trim().toLowerCase();

        AppUser receiver = appUserRepository.findByEmailIgnoreCase(targetEmail)
                .orElseThrow(() -> new IllegalArgumentException("Não foi encontrado nenhum utilizador com esse email."));

        if (requester.getId().equals(receiver.getId())) {
            throw new IllegalArgumentException("Não podes enviar convite a ti próprio.");
        }

        var existing = friendshipRepository.findBetweenUsers(requester.getId(), receiver.getId());

        if (existing.isPresent()) {
            Friendship friendship = existing.get();

            if (friendship.getStatus() == FriendshipStatus.PENDING) {
                throw new IllegalStateException("Já existe um convite pendente entre estes utilizadores.");
            }

            if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
                throw new IllegalStateException("Estes utilizadores já são amigos.");
            }

            if (friendship.getStatus() == FriendshipStatus.REJECTED) {
                friendship.setRequester(requester);
                friendship.setReceiver(receiver);
                friendship.setStatus(FriendshipStatus.PENDING);
                friendshipRepository.save(friendship);
                return;
            }
        }

        Friendship friendship = new Friendship();
        friendship.setRequester(requester);
        friendship.setReceiver(receiver);
        friendship.setStatus(FriendshipStatus.PENDING);

        friendshipRepository.save(friendship);
    }

    public void accept(UUID currentUserId, UUID friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("Convite não encontrado."));

        if (!friendship.getReceiver().getId().equals(currentUserId)) {
            throw new IllegalStateException("Sem permissões para aceitar este convite.");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new IllegalStateException("Este convite já não está pendente.");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);
    }

    public void reject(UUID currentUserId, UUID friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("Convite não encontrado."));

        if (!friendship.getReceiver().getId().equals(currentUserId)) {
            throw new IllegalStateException("Sem permissões para rejeitar este convite.");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new IllegalStateException("Este convite já não está pendente.");
        }

        friendship.setStatus(FriendshipStatus.REJECTED);
        friendshipRepository.save(friendship);
    }

    @Transactional(readOnly = true)
    public List<FriendDto> getFriends(UUID currentUserId) {
        return friendshipRepository
                .findForUserByStatusWithUsers(currentUserId, FriendshipStatus.ACCEPTED)
                .stream()
                .map(friendship -> {
                    AppUser friend = friendship.getRequester().getId().equals(currentUserId)
                            ? friendship.getReceiver()
                            : friendship.getRequester();

                    String displayName = resolveDisplayName(friend);

                    return new FriendDto(
                            friendship.getId(),      // friendshipId
                            friend.getId(),          // id do amigo
                            displayName,
                            friend.getEmail(),
                            friend.getAvatarUrl(),
                            false,                   // hasUnreadMessages por agora
                            0L                       // unreadCount por agora
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendRequestResponseDto> getPending(UUID currentUserId) {
        return friendshipRepository.findPendingDtosByReceiverIdAndStatus(
                currentUserId,
                FriendshipStatus.PENDING
        );
    }

    private String resolveDisplayName(AppUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName().trim();
        }

        String firstName = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String lastName = user.getLastName() != null ? user.getLastName().trim() : "";
        String fullName = (firstName + " " + lastName).trim();

        if (!fullName.isBlank()) {
            return fullName;
        }

        return user.getEmail();
    }

    public void deleteFriendship(UUID currentUserId, UUID friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("Amizade não encontrada."));

        boolean belongsToUser =
                friendship.getRequester().getId().equals(currentUserId) ||
                        friendship.getReceiver().getId().equals(currentUserId);

        if (!belongsToUser) {
            throw new IllegalStateException("Sem permissões para eliminar esta amizade.");
        }

        friendshipRepository.delete(friendship);
    }
}