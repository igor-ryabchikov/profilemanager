package ru.ifmo.smartcity;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.ifmo.smartcity.ao.ProfileServiceRightsAO;
import ru.ifmo.smartcity.ao.ServiceAO;
import ru.ifmo.smartcity.ao.UserAO;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Created by igor on 06.07.18.
 */
@RestController
@Transactional(isolation = Isolation.SERIALIZABLE)
public class ProfileManagerController
{
    @Autowired
    private UserAO userAO;

    @Autowired
    private ServiceAO serviceAO;

    @Autowired
    private ProfileServiceRightsAO profileServiceRightsAO;

    @RequestMapping(path = "/user", method = RequestMethod.POST)
    public void createUser(@RequestParam(name = "login") String login)
    {
        userAO.createUser(login);
    }


    @RequestMapping(path = "/user/{uLogin}/profile", method = RequestMethod.POST)
    public String userProfile(@PathVariable(name = "uLogin") String uLogin,
                              @RequestParam(name = "rem", required = false, defaultValue = "") String rem,
                              @RequestParam(name = "add", required = false, defaultValue = "") String add,
                              @RequestParam(name = "query", required = false, defaultValue = "") String query,
                              @RequestParam(name = "sName", required = false, defaultValue = "") String sName)
    {
        Model remModel = null;
        Model addModel = null;

        if (!StringUtils.isEmpty(rem))
        {
            remModel = ProfileOntologyUtils.deserializeModel(Base64.decodeBase64(rem));
        }

        if (!StringUtils.isEmpty(add))
        {
            addModel = ProfileOntologyUtils.deserializeModel(Base64.decodeBase64(add));
        }

        if (remModel != null || addModel != null)
        {
            userAO.updateProfile(uLogin, remModel, addModel);
            return "";
        }
        else if (!StringUtils.isEmpty(query))
        {
            return Base64.encodeBase64String(ProfileOntologyUtils.serializeModel(userAO.queryProfile(uLogin, new String(Base64.decodeBase64(query)), sName)));
        }
        else
        {
            return "";
        }
    }


    @RequestMapping(path = "/service", method = RequestMethod.POST)
    public void createService(@RequestParam(name = "sName") String sName)
    {
        serviceAO.createService(sName);
    }


    @RequestMapping(path = "/user/{uLogin}/profile/rights", method = RequestMethod.POST)
    public void updateRights(@PathVariable(name = "uLogin") String uLogin,
                             @RequestParam(name = "sName") String sName,
                             @RequestParam(name = "typeOntQuery") String typeOntQuery,
                             @RequestParam(name = "isAdd", required = false, defaultValue = "true") boolean isAdd)
    {
        profileServiceRightsAO.updateRights(uLogin, sName, new String(Base64.decodeBase64(typeOntQuery)), isAdd);
    }


    @RequestMapping(path = "/user/{uLogin}/profile/rights", method = RequestMethod.GET)
    public String getRights(@PathVariable(name = "uLogin") String uLogin,
                          @RequestParam(name = "sName") String sName)
    {
        return Base64.encodeBase64String(ProfileOntologyUtils.serializeModel(profileServiceRightsAO.getMask(uLogin, sName)));
    }


    @RequestMapping(path = "/user/{uLogin}/profile/rights/request", method = RequestMethod.POST)
    public void createRightsRequest(@PathVariable(name = "uLogin") String uLogin,
                                    @RequestParam(name = "sName") String sName,
                                    @RequestParam(name = "req") String req,
                                    @RequestParam(name = "force", required = false, defaultValue = "false") boolean force)
    {
        profileServiceRightsAO.createRightsRequest(uLogin, sName, new String(Base64.decodeBase64(req)), force);
    }


    @RequestMapping(path = "/user/{uLogin}/profile/rights/request", method = RequestMethod.GET)
    public String getLastRightsRequest(@PathVariable(name = "uLogin") String uLogin,
                                   @RequestParam(name = "sName") String sName)
    {
        RightsRequestDescription rrd = profileServiceRightsAO.getLastRightsRequest(uLogin, sName);
        if (rrd == null)
        {
            return "{}";
        }
        else
        {
            return rrd.toJSONString();
        }
    }


    @RequestMapping(path = "/user/{uLogin}/profile/rights/request/{rId}/image", method = RequestMethod.GET)
    public void getRightsRequestImage(@PathVariable(name = "uLogin") String uLogin,
                                      @PathVariable(name = "rId") long rId,
                                      HttpServletResponse response) throws IOException
    {
        response.setContentType("image/svg+xml");
        response.getOutputStream().write(profileServiceRightsAO.getRightsRequestImageSVG(uLogin, rId));
    }


    @RequestMapping(path = "/user/{uLogin}/profile/rights/request/{rId}", method = RequestMethod.POST)
    public void handleRightsRequest(@PathVariable(name = "uLogin") String uLogin,
                                    @PathVariable(name = "rId") long rId,
                                    @RequestParam(name = "confirm") boolean confirm)
    {
        profileServiceRightsAO.handleRightsRequest(uLogin, rId, confirm);
    }
}
