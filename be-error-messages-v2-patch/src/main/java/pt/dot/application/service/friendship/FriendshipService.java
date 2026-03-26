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
import pt.dot.application.exception.Errors;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final AppUserRepository appUserRepository;

    public void sendRequest(UUID currentUserId, FriendRequestDto dto) {
        String targetEmail = normalizeAndValidateEmail(dto);
        AppUser requester = getUserOrThrow(currentUserId, "AUTH_USER_NOT_FOUND", "Não foi possível validar a tua conta. Inicia sessão novamente e tenta de novo.");
        AppUser receiver = getUserByEmailOrThrow(targetEmail);

        validateNotSelfRequest(requester, receiver);

        friendshipRepository.findBetweenUsers(requester.getId(), receiver.getId())
                .ifPresentOrElse(
                        friendship -> handleExistingFriendship(friendship, requester, receiver),
                        () -> createPendingFriendship(requester, receiver)
                );
    }

    public void accept(UUID currentUserId, UUID friendshipId) {
        Friendship friendship = getFriendshipOrThrow(friendshipId, "FRIEND_REQUEST_NOT_FOUND", "Não foi possível encontrar este pedido de amizade.");

        validateReceiverOwnership(friendship, currentUserId, "FRIEND_REQUEST_FORBIDDEN", "Não tens permissão para aceitar este pedido de amizade.");
        validatePendingStatus(friendship, "FRIEND_REQUEST_NOT_PENDING", "Este pedido já não está pendente.");

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);
    }

    public void reject(UUID currentUserId, UUID friendshipId) {
        Friendship friendship = getFriendshipOrThrow(friendshipId, "FRIEND_REQUEST_NOT_FOUND", "Não foi possível encontrar este pedido de amizade.");

        validateReceiverOwnership(friendship, currentUserId, "FRIEND_REQUEST_FORBIDDEN", "Não tens permissão para rejeitar este pedido de amizade.");
        validatePendingStatus(friendship, "FRIEND_REQUEST_NOT_PENDING", "Este pedido já não está pendente.");

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

                    return new FriendDto(
                            friendship.getId(),
                            friend.getId(),
                            resolveDisplayName(friend),
                            friend.getEmail(),
                            friend.getAvatarUrl(),
                            false,
                            0L
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

    public void deleteFriendship(UUID currentUserId, UUID friendshipId) {
        Friendship friendship = getFriendshipOrThrow(friendshipId, "FRIENDSHIP_NOT_FOUND", "Não foi possível encontrar esta amizade.");

        boolean belongsToUser =
                friendship.getRequester().getId().equals(currentUserId) ||
                        friendship.getReceiver().getId().equals(currentUserId);

        if (!belongsToUser) {
            throw Errors.forbidden("FRIENDSHIP_DELETE_FORBIDDEN", "Não tens permissão para remover esta amizade.");
        }

        friendshipRepository.delete(friendship);
    }

    private String normalizeAndValidateEmail(FriendRequestDto dto) {
        if (dto == null || dto.getEmail() == null) {
            throw Errors.badRequest("INVALID_FRIEND_EMAIL", "Indica um email válido para enviar o pedido de amizade.");
        }

        String email = dto.getEmail().trim().toLowerCase();

        if (email.isBlank()) {
            throw Errors.badRequest("INVALID_FRIEND_EMAIL", "Indica um email válido para enviar o pedido de amizade.");
        }

        return email;
    }

    private AppUser getUserOrThrow(UUID userId, String code, String message) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> Errors.unauthorized(code, message));
    }

    private AppUser getUserByEmailOrThrow(String email) {
        return appUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> Errors.notFound(
                        "FRIEND_TARGET_NOT_FOUND",
                        "Não foi possível encontrar nenhum utilizador com este email. Confirma o endereço e tenta novamente."
                ));
    }

    private Friendship getFriendshipOrThrow(UUID friendshipId, String code, String message) {
        return friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> Errors.notFound(code, message));
    }

    private void validateNotSelfRequest(AppUser requester, AppUser receiver) {
        if (requester.getId().equals(receiver.getId())) {
            throw Errors.badRequest("SELF_FRIEND_REQUEST", "Não podes enviar um pedido de amizade a ti próprio.");
        }
    }

    private void validateReceiverOwnership(Friendship friendship, UUID currentUserId, String code, String message) {
        if (!friendship.getReceiver().getId().equals(currentUserId)) {
            throw Errors.forbidden(code, message);
        }
    }

    private void validatePendingStatus(Friendship friendship, String code, String message) {
        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw Errors.conflict(code, message);
        }
    }

    private void handleExistingFriendship(Friendship friendship, AppUser requester, AppUser receiver) {
        if (friendship.getStatus() == FriendshipStatus.PENDING) {
            boolean currentUserAlreadyRequested = friendship.getRequester().getId().equals(requester.getId());

            if (currentUserAlreadyRequested) {
                throw Errors.conflict(
                        "FRIEND_REQUEST_ALREADY_SENT",
                        "Já enviaste um pedido a este utilizador. Aguarda pela resposta."
                );
            }

            throw Errors.conflict(
                    "FRIEND_REQUEST_ALREADY_RECEIVED",
                    "Este utilizador já te enviou um pedido de amizade. Verifica os teus pedidos pendentes."
            );
        }

        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            throw Errors.conflict(
                    "FRIEND_ALREADY_EXISTS",
                    "Este utilizador já faz parte da tua lista de amigos."
            );
        }

        if (friendship.getStatus() == FriendshipStatus.REJECTED) {
            friendship.setRequester(requester);
            friendship.setReceiver(receiver);
            friendship.setStatus(FriendshipStatus.PENDING);
            friendshipRepository.save(friendship);
            return;
        }

        throw Errors.conflict(
                "INVALID_FRIENDSHIP_STATE",
                "Não foi possível concluir a ação porque o estado atual deste pedido já não o permite."
        );
    }

    private void createPendingFriendship(AppUser requester, AppUser receiver) {
        Friendship friendship = new Friendship();
        friendship.setRequester(requester);
        friendship.setReceiver(receiver);
        friendship.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(friendship);
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
}
