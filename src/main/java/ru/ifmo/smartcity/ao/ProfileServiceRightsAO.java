package ru.ifmo.smartcity.ao;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.ifmo.smartcity.ProfileManagerException;
import ru.ifmo.smartcity.ProfileOntologyUtils;
import ru.ifmo.smartcity.RightsRequestDescription;
import ru.ifmo.smartcity.entities.ProfileServiceRights;
import ru.ifmo.smartcity.entities.ProfileServiceRightsId;
import ru.ifmo.smartcity.entities.RightsRequest;
import ru.ifmo.smartcity.entities.User;
import ru.ifmo.smartcity.repositories.ProfileServiceRightsRepository;
import ru.ifmo.smartcity.repositories.RightsRequestRepository;
import ru.ifmo.smartcity.repositories.ServiceRepository;
import ru.ifmo.smartcity.repositories.UserRepository;

/**
 * Created by igor on 06.07.18.
 */
@Service
public class ProfileServiceRightsAO {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private ProfileServiceRightsRepository profileServiceRightsRepository;

    @Autowired
    private RightsRequestRepository rightsRequestRepository;

    @Autowired
    private Model typeProfileOntology;



    public Model getMask(String userLogin, String serviceName)
    {
        User user = userRepository.findByLogin(userLogin);
        if (user == null) {
            throw new ProfileManagerException("No user with login "+userLogin);
        }

        ru.ifmo.smartcity.entities.Service service = serviceRepository.findByName(serviceName);
        if (service == null) {
            throw new ProfileManagerException("No service with name "+serviceName);
        }

        ProfileServiceRights rights = profileServiceRightsRepository.findById(new ProfileServiceRightsId(user.getId(), service.getId()));
        if (rights == null)
        {
            return ModelFactory.createDefaultModel();
        }

        return ProfileOntologyUtils.deserializeModelWithZip(rights.getMask());
    }


    public void updateRights(String userLogin, String serviceName, String maskQuery, boolean isAdd)
    {
        User user = userRepository.findByLogin(userLogin);
        if (user == null) {
            throw new ProfileManagerException("No user with login "+userLogin);
        }

        ru.ifmo.smartcity.entities.Service service = serviceRepository.findByName(serviceName);
        if (service == null) {
            throw new ProfileManagerException("No service with name "+serviceName);
        }

        ProfileServiceRights rights = profileServiceRightsRepository.findById(new ProfileServiceRightsId(user.getId(), service.getId()));

        if (rights == null)
        {
            if (isAdd) {
                Model mask = ProfileOntologyUtils.buildProfileMask(typeProfileOntology, maskQuery);
                profileServiceRightsRepository.save(new ProfileServiceRights(user.getId(), service.getId(), ProfileOntologyUtils.serializeModelWithZip(mask)));
            }
        }
        else
        {
            Model pMask = ProfileOntologyUtils.deserializeModelWithZip(rights.getMask());
            if (isAdd) {
                Model mask = ProfileOntologyUtils.buildProfileMask(typeProfileOntology, maskQuery);
                pMask.add(mask);
            } else {
                pMask = ProfileOntologyUtils.removeFromProfileMask(typeProfileOntology, maskQuery, pMask);
            }
            rights.setMask(ProfileOntologyUtils.serializeModelWithZip(pMask));
        }
    }


    public RightsRequestDescription getLastRightsRequest(String userLogin, String serviceName)
    {
        User user = userRepository.findByLogin(userLogin);
        if (user == null) {
            throw new ProfileManagerException("No user with login "+userLogin);
        }

        ru.ifmo.smartcity.entities.Service service = serviceRepository.findByName(serviceName);
        if (service == null) {
            throw new ProfileManagerException("No service with name "+serviceName);
        }

        RightsRequest rightsRequest = rightsRequestRepository.findFirstByUserIdAndServiceIdOrderByIdDesc(user.getId(), service.getId());
        if (rightsRequest == null)
        {
            return null;
        }

        return new RightsRequestDescription(rightsRequest);
    }


    public byte[] getRightsRequestImageSVG(String userLogin, long requestId)
    {
        User user = userRepository.findByLogin(userLogin);
        if (user == null) {
            throw new ProfileManagerException("No user with login "+userLogin);
        }

        RightsRequest rightsRequest = rightsRequestRepository.findById(requestId);

        if (rightsRequest == null) {
            throw new ProfileManagerException("No rights request with id "+requestId);
        }

        if (user.getId() != rightsRequest.getUserId()) {
            throw new ProfileManagerException("Wrong user login specified");
        }

        ProfileServiceRights rights = profileServiceRightsRepository.findById(new ProfileServiceRightsId(rightsRequest.getUserId(), rightsRequest.getServiceId()));
        Model curMask = rights == null ? ModelFactory.createDefaultModel() : ProfileOntologyUtils.deserializeModelWithZip(rights.getMask());
        Model addMask = ProfileOntologyUtils.deserializeModelWithZip(rightsRequest.getAddModel());
        return ProfileOntologyUtils.imageMaskSVG(addMask, ProfileOntologyUtils.getMaskDiff(addMask, curMask));
    }


    public void createRightsRequest(String userLogin, String serviceName, String maskRequest, boolean force)
    {
        User user = userRepository.findByLogin(userLogin);
        if (user == null) {
            throw new ProfileManagerException("No user with login "+userLogin);
        }

        ru.ifmo.smartcity.entities.Service service = serviceRepository.findByName(serviceName);
        if (service == null) {
            throw new ProfileManagerException("No service with name "+serviceName);
        }

        Model mask = ProfileOntologyUtils.buildProfileMask(typeProfileOntology, maskRequest);

        // TODO: проверить запрос
        RightsRequest lastRightsRequest = rightsRequestRepository.findFirstByUserIdAndServiceIdOrderByIdDesc(user.getId(), service.getId());
        if (lastRightsRequest != null && lastRightsRequest.getAccepted() == null)
        {
            if (!force)
            {
                throw new ProfileManagerException("There is unconfirmed request. To create a new request 'force' must be set true");
            }

            // Завершаем текущий запрос
            lastRightsRequest.setAccepted(false);
        }

        // Создаем новый запрос
        rightsRequestRepository.save(new RightsRequest(user.getId(), service.getId(), ProfileOntologyUtils.serializeModelWithZip(mask)));
    }


    public void handleRightsRequest(String userLogin, long requestId, boolean confirm)
    {
        User user = userRepository.findByLogin(userLogin);
        if (user == null) {
            throw new ProfileManagerException("No user with login "+userLogin);
        }

        // TODO: проверять, что подтверждает запросивший пользователь
        RightsRequest rightsRequest = rightsRequestRepository.findById(requestId);

        if (rightsRequest == null) {
            throw new ProfileManagerException("No rights request with id "+requestId);
        }

        if (rightsRequest.getAccepted() != null) {
            throw new ProfileManagerException("Rights is already "+(rightsRequest.getAccepted() ? "accepted" : "declined"));
        }

        if (user.getId() != rightsRequest.getUserId()) {
            throw new ProfileManagerException("Wrong user login specified");
        }

        rightsRequest.setAccepted(confirm);

        if (confirm) {
            ProfileServiceRights rights = profileServiceRightsRepository.findById(new ProfileServiceRightsId(rightsRequest.getUserId(), rightsRequest.getServiceId()));

            Model mask = rights == null ? ModelFactory.createDefaultModel() : ProfileOntologyUtils.deserializeModelWithZip(rights.getMask());
            mask.add(ProfileOntologyUtils.deserializeModelWithZip(rightsRequest.getAddModel()));

            if (rights != null) {
                rights.setMask(ProfileOntologyUtils.serializeModelWithZip(mask));
            } else {
                profileServiceRightsRepository.save(new ProfileServiceRights(rightsRequest.getUserId(),
                        rightsRequest.getServiceId(), ProfileOntologyUtils.serializeModelWithZip(mask)));
            }
        }
    }
}