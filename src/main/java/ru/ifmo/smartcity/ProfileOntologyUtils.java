package ru.ifmo.smartcity;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.*;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * Created by igor on 01.07.18.
 */
public class ProfileOntologyUtils {
    private static final String ADD_PARENT_PROPERTIES_SPARQL =
            "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                    "INSERT { ?cl ?pProp ?pObj}\n" +
                    "WHERE {\n" +
                    "?cl rdfs:subClassOf+ ?pCl .\n" +
                    "?pCl ?pProp ?pObj .\n" +
                    "?pCl !rdfs:subClassOf ?pObj\n" +
                    "}";
    private static final String ADD_PARENT_PROPERTIES_INV_SPARQL =
            "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                    "INSERT {?pSubj ?pInverseProp ?cl}\n" +
                    "WHERE {\n" +
                    "?cl rdfs:subClassOf+ ?pCl .\n" +
                    "?pSubj ?pInverseProp ?pCl .\n" +
                    "?pSubj !rdfs:subClassOf ?pCl\n" +
                    "}";

    private static final String GET_VALID_CONCEPTS_COUNT_SPARQL =
            "SELECT (count(?c) AS ?cCount) { \n" +
                    "    <http://profmanager.com/User> (!(<http://NotUsedPred>|^<http://NotUsedPred>))* ?c\n" +
                    "}";


    private static final String FILTER_EXTRA_TRIPLES_SPARQL = "CONSTRUCR WHERE {\n" +
            "    <http://profmanager.com/User> (!(<http://NotUsedPred>|^<http://NotUsedPred>))* ?c0\n" +
            "    <http://profmanager.com/User> (!(<http://NotUsedPred>|^<http://NotUsedPred>))* ?c1\n" +
            "    ?c0 ?p ?c1\n" +
            "}";

    /**
     * Добавляет родительские свойства дочерним для окончательного офрмирвоания онтологии типов.
     *
     * @param typeOntologyWithoutParentProp онтология типов без родительских свойств у дочерних
     */
    public static void addParentProperties(Model typeOntologyWithoutParentProp) {
        UpdateAction.parseExecute(ADD_PARENT_PROPERTIES_SPARQL, typeOntologyWithoutParentProp);
        UpdateAction.parseExecute(ADD_PARENT_PROPERTIES_INV_SPARQL, typeOntologyWithoutParentProp);
    }


    /**
     * Приеняет запрос к онтологии типов для получения маски.
     *
     * @param typeOntology онтология типов
     * @param maskQueryStr CONSTRUCT запрос маски
     * @return маска или null, если правила были нарушены
     */
    public static Model getProfileMask(Model typeOntology, String maskQueryStr) {
        Query maskQuery = QueryFactory.create(maskQueryStr);
        QueryExecution maskQExec = QueryExecutionFactory.create(maskQuery, typeOntology);
        Model mask = maskQExec.execConstruct();
        maskQExec.close();

        if (!typeOntology.containsAll(mask))
        {
            throw new ProfileManagerException("Type ontology does not contain all selected triples");
        }

        Query cCountQuery = QueryFactory.create(GET_VALID_CONCEPTS_COUNT_SPARQL);
        QueryExecution cCountQExec = QueryExecutionFactory.create(cCountQuery, mask);
        int cCount = cCountQExec.execSelect().next().getLiteral("cCount").getInt();
        cCountQExec.close();

        Set<RDFNode> nodes = new HashSet<>();
        StmtIterator iter = mask.listStatements();
        while (iter.hasNext()) {
            Statement st = iter.nextStatement();
            nodes.add(st.getSubject());
            nodes.add(st.getObject());
        }

        System.out.println(mask.toString());

        if (cCount != nodes.size()) {
            throw new ProfileManagerException("Not all concepts are connected to the Uses. CCount: "
                    + nodes.size() + " vs " + cCount);
        }

        return mask;
    }


    /**
     * Возвращает триплеты, которые присутствуют в mask0, но отсутствуют в mask1.
     *
     * @param mask0 запрошенные права
     * @param mask1 имеющиеся права
     * @return недостающие права
     */
    public static Model getMaskDiff(Model mask0, Model mask1) {
        Model diff = ModelFactory.createDefaultModel();
        diff.add(mask0);
        diff.remove(mask1);
        return diff;
    }


    /**
     * Применяет маску к онтологии типов.
     *
     * @param profileOntology онтология профиля
     * @param typeOntology    онтология типов
     * @param mask            маска
     * @return результирующая онтология
     */
    public static Model applyMask(Model profileOntology, Model typeOntology, Model mask)
    {
        Query typeOQuery = QueryFactory.create(buildMaskSelectQuery(typeOntology));
        QueryExecution typeOQExec = QueryExecutionFactory.create(typeOQuery, mask);
        Model typeOProfile = typeOQExec.execConstruct();
        typeOQExec.close();

        Query maskQuery = QueryFactory.create(buildMaskSelectQuery(typeOntology));
        QueryExecution maskQExec = QueryExecutionFactory.create(maskQuery, mask);
        Model maskProfile = maskQExec.execConstruct();
        maskQExec.close();

        Model filteredProfWithExtra = ModelFactory.createDefaultModel();
        filteredProfWithExtra.add(profileOntology);
        filteredProfWithExtra.remove(typeOProfile);
        filteredProfWithExtra.add(maskProfile);

        // Удаляем лишние триплеты, которые не могут быть достигнуты из корня

        // TODO: протестировать и, возможно, упростить запрос
        Query filterQuery = QueryFactory.create(FILTER_EXTRA_TRIPLES_SPARQL);
        QueryExecution filterQExec = QueryExecutionFactory.create(filterQuery, filteredProfWithExtra);
        Model profile = filterQExec.execConstruct();
        filterQExec.close();

        return profile;
    }


    private static String buildMaskSelectQuery(Model mask)
    {
        Set<Resource> resources = new HashSet<>();

        StmtIterator iter = mask.listStatements();
        while (iter.hasNext())
        {
            Statement st = iter.nextStatement();
            resources.add(st.getSubject());
            if (st.getObject().isResource())
            {
                resources.add(st.getObject().asResource());
            }
        }

        resources.remove(RDFS.Literal);

        // Формируем конструирующий запрос

        StringBuilder sb = new StringBuilder();
        sb.append("CONSTRUCT WHERE { \n");
        Map<Resource, String> resToVarMap = new HashMap<>();
        int rCounter = 0;
        for (Resource r : resources)
        {
            String rName = "r"+(rCounter++);
            resToVarMap.put(r, rName);
            sb.append("?" + rName + " " + RDF.type + " " + r.getURI()+" .\n");
        }

        int lCounter = 0;
        iter = mask.listStatements();
        while (iter.hasNext())
        {
            Statement st = iter.nextStatement();
            sb.append(resToVarMap.get(st.getSubject()) + " " + st.getPredicate().getURI() + " "
                    + (st.getObject().isResource() ? resToVarMap.get(st.getObject().asResource()) : "l"+(lCounter++)) + " .\n");
            if (st.getObject().isLiteral())
            {
                sb.append("FILTER isLiteral(?l"+(lCounter-1)+") .\n");
            }
        }

        sb.append("}");

        return sb.toString();
    }


    /**
     * Создает изображение маски.
     *
     * @param mask маска онтологии профиля
     * @param markedTriples триплеты, которые нужно выделить среди остальных
     * @return изображение
     */
    public static byte[] imageMask(Model mask, Model markedTriples)
    {
        // TODO: implement
        throw new UnsupportedOperationException();
    }
}
