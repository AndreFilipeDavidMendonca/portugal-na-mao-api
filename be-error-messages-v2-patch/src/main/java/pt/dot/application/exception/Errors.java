package pt.dot.application.exception;

import org.springframework.http.HttpStatus;

public final class Errors {
    private Errors() {}

    public static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    public static ApiException badRequest(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }

    public static ApiException unauthorized(String message) { return new ApiException(HttpStatus.UNAUTHORIZED, message); }
    public static ApiException unauthorized(String code, String message) { return new ApiException(HttpStatus.UNAUTHORIZED, code, message); }

    public static ApiException forbidden(String message) { return new ApiException(HttpStatus.FORBIDDEN, message); }
    public static ApiException forbidden(String code, String message) { return new ApiException(HttpStatus.FORBIDDEN, code, message); }

    public static ApiException notFound(String message) { return new ApiException(HttpStatus.NOT_FOUND, message); }
    public static ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }

    public static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, message); }
    public static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }

    public static ApiException internalServerError(String message) { return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, message); }
    public static ApiException internalServerError(String code, String message) { return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, code, message); }
}
