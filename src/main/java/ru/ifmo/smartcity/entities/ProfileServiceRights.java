package ru.ifmo.smartcity.entities;

import javax.persistence.*;

/**
 * Created by igor on 06.07.18.
 */
@Entity
@IdClass(ProfileServiceRightsId.class)
public class ProfileServiceRights {
    @Id
    private long userId;
    @Id
    private long serviceId;
    @Lob
    @Column(nullable = false)
    private byte[] mask;

    public ProfileServiceRights() {}

    public ProfileServiceRights(long userId, long serviceId, byte[] mask) {
        this.userId = userId;
        this.serviceId = serviceId;
        this.mask = mask;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public long getServiceId() {
        return serviceId;
    }

    public void setServiceId(long serviceId) {
        this.serviceId = serviceId;
    }

    public byte[] getMask() {
        return mask;
    }

    public void setMask(byte[] mask) {
        this.mask = mask;
    }
}
