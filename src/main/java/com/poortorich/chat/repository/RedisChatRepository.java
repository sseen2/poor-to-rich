package com.poortorich.chat.repository;

import com.poortorich.chat.model.ChatroomContext;
import com.poortorich.chat.request.enums.SortBy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisChatRepository {

    private static final String REDIS_CURRENT_VERSION_KEY = "chatrooms:current_version";

    private static final String REDIS_CHAT_ZSET_KEY = "chatrooms:sort:%s:version:%d:zset";
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
        String key = getRedisChatKey(sortBy.name(), version);

        redisTemplate.opsForZSet().add(key, buildZset(chatroomIds, lastMessageTimes));
        redisTemplate.expire(key, Duration.ofMinutes(CHAT_KEY_EXPIRATION_TIME));
    }

    private Set<TypedTuple<String>> buildZset(List<Long> chatroomIds, List<String> lastMessageTimes) {
        Set<TypedTuple<String>> zset = new HashSet<>();
        int size = chatroomIds.size();
        for (int i = 0; i < size; i++) {
            String member = chatroomIds.get(i) + ":" + lastMessageTimes.get(i);
            zset.add(new DefaultTypedTuple<>(member, (double) (size - i)));
        }
        return zset;
    }

    public void updateCurrentVersion(Long version) {
        redisTemplate.opsForValue().set(REDIS_CURRENT_VERSION_KEY, String.valueOf(version));
    }

    public ChatroomContext getChatroomData(SortBy sortBy, Long cursor, Long version, int size) {
        Set<TypedTuple<String>> zset = getZset(sortBy, cursor, version, size + 1);

        if (zset.isEmpty()) {
            return ChatroomContext.builder()
                    .chatroomIds(List.of())
                    .lastMessageTimes(List.of())
                    .version(null)
                    .hasNext(false)
                    .nextCursor(null)
                    .build();
        }

        return parseChatroomContext(zset, version, size);
    }

    private Set<TypedTuple<String>> getZset(SortBy sortBy, Long cursor, Long version, int size) {
        String key = String.format(REDIS_CHAT_ZSET_KEY, sortBy.name(), version);

        double maxScore = cursor < 0 ? Double.MAX_VALUE : cursor.doubleValue() - 0.000001;

        Set<TypedTuple<String>> results = redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0.0, maxScore, 0, size);

        return results == null || results.isEmpty() ? Set.of() : results;
    }

    private ChatroomContext parseChatroomContext(Set<TypedTuple<String>> zset, Long version, int size) {
        boolean hasNext = zset.size() > size;

        List<Long> ids = new ArrayList<>();
        List<String> times = new ArrayList<>();
        Double lastScore = null;
        int count = 0;

        for (TypedTuple<String> tuple : zset) {
            if (count++ == size) break;
            String[] parts = tuple.getValue().split(":", 2);
            ids.add(Long.parseLong(parts[0]));
            times.add(parts.length > 1 ? parts[1] : "");
            lastScore = tuple.getScore();
        }

        return ChatroomContext.builder()
                .chatroomIds(ids)
                .lastMessageTimes(times)
                .version(version)
                .hasNext(hasNext)
                .nextCursor(lastScore != null ? lastScore.longValue() : null)
                .build();
    }

    public boolean existsBySortBy(SortBy sortBy, Long version) {
        String key = getRedisChatKey(sortBy.name(), version);
        return redisTemplate.hasKey(key);
    }

    private String getRedisChatKey(String sortBy, Long version) {
        return String.format(REDIS_CHAT_ZSET_KEY, sortBy, version);
    }
}
