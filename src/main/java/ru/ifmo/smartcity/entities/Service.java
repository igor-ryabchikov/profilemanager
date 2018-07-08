package ru.ifmo.smartcity.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * Created by igor on 06.07.18.
 */
@Entity
public class Service {
    @Id
    @GeneratedValue
    private long id;

    @Column(nullable = false, updatable = false, unique = true)
    private String name;

    Service() {}

    public Service(String name) {
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
