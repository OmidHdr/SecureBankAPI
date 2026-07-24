package ir.h0p3.securebankapi.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT refreshToken
            FROM RefreshToken refreshToken
            WHERE refreshToken.token = :token
            """)
    Optional<RefreshToken> findByTokenForUpdate(@Param("token") String token);

    @Modifying
    @Query(value = """
            WITH RECURSIVE token_chain AS (
                SELECT id, replaced_by
                FROM refresh_tokens
                WHERE id = :tokenId

                UNION

                SELECT refresh_token.id, refresh_token.replaced_by
                FROM refresh_tokens refresh_token
                JOIN token_chain
                    ON refresh_token.id = token_chain.replaced_by
            )
            UPDATE refresh_tokens
            SET revoked = true
            WHERE id IN (SELECT id FROM token_chain)
            """, nativeQuery = true)
    int revokeTokenChain(@Param("tokenId") Long tokenId);
}
