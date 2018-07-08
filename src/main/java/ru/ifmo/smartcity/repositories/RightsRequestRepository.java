package ru.ifmo.smartcity.repositories;

import org.springframework.data.repository.Repository;
import ru.ifmo.smartcity.entities.RightsRequest;

/**
 * Created by igor on 06.07.18.
 */
public interface RightsRequestRepository extends Repository<RightsRequest, Long> {
    RightsRequest save(RightsRequest rightsRequest);
    RightsRequest findById(long id);
    RightsRequest findFirstByUserIdAndServiceIdOrderByIdDesc(long userId, long serviceId);
}
