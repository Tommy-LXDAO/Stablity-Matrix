package com.stability.martrix.service;

import com.stability.martrix.constants.ErrorCode;
import com.stability.martrix.dto.SessionContext;
import com.stability.martrix.dto.SessionResponse;
import com.stability.martrix.util.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 会话服务
 * 负责创建和管理会话
 */
@Service
public class SessionService {

    private static final Logger logger = LoggerFactory.getLogger(SessionService.class);

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    private final RedisTemplate<String, Object> redisTemplate;
    private final SnowflakeIdGenerator idGenerator;

    public SessionService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.idGenerator = new SnowflakeIdGenerator(1, 1); // 数据中心ID=1, 工作机器ID=1
    }

    /**
     * 创建新的会话
     * 生成会话ID并存储到Redis中，有效期24小时
     *
     * @return 会话响应
     */
    public SessionResponse createSession() {
        String sessionId = idGenerator.nextIdString();
        String key = SESSION_KEY_PREFIX + sessionId;
        SessionContext sessionContext = new SessionContext(sessionId);

        logger.info("创建会话: sessionId={}", sessionId);

        try {
            // 存储到Redis，设置24小时过期时间
            redisTemplate.opsForValue().set(key, sessionContext, SESSION_TTL);
            logger.info("会话创建成功: key={}", key);
            return new SessionResponse(sessionId, sessionContext.getCreatedAt(), sessionContext.getExpireAt());
        } catch (Exception e) {
            logger.error("创建会话时发生错误: sessionId={}, error={}", sessionId, e.getMessage(), e);
            return SessionResponse.fail(ErrorCode.SESSION_CREATE_FAILED, "创建会话失败: " + e.getMessage());
        }
    }

    /**
     * 获取会话上下文
     *
     * 注意：反序列化失败的原因是内部类缺少无参构造函数。
     * GenericJackson2JsonRedisSerializer需要无参构造函数来反序列化对象。
     * 已通过给所有内部类添加@NoArgsConstructor注解解决此问题。
     *
     * @param sessionId 会话ID
     * @return 会话上下文，如果不存在返回null
     */
    public SessionContext getSession(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof SessionContext) {
                logger.debug("获取会话成功: sessionId={}", sessionId);
                return (SessionContext) value;
            }
            logger.debug("会话不存在: sessionId={}", sessionId);
            return null;
        } catch (Exception e) {
            logger.error("获取会话失败: key={}, error={}", key, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 更新会话上下文
     *
     * @param sessionId 会话ID
     * @param sessionContext 会话上下文
     * @return 是否更新成功
     */
    public boolean updateSessionContext(String sessionId, SessionContext sessionContext) {
        String key = SESSION_KEY_PREFIX + sessionId;

        logger.info("更新会话: sessionId={}", sessionId);

        try {
            redisTemplate.opsForValue().set(key, sessionContext, SESSION_TTL);
            logger.info("会话更新成功: sessionId={}", sessionId);
            return true;
        } catch (Exception e) {
            logger.error("更新会话失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 删除会话
     *
     * @param sessionId 会话ID
     * @return 是否删除成功
     */
    public boolean deleteSession(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;

        logger.info("删除会话: sessionId={}", sessionId);

        try {
            Boolean deleted = redisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                logger.info("会话删除成功: sessionId={}", sessionId);
                return true;
            } else {
                logger.warn("会话不存在: sessionId={}", sessionId);
                return false;
            }
        } catch (Exception e) {
            logger.error("删除会话失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 检查会话是否存在
     *
     * @param sessionId 会话ID
     * @return 是否存在
     */
    public boolean sessionExists(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        try {
            Boolean exists = redisTemplate.hasKey(key);
            logger.debug("检查会话存在性: sessionId={}, exists={}", sessionId, exists);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            logger.error("检查会话存在性失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 刷新会话过期时间（重置为24小时）
     *
     * @param sessionId 会话ID
     * @return 是否刷新成功
     */
    public boolean refreshSession(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;

        logger.info("刷新会话: sessionId={}", sessionId);

        try {
            Boolean expired = redisTemplate.expire(key, SESSION_TTL);
            if (Boolean.TRUE.equals(expired)) {
                logger.info("会话刷新成功: sessionId={}", sessionId);
                return true;
            } else {
                logger.warn("会话不存在，刷新失败: sessionId={}", sessionId);
                return false;
            }
        } catch (Exception e) {
            logger.error("刷新会话失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
            return false;
        }
    }
}
