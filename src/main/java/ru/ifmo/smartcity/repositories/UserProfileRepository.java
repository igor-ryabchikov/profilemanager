package ru.ifmo.smartcity.repositories;

import org.springframework.data.repository.Repository;
import ru.ifmo.smartcity.entities.UserProfile;

/**
 * Created by igor on 06.07.18.
 */
public interface UserProfileRepository extends Repository<UserProfile, Long> {
    UserProfile save(UserProfile userProfile);
    UserProfile findById(long id);
}
