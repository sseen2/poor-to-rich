package com.poortorich.chat.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllChatroomsResponse {

    private Boolean hasNext;
    private String nextCursor;
    private List<ChatroomResponse> chatrooms;
}
