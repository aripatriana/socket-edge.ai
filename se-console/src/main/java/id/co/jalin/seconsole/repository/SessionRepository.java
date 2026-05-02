package id.co.jalin.seconsole.repository;

import id.co.jalin.seconsole.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, String> {
    // PK is jti (String) — default findById(jti) suffices for revocation check.
}
