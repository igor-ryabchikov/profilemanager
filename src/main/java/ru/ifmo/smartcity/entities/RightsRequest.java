package ru.ifmo.smartcity.entities;

import javax.persistence.*;
import java.util.Date;

/**
 * Created by igor on 06.07.18.
 */
@Entity
@Table(indexes = {@Index(name = "userServiceIndex",  columnList="userId,serviceId")})
public class RightsRequest {
    @Id
    @GeneratedValue
    private long id;

    @Column(nullable = false, updatable = false)
    private long userId;

    @Column(nullable = false, updatable = false)
    private long serviceId;

    @Column
    private Boolean accepted;

    @Column(updatable = false, nullable = false, insertable = false, columnDefinition="TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Date createTime;

    @Lob
    @Column(nullable = false, updatable = false)
    private byte[] addModel;


    public RightsRequest()
    {}

    public RightsRequest(long userId, long serviceId, byte[] addModel) {
        this.userId = userId;
        this.serviceId = serviceId;
        this.addModel = addModel;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public Boolean getAccepted() {
        return accepted;
    }

    public void setAccepted(Boolean accepted) {
        this.accepted = accepted;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public byte[] getAddModel() {
        return addModel;
    }

    public void setAddModel(byte[] addModel) {
        this.addModel = addModel;
    }
}
