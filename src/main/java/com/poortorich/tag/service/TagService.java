package com.poortorich.tag.service;

import com.poortorich.chat.entity.Chatroom;
import com.poortorich.tag.entity.Tag;
import com.poortorich.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    public void createTag(List<String> hashtags, Chatroom chatroom) {
        if (Objects.isNull(hashtags)) {
            return;
        }

        List<Tag> tag = hashtags.stream()
                .map(tagName -> buildTag(tagName, chatroom))
                .toList();

        tagRepository.saveAll(tag);
    }

    private Tag buildTag(String tagName, Chatroom chatroom) {
        return Tag.builder()
                .name(tagName)
                .chatroom(chatroom)
                .build();
    }

    public List<String> getTagNames(Chatroom chatroom) {
        return tagRepository.findByChatroom(chatroom).stream()
                .map(Tag::getName)
                .toList();
    }

    public Map<Long, List<String>> getTagNamesByChatroomIds(List<Long> chatroomIds) {
        return tagRepository.findByChatroomIds(chatroomIds).stream()
                .collect(Collectors.groupingBy(
                        tag -> tag.getChatroom().getId(),
                        Collectors.mapping(Tag::getName, Collectors.toList())
                ));
    }

    @Transactional
    public void updateTag(List<String> hashtags, Chatroom chatroom) {
        tagRepository.deleteByChatroom(chatroom);
        createTag(hashtags, chatroom);
    }

    @Transactional
    public void deleteAllByChatroom(Chatroom chatroom) {
        tagRepository.deleteByChatroom(chatroom);
    }
}
