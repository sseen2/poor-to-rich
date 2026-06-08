package com.poortorich.broadcast;

import com.poortorich.websocket.stomp.command.subscribe.endpoint.SubscribeEndpoint;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class BroadcastService {

    private final SimpMessagingTemplate messagingTemplate;
    private final Timer chatroomBroadcastTimer;
    private final Timer myChatroomBroadcastTimer;

    public BroadcastService(SimpMessagingTemplate messagingTemplate, MeterRegistry meterRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.chatroomBroadcastTimer = Timer.builder("chat.broadcast.send.duration")
                .description("Time spent sending WebSocket broadcast messages")
                .tag("destination", "chatroom")
                .register(meterRegistry);
        this.myChatroomBroadcastTimer = Timer.builder("chat.broadcast.send.duration")
                .description("Time spent sending WebSocket broadcast messages")
                .tag("destination", "my_chatroom")
                .register(meterRegistry);
    }

    public void broadcastInChatroom(Long chatroomId, Object... objects) {
        for (Object obj : objects) {
            broadcastInChatroom(chatroomId, obj);
        }
    }

    public void broadcastInChatroom(Long chatroomId, Object object) {
        if (Objects.nonNull(object)) {
            chatroomBroadcastTimer.record(() ->
                    messagingTemplate.convertAndSend(
                            SubscribeEndpoint.CHATROOM_SUBSCRIBE_PREFIX + chatroomId,
                            object));
        }
    }

    public void broadcastInMyChatroom(Long userId, Object... objects) {
        for (Object obj : objects) {
            broadcastInMyChatroom(userId, obj);
        }
    }

    public void broadcastInMyChatroom(Long userId, Object obejct) {
        if (Objects.nonNull(obejct)) {
            myChatroomBroadcastTimer.record(() ->
                    messagingTemplate.convertAndSend(
                            SubscribeEndpoint.JOINED_CHATROOM_LIST_PREFIX + userId,
                            obejct));
        }
    }
}
