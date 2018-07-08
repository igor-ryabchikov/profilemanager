package ru.ifmo.smartcity;

import guru.nidi.graphviz.attribute.Color;
import guru.nidi.graphviz.attribute.Shape;
import guru.nidi.graphviz.attribute.Style;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.Factory;
import guru.nidi.graphviz.model.Graph;
import guru.nidi.graphviz.model.Label;
import guru.nidi.graphviz.model.Node;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Created by igor on 01.07.18.
 */
public class ProfileOntologyUtils {

    public static final String BASE_URL = "http://profmanager.com/";

    public static final Resource User;

    /**
     * Используется, поскольку Graphviz должен быть использован из единственного потока.
     */
    private static final ExecutorService es = Executors.newFixedThreadPool(1);

    static {
        Model m = ModelFactory.createDefaultModel();
        User = m.createResource(BASE_URL+"User");
    }


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
                    "    <http://profmanager.com/User> (!(<http://NotUsedPred>|^<http://NotUsedPred>))* ?c .\n" +
                    "    FILTER(<http://profmanager.com/User> != ?c) \n" +
                    "}";


    /*private static final String FILTER_EXTRA_TRIPLES_SPARQL = "CONSTRUCT WHERE {\n" +
            "    <http://profmanager.com/User> (!(<http://NotUsedPred>|^<http://NotUsedPred>))* ?c0 .\n" +
            "    <http://profmanager.com/User> (!(<http://NotUsedPred>|^<http://NotUsedPred>))* ?c1 .\n" +
            "    ?c0 ?p ?c1 .\n" +
            "}";
    */

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
     * Применяет запрос к онтологии типов для получения маски.
     *
     * @param typeOntology онтология типов
     * @param maskQueryStr CONSTRUCT запрос маски
     * @return маска или null, если правила были нарушены
     */
    public static Model buildProfileMask(Model typeOntology, String maskQueryStr) {
        Query maskQuery = QueryFactory.create(maskQueryStr);
        QueryExecution maskQExec = QueryExecutionFactory.create(maskQuery, typeOntology);
        Model mask = maskQExec.execConstruct();
        maskQExec.close();

        validateProfileMask(typeOntology, mask);

        return mask;
    }


    public static Model removeFromProfileMask(Model typeOntology, String maskQueryStr, Model prevProfileMask) {
        Query maskQuery = QueryFactory.create(maskQueryStr);
        QueryExecution maskQExec = QueryExecutionFactory.create(maskQuery, typeOntology);
        Model selectedTypeOnt = maskQExec.execConstruct();
        maskQExec.close();

        Model mask = ModelFactory.createDefaultModel();
        mask.add(prevProfileMask);
        mask.remove(selectedTypeOnt);

        validateProfileMask(typeOntology, mask);

        return mask;
    }


    private static void validateProfileMask(Model typeOntology, Model mask) {

        if (!typeOntology.containsAll(mask)) {
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

        if (cCount != nodes.size()-1) {
            throw new ProfileManagerException("Not all concepts are connected to the User. CCount: "
                    + (nodes.size()-1) + " vs " + cCount);
        }
    }


    public static boolean isMaskValid(Model mask)
    {
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

        return cCount == nodes.size();
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
        QueryExecution typeOQExec = QueryExecutionFactory.create(typeOQuery, profileOntology);
        Model typeOProfile = typeOQExec.execConstruct();
        typeOQExec.close();

        Query maskQuery = QueryFactory.create(buildMaskSelectQuery(mask));
        QueryExecution maskQExec = QueryExecutionFactory.create(maskQuery, profileOntology);
        Model maskProfile = maskQExec.execConstruct();
        maskQExec.close();

        Set<Resource> restrictedResources = getResourcesSet(typeOProfile);
        Set<Resource> selectedResources = getResourcesSet(maskProfile);

        Model extraTriplesModel = ModelFactory.createDefaultModel();
        extraTriplesModel.add(profileOntology);
        extraTriplesModel.remove(typeOProfile);

        System.out.println("MaskProf: "+maskProfile.toString());
        System.out.println("Selected res: "+selectedResources.toString());
        System.out.println("Restricted res: "+restrictedResources.toString());

        long prevSize = 0;
        while (prevSize != extraTriplesModel.size())
        {
            prevSize = extraTriplesModel.size();
            StmtIterator iter = extraTriplesModel.listStatements();

            while (iter.hasNext())
            {
                Statement st = iter.nextStatement();

                if ((selectedResources.contains(st.getSubject())
                        && (st.getObject().isLiteral() || selectedResources.contains(st.getObject().asResource())))

                        || (selectedResources.contains(st.getSubject()) && !restrictedResources.contains(st.getObject().asResource()))
                        || (st.getObject().isResource() && selectedResources.contains(st.getObject().asResource()) && !restrictedResources.contains(st.getSubject())))
                {
                    iter.remove();
                    maskProfile.add(st);
                    selectedResources.add(st.getSubject());
                    if (st.getObject().isResource())
                    {
                        selectedResources.add(st.getObject().asResource());
                    }
                }
            }
        }

        return maskProfile;
    }


    private static Set<Resource> getResourcesSet(Model m)
    {
        StmtIterator iter = m.listStatements();
        Set<Resource> r = new HashSet<>();
        while (iter.hasNext())
        {
            Statement st = iter.nextStatement();
            r.add(st.getSubject());
            if (st.getObject().isResource())
            {
                r.add(st.getObject().asResource());
            }
        }
        return r;
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

        Map<Resource, String> resToVarMap = new HashMap<>();
        int rCounter = 0;
        for (Resource r : resources)
        {
            String rName = "r"+(rCounter++);
            resToVarMap.put(r, rName);
        }

        // TODO: ОШИБКА - у пользователя может не быть всех элементов.
        // TODO: Решение - либо ввести поиск по графу. Либо - ДОБАВИТЬ в аккаунт пользователя элементы, чтобы запрос выполнялся
        // TODO: не уверен, будет ли работать второе

        StringBuilder sb = new StringBuilder();
        sb.append("CONSTRUCT {\n");

        int lCounter = 0;
        iter = mask.listStatements();
        while (iter.hasNext())
        {
            Statement st = iter.nextStatement();
            sb.append("?"+resToVarMap.get(st.getSubject()) + " <" + st.getPredicate().getURI() + "> ?"
                    + (st.getObject().isResource() ? resToVarMap.get(st.getObject().asResource()) : "l"+(lCounter++)) + " .\n");
        }

        sb.append("} WHERE { \n");

        for (Map.Entry<Resource, String> rEntry : resToVarMap.entrySet())
        {
            sb.append("?" + rEntry.getValue() + " <" + RDF.type + ">/<"+RDFS.subClassOf+">* <" + rEntry.getKey().getURI() + "> .\n");
        }

        lCounter = 0;
        iter = mask.listStatements();
        while (iter.hasNext())
        {
            Statement st = iter.nextStatement();
            sb.append("?"+resToVarMap.get(st.getSubject()) + " <" + st.getPredicate().getURI() + "> ?"
                    + (st.getObject().isResource() ? resToVarMap.get(st.getObject().asResource()) : "l"+(lCounter++)) + " .\n");
            if (st.getObject().isLiteral())
            {
                sb.append("FILTER isLiteral(?l"+(lCounter-1)+") \n");
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
    public static byte[] imageMaskSVG(Model mask, Model markedTriples)
    {
        Map<RDFNode, Node> nodes = new HashMap<>();
        StmtIterator iter = mask.listStatements();
        Graph g = Factory.graph("mask").directed();
        while (iter.hasNext()) {
            Statement st = iter.nextStatement();
            Node sNode = nodes.computeIfAbsent(st.getSubject(), s -> Factory.node(s.asResource().getURI()));
            Node oNode = nodes.computeIfAbsent(st.getObject(), o -> o.isResource() ?
                    Factory.node(o.asResource().getURI()) : Factory.node(o.asLiteral().getLexicalForm()).with(Shape.CIRCLE));

            g = g.with(sNode.link(Factory.to(oNode).with(Label.of(st.getPredicate().getURI()), markedTriples.contains(st) ? Color.RED : Color.BLACK)));
        }
        Graph gFinal = g;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            synchronized (es) {
                es.submit(() -> {
                    try {
                        Graphviz.fromGraph(gFinal).render(Format.SVG).toOutputStream(out);
                    } catch (IOException e) {
                        throw new ProfileManagerException(e);
                    }
                }).get();
            }
            return out.toByteArray();
        } catch(InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof ProfileManagerException) {
                throw (ProfileManagerException) e.getCause();
            } else {
                throw new ProfileManagerException(e);
            }
        }
    }

    public static byte[] serializeModel(Model model)
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(1024);

        model.write(bout, Lang.TURTLE.getName());

        return bout.toByteArray();
    }

    public static Model deserializeModel(byte[] sModel)
    {
        ByteArrayInputStream bin = new ByteArrayInputStream(sModel);
        Model model = ModelFactory.createDefaultModel();
        model.read(bin, null, Lang.TURTLE.getName());
        return model;
    }

    public static byte[] serializeModelWithZip(Model model)
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(1024);
        ZipOutputStream zip = new ZipOutputStream(bout);
        ZipEntry ze = new ZipEntry("model");
        try {
            zip.putNextEntry(ze);
            model.write(zip, Lang.TURTLE.getName());
            zip.closeEntry();
        } catch(IOException e) {
            throw new ProfileManagerException(e);
        }
        return bout.toByteArray();
    }


    public static Model deserializeModelWithZip(byte[] sModel)
    {
        ByteArrayInputStream bin = new ByteArrayInputStream(sModel);
        ZipInputStream zip = new ZipInputStream(bin);
        Model model = ModelFactory.createDefaultModel();
        try {
            zip.getNextEntry();
        } catch(IOException e) {
            throw new ProfileManagerException(e);
        }
        model.read(zip, null, Lang.TURTLE.getName());
        return model;
    }
}
