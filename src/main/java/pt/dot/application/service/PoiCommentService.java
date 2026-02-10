package pt.dot.application.service;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pt.dot.application.api.dto.CreatePoiCommentRequest;
import pt.dot.application.api.dto.PoiCommentDto;
import pt.dot.application.db.entity.AppUser;
import pt.dot.application.db.entity.PoiComment;
import pt.dot.application.db.repo.AppUserRepository;
import pt.dot.application.db.repo.PoiCommentRepository;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@Service
public class PoiCommentService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final PoiCommentRepository commentRepo;
    private final AppUserRepository userRepo;

    public PoiCommentService(PoiCommentRepository commentRepo, AppUserRepository userRepo) {
        this.commentRepo = commentRepo;
        this.userRepo = userRepo;
    }

    private UUID requireUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
        return (UUID) auth.getPrincipal();
    }

    private AppUser requireMe() {
        UUID userId = requireUserId();
        return userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED));
    }

    private AppUser tryGetMeOrNull() {
        try {
            UUID userId = requireUserId();
            return userRepo.findById(userId).orElse(null);
        } catch (Exception ignore) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<PoiCommentDto> listByPoi(long poiId) {
        AppUser me = tryGetMeOrNull();

        return commentRepo.findByPoiIdOrderByCreatedAtDesc(poiId)
                .stream()
                .map(c -> toDto(c, me))
                .toList();
    }

    @Transactional
    public PoiCommentDto add(long poiId, CreatePoiCommentRequest req) {
        AppUser me = requireMe();

        String body = (req == null || req.body() == null) ? "" : req.body().trim();
        if (body.isBlank()) throw new ResponseStatusException(BAD_REQUEST, "Comentário vazio");

        PoiComment c = new PoiComment();
        c.setPoiId(poiId);
        c.setUserId(me.getId());
        c.setAuthorName(safeAuthorName(me));
        c.setBody(body);

        PoiComment saved = commentRepo.save(c);
        return toDto(saved, me);
    }

    @Transactional
    public void delete(long commentId) {
        AppUser me = requireMe();

        PoiComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Comentário não encontrado"));

        boolean isOwner = c.getUserId() != null && c.getUserId().equals(me.getId());
        boolean isAdmin = me.getRole() != null && "ADMIN".equalsIgnoreCase(me.getRole().name());

        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(FORBIDDEN, "Sem permissão para remover este comentário");
        }

        commentRepo.delete(c);
    }

    private PoiCommentDto toDto(PoiComment c, AppUser meOrNull) {
        boolean canDelete = false;
        if (meOrNull != null && c.getUserId() != null) {
            boolean isOwner = c.getUserId().equals(meOrNull.getId());
            boolean isAdmin = meOrNull.getRole() != null && "ADMIN".equalsIgnoreCase(meOrNull.getRole().name());
            canDelete = isOwner || isAdmin;
        }

        return new PoiCommentDto(
                c.getId() == null ? 0 : c.getId(),
                c.getPoiId() == null ? 0 : c.getPoiId(),
                c.getAuthorName(),
                c.getBody(),
                c.getCreatedAt() == null ? null : FMT.format(c.getCreatedAt()),
                c.getUpdatedAt() == null ? null : FMT.format(c.getUpdatedAt()),
                canDelete
        );
    }

    private String safeAuthorName(AppUser u) {
        String dn = u.getDisplayName() == null ? "" : u.getDisplayName().trim();
        if (!dn.isEmpty()) return dn;

        String fn = u.getFirstName() == null ? "" : u.getFirstName().trim();
        String ln = u.getLastName() == null ? "" : u.getLastName().trim();
        String full = (fn + " " + ln).trim();
        if (!full.isEmpty()) return full;

        String email = u.getEmail() == null ? "" : u.getEmail().trim();
        if (!email.isEmpty()) {
            int at = email.indexOf('@');
            return at > 0 ? email.substring(0, at) : email;
        }

        return "Utilizador";
    }
}