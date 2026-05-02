package id.co.jalin.seconsole.repository;

import id.co.jalin.seconsole.domain.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {
    // Query methods added when Audit Trail page is built.
}
