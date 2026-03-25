package pt.dot.application.api.friendship;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.dot.application.api.dto.friendship.FriendDto;
import pt.dot.application.api.dto.friendship.FriendRequestDto;
import pt.dot.application.api.dto.friendship.FriendRequestResponseDto;
import pt.dot.application.security.SecurityUtil;
import pt.dot.application.service.friendship.FriendshipService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/friendships")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;

    @PostMapping("/request")
    public ResponseEntity<Void> sendRequest(@RequestBody FriendRequestDto dto) {
        UUID currentUserId = SecurityUtil.getUserIdOrNull();
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        friendshipService.sendRequest(currentUserId, dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{friendshipId}/accept")
    public ResponseEntity<Void> accept(@PathVariable UUID friendshipId) {
        UUID currentUserId = SecurityUtil.getUserIdOrNull();
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        friendshipService.accept(currentUserId, friendshipId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{friendshipId}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID friendshipId) {
        UUID currentUserId = SecurityUtil.getUserIdOrNull();
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        friendshipService.reject(currentUserId, friendshipId);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<FriendDto>> getFriends() {
        UUID currentUserId = SecurityUtil.getUserIdOrNull();
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(friendshipService.getFriends(currentUserId));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<FriendRequestResponseDto>> getPending() {
        UUID currentUserId = SecurityUtil.getUserIdOrNull();
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(friendshipService.getPending(currentUserId));
    }

    @DeleteMapping("/{friendshipId}")
    public ResponseEntity<Void> delete(@PathVariable UUID friendshipId) {
        UUID currentUserId = SecurityUtil.getUserIdOrNull();
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        friendshipService.deleteFriendship(currentUserId, friendshipId);
        return ResponseEntity.noContent().build();
    }
}