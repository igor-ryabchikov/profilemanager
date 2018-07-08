package ru.ifmo.smartcity.entities;

import ru.ifmo.smartcity.ProfileOntologyUtils;

import java.io.Serializable;
import java.util.Objects;

/**
 * Created by igor on 06.07.18.
 */
public class ProfileServiceRightsId implements Serializable {

    private static final long serialVersionUID = 1659295008775697885L;

    private long userId;
    private long serviceId;

    ProfileServiceRightsId()
    {}

    public ProfileServiceRightsId(long userId, long serviceId) {
        this.userId = userId;
        this.serviceId = serviceId;
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


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProfileServiceRightsId that = (ProfileServiceRightsId) o;
        return userId == that.userId &&
                serviceId == that.serviceId;
    }

    @Override
    public int hashCode() {

        return Objects.hash(userId, serviceId);
    }
}
