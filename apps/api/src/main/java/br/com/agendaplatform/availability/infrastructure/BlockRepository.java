package br.com.agendaplatform.availability.infrastructure;

import br.com.agendaplatform.availability.domain.Block;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockRepository extends JpaRepository<Block, UUID> {

    List<Block> findAllByOrganizationIdOrderByStartAtAsc(UUID organizationId);

    Optional<Block> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("select count(b) > 0 from Block b where b.organizationId = :organizationId "
            + "and b.startAt < :endAt and b.endAt > :startAt")
    boolean existsOverlapping(
            @Param("organizationId") UUID organizationId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);
}
