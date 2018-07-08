package ru.ifmo.smartcity.ao;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import ru.ifmo.smartcity.ProfileManagerException;
import ru.ifmo.smartcity.ProfileOntologyUtils;
import ru.ifmo.smartcity.entities.ProfileServiceRights;
import ru.ifmo.smartcity.entities.ProfileServiceRightsId;
import ru.ifmo.smartcity.entities.User;
import ru.ifmo.smartcity.entities.UserProfile;
import ru.ifmo.smartcity.repositories.ProfileServiceRightsRepository;
import ru.ifmo.smartcity.repositories.ServiceRepository;
import ru.ifmo.smartcity.repositories.UserProfileRepository;
import ru.ifmo.smartcity.repositories.UserRepository;

/**
 * Created by igor on 06.07.18.
 */
@Service
public class UserAO {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private ProfileServiceRightsRepository profileServiceRightsRepository;

    @Autowired
    private Model typeProfileOntology;


    public void createUser(String login)
    {
        User user = userRepository.findByLogin(login);
        if (user == null) {
            user = userRepository.save(new User(login));

            Model profile = ModelFactory.createDefaultModel();
            profile.add(profile.createResource(ProfileOntologyUtils.BASE_URL+login), RDF.type, ProfileOntologyUtils.User);
            userProfileRepository.save(new UserProfile(user.getId(), ProfileOntologyUtils.serializeModelWithZip(profile)));

        } else {
            throw new ProfileManagerException(String.format("User with login %s already exists", login));
        }
    }


    public Model queryProfile(String userLogin, String constructQuery, @Nullable String sName)
    {
        User user = userRepository.findByLogin(userLogin);
        if (user == null)
        {
            throw new ProfileManagerException("No user with login "+userLogin);
        }

        UserProfile userProfile = userProfileRepository.findById(user.getId());
        Model profile = ProfileOntologyUtils.deserializeModelWithZip(userProfile.getProfileData());

        if (!StringUtils.isEmpty(sName))
        {
            ru.ifmo.smartcity.entities.Service service = serviceRepository.findByName(sName);
            if (service == null)
            {
                throw new ProfileManagerException("No service with name "+sName);
            }

            ProfileServiceRights serviceRights = profileServiceRightsRepository.findById(new ProfileServiceRightsId(user.getId(), service.getId()));
            if (serviceRights == null)
            {
                return ModelFactory.createDefaultModel();
            }

            Model mask = ProfileOntologyUtils.deserializeModelWithZip(serviceRights.getMask());
            profile = ProfileOntologyUtils.applyMask(profile, typeProfileOntology, mask);
        }

        Query query = QueryFactory.create(constructQuery);
        QueryExecution queryExec = QueryExecutionFactory.create(query, profile);
        Model resProfile = queryExec.execConstruct();
        queryExec.close();

        return resProfile;
    }


    public void updateProfile(String userLogin, Model remModel, Model addModel)
    {
        User user = userRepository.findByLogin(userLogin);
        if (user == null)
        {
            throw new ProfileManagerException("No user with login "+userLogin);
        }

        UserProfile userProfile = userProfileRepository.findById(user.getId());
        Model profile = ProfileOntologyUtils.deserializeModelWithZip(userProfile.getProfileData());

        if (remModel != null)
        {
            profile.remove(remModel);
        }

        if (addModel != null)
        {
            profile.add(addModel);
        }

        userProfile.setProfileData(ProfileOntologyUtils.serializeModelWithZip(profile));
    }
}
