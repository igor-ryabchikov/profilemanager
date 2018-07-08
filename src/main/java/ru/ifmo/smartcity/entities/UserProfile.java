package ru.ifmo.smartcity.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;

/**
 * Created by igor on 06.07.18.
 */
@Entity
public class UserProfile {
    @Id
    private long userId;

    @Lob
    @Column(nullable = false)
    private byte[] profileData;


    UserProfile() {}

    public UserProfile(long userId, byte[] profileData) {
        this.userId = userId;
        this.profileData = profileData;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public byte[] getProfileData() {
        return profileData;
    }

    public void setProfileData(byte[] profileData) {
        this.profileData = profileData;
    }
}
