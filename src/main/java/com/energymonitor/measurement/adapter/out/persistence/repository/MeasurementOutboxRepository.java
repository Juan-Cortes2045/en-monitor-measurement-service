package com.energymonitor.measurement.adapter.out.persistence.repository;

import com.energymonitor.measurement.adapter.out.persistence.entity.MeasurementOutboxEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository over {@code measurement_outbox}.
 */
@Repository
public interface MeasurementOutboxRepository extends JpaRepository<MeasurementOutboxEntity, String> {

    /**
     * The messages still waiting for the broker, oldest first, so the order in which readings were
     * taken is the order in which other services hear about them.
     */
    @Query("""
            select m from MeasurementOutboxEntity m
            where m.publishedAt is null and m.discardedAt is null
            order by m.occurredAt asc, m.idMessage asc
            """)
    List<MeasurementOutboxEntity> findPending(Pageable page);

    /**
     * Removes the messages published or discarded before an instant: they are only kept for a
     * while to look into a delivery. A bulk delete skips the persistence context, so it is cleared
     * afterwards: a removed message is never served from it.
     *
     * @return how many were removed
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from MeasurementOutboxEntity m
            where m.publishedAt < :before or m.discardedAt < :before
            """)
    int deleteDeliveredBefore(@Param("before") Instant before);
}
