package ru.ifmo.smartcity.repositories;

import org.springframework.data.repository.Repository;
import ru.ifmo.smartcity.entities.Service;

/**
 * Created by igor on 06.07.18.
 */
public interface ServiceRepository extends Repository<Service, Long> {
    Service save(Service service);
    Service findByName(String name);
    Service findById(long id);
}
