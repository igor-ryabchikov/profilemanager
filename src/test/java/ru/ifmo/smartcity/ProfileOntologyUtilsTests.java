package ru.ifmo.smartcity;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by igor on 01.07.18.
 */
public class ProfileOntologyUtilsTests
{
    @Test
    public void addParentPropertiesTest()
    {
        Model baseTO = ModelFactory.createDefaultModel();
        Resource a = baseTO.createResource("http://a");
        Resource aP = baseTO.createResource("http://aP");
        Resource aPP = baseTO.createResource("http://aPP");

        Property aProp = baseTO.createProperty("http://aProp");
        Property aPProp = baseTO.createProperty("http://aPProp");
        Property aPPProp0 = baseTO.createProperty("http://aPProp0");
        Property aPPProp1 = baseTO.createProperty("http://aPProp1");

        Resource aObj = baseTO.createResource("http://aObj");
        Resource aPObj0 = baseTO.createResource("http://aPObj0");
        Resource aPObj1 = baseTO.createResource("http://aPObj1");
        Resource aPPObj0 = baseTO.createResource("http://aPPObj0");
        Resource aPPObj1 = baseTO.createResource("http://aPPObj1");
        Resource aPPSubj = baseTO.createResource("http://aPPSubj");

        baseTO.add(a, RDFS.subClassOf, aP);
        baseTO.add(aP, RDFS.subClassOf, aPP);

        baseTO.add(a, aProp, aObj);
        baseTO.add(aP, aPProp, aPObj0);
        baseTO.add(aP, aPProp, aPObj1);
        baseTO.add(aPP, aPPProp0, aPPObj0);
        baseTO.add(aPP, aPPProp1, aPPObj1);
        baseTO.add(aPPSubj, aPPProp0, aPP);

        Model expectedTO = ModelFactory.createDefaultModel();
        expectedTO.add(baseTO);

        expectedTO.add(a, aPProp, aPObj0);
        expectedTO.add(a, aPProp, aPObj1);
        expectedTO.add(a, aPPProp0, aPPObj0);
        expectedTO.add(a, aPPProp1, aPPObj1);
        expectedTO.add(aP, aPPProp0, aPPObj0);
        expectedTO.add(aP, aPPProp1, aPPObj1);
        expectedTO.add(aPPSubj, aPPProp0, a);
        expectedTO.add(aPPSubj, aPPProp0, aP);

        ProfileOntologyUtils.addParentProperties(baseTO);

        if (!expectedTO.isIsomorphicWith(baseTO))
        {
            throw new AssertionError("Models are not isomorphic:\n"+expectedTO.toString()+"\n"+baseTO.toString());
        }
    }


    @Test
    public void getProfileMaskTest()
    {
        Model typeOnt = ModelFactory.createDefaultModel();

        Resource A = typeOnt.createResource("http://profmanager.com/User");
        Resource B = typeOnt.createResource("http://B");
        Resource C = typeOnt.createResource("http://C");
        Resource D = typeOnt.createResource("http://D");
        Resource E = typeOnt.createResource("http://E");
        Resource F = typeOnt.createResource("http://F");

        Property prop = typeOnt.createProperty("http://p");

        typeOnt.add(A, prop, B);
        typeOnt.add(B, prop, C);
        typeOnt.add(C, prop, D);
        typeOnt.add(D, prop, A);
        typeOnt.add(C, prop, E);
        typeOnt.add(F, prop, E);

        String positivePattern = "CONSTRUCT WHERE {\n" +
                "    <http://profmanager.com/User> <http://p> <http://B> .\n" +
                "    <http://B> <http://p> <http://C> .\n" +
                "    <http://C> <http://p> <http://E> .\n" +
                "    <http://D> <http://p> <http://profmanager.com/User>\n" +
                "}";

        String badPattern = "CONSTRUCT WHERE {\n" +
                "    <http://B> <http://p> <http://C> .\n" +
                "    <http://C> <http://p> <http://E> .\n" +
                "    <http://F> <http://p> <http://E>\n" +
                "}";

        Model res = ProfileOntologyUtils.getProfileMask(typeOnt, positivePattern);
        Assert.assertEquals(4, res.size());

        boolean exception = false;
        try
        {
            ProfileOntologyUtils.getProfileMask(typeOnt, badPattern);
        }
        catch (ProfileManagerException e)
        {
            exception = true;
            System.out.println("Exception: "+e.getMessage());
        }

        Assert.assertTrue(exception);
    }


    @Test
    public void applyMaskTest()
    {
        // TODO:
        // 1) Создать онтологию типов, профиль и маску. Так, чтобы были висящие незаданные в онтологии типов концепты. И с несколькими путями!!!
        // 2) Проверить, что в возвращаемом результате все пути
    }
}
