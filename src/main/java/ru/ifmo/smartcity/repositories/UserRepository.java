package ru.ifmo.smartcity.repositories;

import org.springframework.data.repository.Repository;
import ru.ifmo.smartcity.entities.User;

/**
 * Created by igor on 06.07.18.
 */
public interface UserRepository extends Repository<User, Long> {
    User save(User user);
    User findByLogin(String login);
    User findById(long id);
}
