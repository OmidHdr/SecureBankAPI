package ir.h0p3.securebankapi.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT authSession
            FROM AuthSession authSession
            JOIN FETCH authSession.user
            WHERE authSession.id = :sessionId
            """)
    Optional<Session> findByIdForUpdate(
            @Param("sessionId") UUID sessionId
    );
}
