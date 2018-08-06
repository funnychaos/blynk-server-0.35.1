package cc.blynk.server.internal;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The Blynk project
 * Created by Andrew Zakordonets
 * Date : 12/05/2015.
 */
public final class TokensPool {

    private static final Logger log = LogManager.getLogger(TokensPool.class);

    private final int tokenExpirationPeriodMillis;
    private final ConcurrentMap<String, TokenUser> holder;

    public TokensPool(int expirationPeriodMillis) {
        this.holder = new ConcurrentHashMap<>();
        this.tokenExpirationPeriodMillis = expirationPeriodMillis;
    }

    public void addToken(String token, TokenUser user) {
        log.info("Adding token for {} user to the pool", user.email);
        cleanupOldTokens();
        holder.put(token, user);
    }

    public TokenUser getUser(String token) {
        cleanupOldTokens();
        return holder.get(token);
    }

    public void removeToken(String token) {
        holder.remove(token);
    }

    public int size() {
        return holder.size();
    }

    private void cleanupOldTokens() {
        long now = System.currentTimeMillis();
        holder.entrySet().removeIf(entry -> entry.getValue().createdAt + tokenExpirationPeriodMillis < now);
    }

}
