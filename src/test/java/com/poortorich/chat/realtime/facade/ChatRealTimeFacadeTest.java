package com.poortorich.chat.realtime.facade;

import com.poortorich.chat.entity.ChatParticipant;
import com.poortorich.chat.entity.Chatroom;
import com.poortorich.chat.realtime.collect.ChatPayloadCollector;
import com.poortorich.chat.realtime.model.PayloadContext;
import com.poortorich.chat.realtime.payload.request.ChatMessageRequestPayload;
import com.poortorich.chat.realtime.payload.request.MarkMessagesAsReadRequestPayload;
import com.poortorich.chat.realtime.payload.response.BasePayload;
import com.poortorich.chat.realtime.payload.response.MessageReadPayload;
import com.poortorich.chat.realtime.payload.response.UserChatMessagePayload;
import com.poortorich.chat.service.ChatMessageService;
import com.poortorich.chat.service.ChatParticipantService;
import com.poortorich.chat.service.ChatroomService;
import com.poortorich.chat.service.UnreadChatMessageService;
import com.poortorich.chat.util.manager.ChatroomLeaveManager;
import com.poortorich.chat.validator.ChatParticipantValidator;
import com.poortorich.chat.validator.ChatroomValidator;
import com.poortorich.chatnotice.service.ChatNoticeService;
import com.poortorich.user.entity.User;
import com.poortorich.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRealTimeFacadeTest {

    @Mock
    private UserService userService;
    @Mock
    private ChatroomService chatroomService;
    @Mock
    private ChatMessageService chatMessageService;
    @Mock
    private ChatParticipantService chatParticipantService;
    @Mock
    private UnreadChatMessageService unreadChatMessageService;
    @Mock
    private ChatNoticeService chatNoticeService;
    @Mock
    private ChatPayloadCollector payloadCollector;
    @Mock
    private ChatroomLeaveManager chatroomLeaveManager;
    @Mock
    private ChatroomValidator chatroomValidator;
    @Mock
    private ChatParticipantValidator participantValidator;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private ChatRealTimeFacade chatRealTimeFacade;

    @Test
    @DisplayName("Redis 구독 여부와 관계없이 모든 정상 참여자를 미읽음 대상으로 메시지를 저장한다")
    void createUserChatMessageSavesAllUnreadMembers() {
        String username = "sender";
        Long chatroomId = 1L;
        User user = User.builder().id(1L).username(username).build();
        Chatroom chatroom = Chatroom.builder().id(chatroomId).build();
        ChatParticipant sender = ChatParticipant.builder().user(user).chatroom(chatroom).build();
        ChatParticipant unreadMember = ChatParticipant.builder().chatroom(chatroom).build();
        ChatMessageRequestPayload request = new ChatMessageRequestPayload(chatroomId, null, "message");
        PayloadContext context = new PayloadContext(user, chatroom, sender);
        UserChatMessagePayload savedMessage = UserChatMessagePayload.builder()
                .messageId(10L)
                .chatroomId(chatroomId)
                .senderId(user.getId())
                .content("message")
                .unreadBy(List.of())
                .build();

        when(payloadCollector.getPayloadContext(username, chatroomId)).thenReturn(context);
        when(chatParticipantService.findUnreadMembers(chatroom, user))
                .thenReturn(List.of(unreadMember));
        when(chatMessageService.saveUserChatMessage(sender, List.of(unreadMember), request))
                .thenReturn(savedMessage);

        BasePayload result = chatRealTimeFacade.createUserChatMessage(username, request);

        assertThat(result).isNotNull();
        verify(chatParticipantService).findUnreadMembers(chatroom, user);
        verify(chatMessageService).saveUserChatMessage(sender, List.of(unreadMember), request);
    }

    @Test
    @DisplayName("클라이언트가 마지막으로 확인한 메시지 ID까지 읽음 처리한다")
    void markMessagesAsReadUntilLastReadMessageId() {
        String username = "reader";
        Long chatroomId = 1L;
        Long lastReadMessageId = 10L;
        User user = User.builder().id(2L).username(username).build();
        Chatroom chatroom = Chatroom.builder().id(chatroomId).build();
        ChatParticipant participant = ChatParticipant.builder().user(user).chatroom(chatroom).build();
        PayloadContext context = new PayloadContext(user, chatroom, participant);
        MarkMessagesAsReadRequestPayload request =
                new MarkMessagesAsReadRequestPayload(chatroomId, lastReadMessageId);
        MessageReadPayload readPayload = MessageReadPayload.builder()
                .chatroomId(chatroomId)
                .lastReadMessageId(lastReadMessageId)
                .userId(user.getId())
                .build();

        when(payloadCollector.getPayloadContext(username, chatroomId)).thenReturn(context);
        when(unreadChatMessageService.markMessageAsRead(participant, lastReadMessageId))
                .thenReturn(readPayload);

        BasePayload result = chatRealTimeFacade.markMessagesAsRead(username, request);

        assertThat(result.getPayload()).isEqualTo(readPayload);
        verify(unreadChatMessageService).markMessageAsRead(participant, lastReadMessageId);
    }
}
