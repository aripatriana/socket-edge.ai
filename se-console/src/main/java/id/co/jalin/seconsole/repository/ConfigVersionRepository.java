package id.co.jalin.seconsole.repository;

import id.co.jalin.seconsole.domain.ConfigVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConfigVersionRepository extends JpaRepository<ConfigVersion, Long> {

    /** Full version list for a file, newest first. */
    List<ConfigVersion> findByFileNameOrderByVersionDesc(String fileName);

    /** Find one specific version. */
    Optional<ConfigVersion> findByFileNameAndVersion(String fileName, Integer version);

    /** Latest version number, used to compute {@code newVersion = latest + 1}. */
    @Query("SELECT MAX(c.version) FROM ConfigVersion c WHERE c.fileName = :fileName")
    Optional<Integer> findMaxVersion(@Param("fileName") String fileName);
}
