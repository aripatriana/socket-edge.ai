package id.co.jalin.seconsole.repository;

import id.co.jalin.seconsole.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Users are a small table in practice (tens to low hundreds of rows for
 * an ops console), so a simple unpaginated {@code findAll}-style query is
 * fine. When the row count gets large enough to matter, swap to
 * {@code Pageable}; all call sites go through {@code UserManagementService}
 * so the change is localised.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /**
     * Filtered list for the admin Users table. All filter params are
     * optional — null means "don't filter on that axis". JPQL ordered
     * by username for a stable, scannable UI default.
     */
    @Query("""
            SELECT u FROM User u
            WHERE (:search IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:role   IS NULL OR u.role   = :role)
              AND (:status IS NULL OR u.status = :status)
            ORDER BY u.username ASC
            """)
    List<User> findFiltered(
            @Param("search") String search,
            @Param("role") id.co.jalin.seconsole.domain.Role role,
            @Param("status") id.co.jalin.seconsole.domain.UserStatus status
    );
}
