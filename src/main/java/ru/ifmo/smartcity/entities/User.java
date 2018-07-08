package ru.ifmo.smartcity.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * Created by igor on 06.07.18.
 */
@Entity
public class User {

    @Id
    @GeneratedValue
    private long id;

    @Column(nullable = false, updatable = false, unique = true)
    private String login;

    public User() {}

    public User(String login) {
        this.login = login;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }
}
