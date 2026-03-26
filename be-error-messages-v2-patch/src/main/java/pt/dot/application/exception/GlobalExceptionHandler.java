package pt.dot.application.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex, HttpServletRequest request) {
        String message = ex.getReason();
        if (message == null || message.isBlank()) {
            message = defaultMessageFor(ex.getStatusCode().value());
        }
        return build(HttpStatus.valueOf(ex.getStatusCode().value()), null, message, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = "Não foi possível concluir a ação. Verifica os dados enviados e tenta novamente.";
        }
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = "Não foi possível concluir a ação. Tenta novamente dentro de alguns segundos.";
        }
        return build(HttpStatus.CONFLICT, "CONFLICT", message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "UNEXPECTED_ERROR",
                "Ocorreu um erro inesperado. Tenta novamente dentro de alguns segundos.",
                request
        );
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity
                .status(status)
                .body(new ErrorResponse(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        code,
                        message,
                        request.getRequestURI()
                ));
    }

    private String defaultMessageFor(int statusCode) {
        HttpStatus status = HttpStatus.valueOf(statusCode);
        return switch (status) {
            case BAD_REQUEST -> "Não foi possível concluir a ação. Verifica os dados enviados e tenta novamente.";
            case UNAUTHORIZED -> "A tua sessão já não é válida. Inicia sessão novamente para continuar.";
            case FORBIDDEN -> "Não tens permissão para executar esta ação.";
            case NOT_FOUND -> "Não foi possível encontrar o recurso pedido.";
            case CONFLICT -> "Não foi possível concluir a ação porque o estado atual já não o permite.";
            default -> "Ocorreu um erro inesperado. Tenta novamente dentro de alguns segundos.";
        };
    }
}
