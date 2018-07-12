package ru.ifmo.smartcity;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Created by igor on 08.07.18.
 */
@Configuration
public class TypeProfileOntologyFactory {

    @Value("${typeProfileOntologyFilePath}")
    private String typeProfileOntologyFilePath;

    @Bean
    public Model typeProfileOntology() throws IOException
    {
        Model typeProfileOntology = ModelFactory.createDefaultModel();
        typeProfileOntology.read(new FileInputStream(typeProfileOntologyFilePath), null, Lang.TTL.getName());
        ProfileOntologyUtils.addParentProperties(typeProfileOntology);
        if (!ProfileOntologyUtils.isMaskValid(typeProfileOntology)) {
            throw new ProfileManagerException("TypeOntology is not valid. All triples must be reached from pm:User concept");
        }
        return typeProfileOntology;
    }
}
