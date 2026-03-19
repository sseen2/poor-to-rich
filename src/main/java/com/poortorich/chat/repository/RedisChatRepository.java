package com.poortorich.chat.repository;

import com.poortorich.chat.request.enums.SortBy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisChatRepository {

    // 최신 스냅샷 버전 확인용 키
    private static final String REDIS_CURRENT_VERSION_KEY = "chatrooms:current_version";

    private static final String REDIS_CHAT_KEY = "chatrooms:sort:%s:version:%d:ids";
    private static final String REDIS_LAST_MESSAGE_TIME_KEY = "chatrooms:sort:%s:version:%d:times";

    private static final int CHAT_KEY_EXPIRATION_TIME = 5;

    private final RedisTemplate<String, String> redisTemplate;

    public Long getCurrentVersion() {
        String version = redisTemplate.opsForValue().get(REDIS_CURRENT_VERSION_KEY);

        if (version == null) {
            return -1L;
        }

        return Long.parseLong(version);
    }

    public void saveNewVersion(SortBy sortBy, List<Long> chatroomIds, List<String> lastMessageTimes, Long newVersion) {
        if (chatroomIds == null || lastMessageTimes == null) {
            return;
        }

        save(sortBy, chatroomIds, lastMessageTimes, newVersion);
    }

    private void save(SortBy sortBy, List<Long> chatroomIds, List<String> lastMessageTimes, Long version) {
        String idsKey = getRedisIdsKey(sortBy.name(), version);
        String timesKey = getRedisTimesKey(sortBy.name(), version);

        List<String> stringIds = chatroomIds.stream()
                .map(String::valueOf)
                .toList();

        redisTemplate.opsForList().rightPushAll(idsKey, stringIds);
        redisTemplate.opsForList().rightPushAll(timesKey, lastMessageTimes);

        redisTemplate.expire(idsKey, Duration.ofMinutes(CHAT_KEY_EXPIRATION_TIME));
        redisTemplate.expire(timesKey, Duration.ofMinutes(CHAT_KEY_EXPIRATION_TIME));
    }

    public void updateCurrentVersion(Long version) {
        redisTemplate.opsForValue().set(REDIS_CURRENT_VERSION_KEY, String.valueOf(version));
    }

    public List<Long> getChatroomIds(SortBy sortBy, Long cursor, Long version, int size) {
        String key = getRedisIdsKey(sortBy.name(), version);
        List<String> allIds = redisTemplate.opsForList().range(key, 0, -1);
        if (allIds == null || allIds.isEmpty()) {
            return List.of();
        }

        if (cursor == 0 || cursor == -1) {
            return allIds.subList(0, Math.min(size, allIds.size())).stream()
                    .map(Long::parseLong)
                    .toList();
        }

        int startIndex = allIds.indexOf(cursor.toString());
        List<String> result = allIds.subList(startIndex, Math.min(startIndex + size, allIds.size()));
        return result.stream()
                .map(Long::parseLong)
                .toList();
    }

    public List<String> getLastMessageTimes(SortBy sortBy, Long cursor, Long version, int size) {
        String idKey = getRedisIdsKey(sortBy.name(), version);
        String timeKey = getRedisTimesKey(sortBy.name(), version);

        List<String> allIds = redisTemplate.opsForList().range(idKey, 0, -1);
        List<String> allTimes = redisTemplate.opsForList().range(timeKey, 0, -1);

        if (allIds == null || allTimes == null || allIds.isEmpty() || allTimes.isEmpty()) {
            return List.of();
        }

        if (cursor == 0 || cursor == -1) {
            return allTimes.subList(0, Math.min(size, allTimes.size()));
        }

        int startIndex = allIds.indexOf(cursor.toString());
        return allTimes.subList(startIndex, Math.min(startIndex + size, allTimes.size()));
    }

    public boolean existsBySortBy(SortBy sortBy, Long version) {
        String key = getRedisIdsKey(sortBy.name(), version);
        return redisTemplate.hasKey(key);
    }

    public Boolean hasNext(SortBy sortBy, Long lastChatroomId, Long version) {
        List<String> stringIds = getAllList(sortBy, version);
        if (stringIds == null || stringIds.isEmpty()) {
            return false;
        }

        int idx = stringIds.indexOf(lastChatroomId.toString());
        return hasNext(idx, stringIds.size());
    }

    public Long getNextCursor(SortBy sortBy, Long lastChatroomId, Long version) {
        List<String> stringIds = getAllList(sortBy, version);
        if (stringIds == null || stringIds.isEmpty()) {
            return null;
        }

        int idx = stringIds.indexOf(lastChatroomId.toString());
        if (hasNext(idx, stringIds.size())) {
            return Long.parseLong(stringIds.get(idx + 1));
        }

        return null;
    }

    private List<String> getAllList(SortBy sortBy, Long version) {
        String key = getRedisIdsKey(sortBy.name(), version);
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    private Boolean hasNext(int idx, int size) {
        return idx != -1 && idx + 1 < size;
    }

    private String getRedisIdsKey(String sortBy, Long version) {
        return String.format(REDIS_CHAT_KEY, sortBy, version);
    }

    private String getRedisTimesKey(String sortBy, Long version) {
        return String.format(REDIS_LAST_MESSAGE_TIME_KEY, sortBy, version);
    }
}
