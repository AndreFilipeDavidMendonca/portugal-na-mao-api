Patch V2 - Functional error messages polishing

Files changed:
- src/main/java/pt/dot/application/exception/ApiException.java
- src/main/java/pt/dot/application/exception/ErrorResponse.java
- src/main/java/pt/dot/application/exception/Errors.java
- src/main/java/pt/dot/application/exception/GlobalExceptionHandler.java
- src/main/java/pt/dot/application/service/friendship/FriendshipService.java
- src/main/java/pt/dot/application/service/chat/ChatService.java
- src/main/java/pt/dot/application/service/favorite/FavoriteService.java
- src/main/java/pt/dot/application/service/poi/PoiCommentService.java

What changed:
- Added clearer functional messages aimed at product UX.
- Added optional error codes in ApiException/ErrorResponse.
- Standardized friendship/chat statuses around BAD_REQUEST / FORBIDDEN / NOT_FOUND / CONFLICT.
- Added safer fallback handler messages, including:
  "Ocorreu um erro inesperado. Tenta novamente dentro de alguns segundos."
- Polished wording for session/auth, permissions, not found, and empty payload cases.

Main examples covered:
- Add friend and already exists:
  "Este utilizador já faz parte da tua lista de amigos."
- Add friend and user does not exist:
  "Não foi possível encontrar nenhum utilizador com este email. Confirma o endereço e tenta novamente."
- Pending invite already sent:
  "Já enviaste um pedido a este utilizador. Aguarda pela resposta."
- Pending invite already received:
  "Este utilizador já te enviou um pedido de amizade. Verifica os teus pedidos pendentes."

Notes:
- ErrorResponse now includes a new optional field: code.
- If the FE currently types the old error payload shape strictly, update it to accept `code?: string | null`.
