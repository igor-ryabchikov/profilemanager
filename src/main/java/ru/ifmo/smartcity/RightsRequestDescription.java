package ru.ifmo.smartcity;

import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import ru.ifmo.smartcity.entities.RightsRequest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by igor on 07.07.18.
 */
public class RightsRequestDescription {

    private static final FastDateFormat DF = FastDateFormat.getInstance("yyyy-mm-dd hh:MM:ssZ");

    private long id;

    private RightsRequestStatus status;

    private Date createTime;

    private List<List<String>> mask;

    public RightsRequestDescription(RightsRequest rightsRequest)
    {
        this.id = rightsRequest.getId();
        if (rightsRequest.getAccepted() == null) {
            status = RightsRequestStatus.WAITING;
        } else if (rightsRequest.getAccepted()) {
            status = RightsRequestStatus.ACCEPTED;
        } else {
            status = RightsRequestStatus.DECLINED;
        }
        this.createTime = rightsRequest.getCreateTime();
        StmtIterator iter = ProfileOntologyUtils.deserializeModelWithZip(rightsRequest.getAddModel()).listStatements();
        mask = new ArrayList<>();
        while (iter.hasNext())
        {
            Statement st = iter.nextStatement();
            ArrayList<String> triple = new ArrayList<>();
            triple.add(st.getSubject().toString());
            triple.add(st.getPredicate().toString());
            triple.add(st.getObject().toString());
            mask.add(triple);
        }
    }

    public RightsRequestDescription(long id, RightsRequestStatus status, Date createTime, List<List<String>> mask) {
        this.id = id;
        this.status = status;
        this.createTime = createTime;
        this.mask = mask;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public RightsRequestStatus getStatus() {
        return status;
    }

    public void setStatus(RightsRequestStatus status) {
        this.status = status;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public List<List<String>> getMask() {
        return mask;
    }

    public void setMask(List<List<String>> mask) {
        this.mask = mask;
    }

    public enum RightsRequestStatus {
        WAITING, ACCEPTED, DECLINED;
    }

    public String toJSONString()
    {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id);
        jsonObject.put("status", status);
        jsonObject.put("createDate", DF.format(createTime));
        JSONArray maskArray = new JSONArray();
        for (List<String> t : mask) {
            JSONArray tArray = new JSONArray();
            tArray.addAll(t);
            maskArray.add(tArray);
        }
        jsonObject.put("mask", maskArray);
        return jsonObject.toJSONString();
    }
}
