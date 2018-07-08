package ru.ifmo.smartcity.repositories;

import org.springframework.data.repository.Repository;
import ru.ifmo.smartcity.entities.ProfileServiceRights;
import ru.ifmo.smartcity.entities.ProfileServiceRightsId;

/**
 * Created by igor on 06.07.18.
 */
public interface ProfileServiceRightsRepository extends Repository<ProfileServiceRights, ProfileServiceRightsId> {
    ProfileServiceRights save(ProfileServiceRights profileServiceRights);
    ProfileServiceRights findById(ProfileServiceRightsId id);
}
