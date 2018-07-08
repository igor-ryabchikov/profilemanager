package ru.ifmo.smartcity.ao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.ifmo.smartcity.ProfileManagerException;
import ru.ifmo.smartcity.repositories.ServiceRepository;

/**
 * Created by igor on 06.07.18.
 */
@Service
public class ServiceAO {
    @Autowired
    private ServiceRepository serviceRepository;

    public void createService(String name)
    {
        ru.ifmo.smartcity.entities.Service service = serviceRepository.findByName(name);
        if (service == null) {
            serviceRepository.save(new ru.ifmo.smartcity.entities.Service(name));
        } else {
            throw new ProfileManagerException(String.format("Service with name %s already exists", name));
        }
    }
}
