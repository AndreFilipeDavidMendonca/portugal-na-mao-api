package pt.dot.application.api.dto.chat;

import lombok.Getter;
import lombok.Setter;
import pt.dot.application.db.enums.ChatMessageType;

@Getter
@Setter
public class SendChatMessageDto {
    private ChatMessageType type;
    private String body;
    private Long poiId;
    private String poiName;
    private String poiImage;
}
