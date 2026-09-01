package org.dromara.profile.enterprise.transfer;

import cn.hutool.crypto.digest.BCrypt;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferChallengeStore.StageResult;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferChallengeStore.Verification;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferChallengeStore.VerificationStatus;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferChallengeStore.VerifiedChallenge;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.UUID;

@Component
public class RedisEnterpriseTransferChallengeStore implements EnterpriseTransferChallengeStore {

    private static final String CHALLENGE_PREFIX = "profile:enterprise:transfer:challenge:";
    private static final String RATE_PREFIX = "profile:enterprise:transfer:rate:";
    private static final String LOCK_PREFIX = "profile:enterprise:transfer:lock:";
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final Duration RATE_TTL = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RedissonClient client;

    public RedisEnterpriseTransferChallengeStore(RedissonClient client) {
        this.client = client;
    }

    @Override
    public StageResult stage(EnterpriseTransferChallenge challenge) {
        String rateKey = rateKey(challenge.sourceUserId(), challenge.targetUserId());
        RLock lock = client.getLock(LOCK_PREFIX + rateKey);
        lock.lock();
        try {
            RBucket<String> rate = client.getBucket(rateKey, StringCodec.INSTANCE);
            if (!rate.setIfAbsent(challenge.challengeId(), RATE_TTL)) {
                return StageResult.RATE_LIMITED;
            }
            RBucket<String> bucket = bucket(challenge.challengeId());
            if (!bucket.setIfAbsent(write(new StoredChallenge(challenge, null)), CHALLENGE_TTL)) {
                rate.delete();
                throw new EnterpriseTransferException("ENTERPRISE_TRANSFER_CHALLENGE_CONFLICT");
            }
            return StageResult.STAGED;
        } finally {
            unlock(lock);
        }
    }

    @Override
    public boolean activate(String challengeId) {
        return locked(challengeId, () -> {
            StoredChallenge stored = read(challengeId);
            if (stored == null || stored.challenge().state() != EnterpriseTransferChallenge.State.PENDING_DELIVERY) {
                return false;
            }
            save(challengeId, new StoredChallenge(stored.challenge().activate(), null));
            return true;
        });
    }

    @Override
    public Verification verify(String challengeId, long sourceUserId, String code) {
        return locked(challengeId, () -> {
            StoredChallenge stored = read(challengeId);
            if (stored == null || stored.challenge().state() != EnterpriseTransferChallenge.State.ACTIVE
                || stored.challenge().sourceUserId() != sourceUserId
                || stored.challenge().expiresAtEpochMilli() <= System.currentTimeMillis()) {
                return invalid();
            }
            if (!BCrypt.checkpw(code, stored.challenge().codeHash())) {
                EnterpriseTransferChallenge attempted = stored.challenge().failedAttempt();
                if (attempted.attempts() >= MAX_ATTEMPTS) {
                    bucket(challengeId).delete();
                } else {
                    save(challengeId, new StoredChallenge(attempted, null));
                }
                return invalid();
            }
            String token = stored.storageToken() == null ? UUID.randomUUID().toString() : stored.storageToken();
            StoredChallenge verified = new StoredChallenge(stored.challenge(), token);
            save(challengeId, verified);
            return new Verification(VerificationStatus.VERIFIED,
                new VerifiedChallenge(verified.challenge(), token));
        });
    }

    @Override
    public boolean consume(VerifiedChallenge verifiedChallenge) {
        String challengeId = verifiedChallenge.challenge().challengeId();
        return locked(challengeId, () -> {
            StoredChallenge stored = read(challengeId);
            if (stored == null || stored.storageToken() == null
                || !stored.storageToken().equals(verifiedChallenge.storageToken())) {
                return false;
            }
            return bucket(challengeId).delete();
        });
    }

    @Override
    public void revoke(String challengeId) {
        locked(challengeId, () -> bucket(challengeId).delete());
    }

    private void save(String challengeId, StoredChallenge stored) {
        long remaining = bucket(challengeId).remainTimeToLive();
        if (remaining <= 0) {
            bucket(challengeId).delete();
            return;
        }
        bucket(challengeId).set(write(stored), Duration.ofMillis(remaining));
    }

    private StoredChallenge read(String challengeId) {
        String value = bucket(challengeId).get();
        return value == null ? null : JSON.readValue(value, StoredChallenge.class);
    }

    private String write(StoredChallenge stored) {
        return JSON.writeValueAsString(stored);
    }

    private RBucket<String> bucket(String challengeId) {
        return client.getBucket(challengeKey(challengeId), StringCodec.INSTANCE);
    }

    private <T> T locked(String challengeId, java.util.function.Supplier<T> action) {
        RLock lock = client.getLock(LOCK_PREFIX + challengeId);
        lock.lock();
        try {
            return action.get();
        } finally {
            unlock(lock);
        }
    }

    private void unlock(RLock lock) {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    private Verification invalid() {
        return new Verification(VerificationStatus.INVALID, null);
    }

    public static String challengeKey(String challengeId) {
        return CHALLENGE_PREFIX + challengeId;
    }

    public static String rateKey(long sourceUserId, long targetUserId) {
        return RATE_PREFIX + sourceUserId + ":" + targetUserId;
    }

    private record StoredChallenge(EnterpriseTransferChallenge challenge, String storageToken) {
    }
}
