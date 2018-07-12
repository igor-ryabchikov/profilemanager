package ru.ifmo.smartcity;

import guru.nidi.graphviz.attribute.Color;
import guru.nidi.graphviz.attribute.Shape;
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
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.*;

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
                    "INSERT { ?s ?pProp ?o . }\n" +
                    "WHERE {\n" +
                    "?s rdfs:subClassOf* ?pS .\n" +
                    "?o rdfs:subClassOf* ?oS .\n" +
                    "?pS ?pProp ?oS .\n" +
                    "FILTER( rdfs:subClassOf != ?pProp) \n" +
                    "}";

    private static final String GET_VALID_CONCEPTS_COUNT_SPARQL =
            "SELECT (count(?c) AS ?cCount) { \n" +
                    "    <http://profmanager.com/User> (!(<http://NotUsedPred>|^<http://NotUsedPred>))* ?c .\n" +
                    "    FILTER(<http://profmanager.com/User> != ?c) \n" +
                    "}";


    /**
     * Добавляет родительские свойства дочерним для окончательного офрмирвоания онтологии типов.
     *
     * @param typeOntologyWithoutParentProp онтология типов без родительских свойств у дочерних
     */
    public static void addParentProperties(Model typeOntologyWithoutParentProp) {
        UpdateAction.parseExecute(ADD_PARENT_PROPERTIES_SPARQL, typeOntologyWithoutParentProp);
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

        return cCount == nodes.size()-1;
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
    public static Model applyMask(Model profileOntology, Model typeOntology, Model mask, String userLogin)
    {
        Model m = ModelFactory.createDefaultModel();
        m.add(mask);
        addSubClassesToMask(typeOntology, m);
        addParentProperties(m);

        String userURI = BASE_URL+userLogin;
        Model typeOProfile = filterByMask(profileOntology, typeOntology, userURI);
        Model maskProfile = filterByMask(profileOntology, m, userURI);

        Set<Resource> restrictedResources = getResourcesSet(typeOProfile);
        Set<Resource> selectedResources = getResourcesSet(maskProfile);

        Model extraTriplesModel = ModelFactory.createDefaultModel();
        extraTriplesModel.add(profileOntology);
        extraTriplesModel.remove(typeOProfile);

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


    private static void addSubClassesToMask(Model typeOntology, Model mask)
    {
        Map<Resource, List<Resource>> classesMap = new HashMap<>();
        StmtIterator iter = typeOntology.listStatements();
        while (iter.hasNext())
        {
            Statement st = iter.nextStatement();
            if (st.getPredicate().equals(RDFS.subClassOf))
            {
                List<Resource> subClasses = classesMap.computeIfAbsent(st.getObject().asResource(), k -> new ArrayList<>());
                subClasses.add(st.getSubject());
            }
        }

        Model subClassesModel = ModelFactory.createDefaultModel();
        iter = mask.listStatements();
        while (iter.hasNext()) {
            Statement st = iter.nextStatement();
            addSubClassesFromMap(st.getSubject(), classesMap, subClassesModel);
            if (st.getObject().isResource()) {
                addSubClassesFromMap(st.getObject().asResource(), classesMap, subClassesModel);
            }
        }

        mask.add(subClassesModel);
    }


    private static void addSubClassesFromMap(Resource resource, Map<Resource, List<Resource>> classesMap, Model subClassesModel)
    {
        List<Resource> subClasses = classesMap.get(resource);
        if (subClasses == null) {
            return;
        }

        // TODO: может произойти зацикливание - следует валидировать онтологию типов
        for (Resource subClass : subClasses)
        {
            subClassesModel.add(subClass, RDFS.subClassOf, resource);
            addSubClassesFromMap(subClass, classesMap, subClassesModel);
        }
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


    private static Model filterByMask(Model profile, Model mask, String userURI)
    {
        GNode pGraph = buildGraphFromModel(profile, userURI);
        GNode maskGraph = buildGraphFromModel(mask, User.getURI());

        Model filtered = ModelFactory.createDefaultModel();

        pGraph.setVisited(true);
        Queue<GNode> pNodes = new LinkedList<>();
        pNodes.add(pGraph);

        maskGraph.setVisited(true);
        Queue<GNode> mNodes = new LinkedList<>();
        mNodes.add(maskGraph);

        while (!pNodes.isEmpty()) {
            GNode pNode = pNodes.poll();
            GNode mNode = mNodes.poll();

            for (Edge edge : pNode.getEdges().values())
            {
                Edge mEdge = mNode.getEdges().get(new PropName(edge.getProp().getURI(), edge.getEndNode().getType(), edge.isDirect()));
                if (mEdge != null)
                {
                    if (mEdge.isDirect()) {
                        filtered.add(pNode.getRdfNode().asResource(), edge.getProp(), edge.getEndNode().getRdfNode());
                    } else {
                        filtered.add(edge.getEndNode().getRdfNode().asResource(), edge.getProp(), pNode.getRdfNode());
                    }

                    if (!edge.getEndNode().isVisited()) {
                        pNodes.add(edge.getEndNode());
                        mNodes.add(mEdge.getEndNode());

                        edge.getEndNode().setVisited(true);
                    }
                }
            }
        }

        return filtered;
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
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(1024);
            GZIPOutputStream zout = new GZIPOutputStream(bout, 2048);
            model.write(zout, Lang.TURTLE.getName());
            zout.close();
            return bout.toByteArray();
        } catch(IOException e) {
            throw new ProfileManagerException(e);
        }
    }


    public static Model deserializeModelWithZip(byte[] sModel)
    {
        try {
            ByteArrayInputStream bin = new ByteArrayInputStream(sModel);
            GZIPInputStream zin = new GZIPInputStream(bin);
            Model model = ModelFactory.createDefaultModel();
            model.read(zin, null, Lang.TURTLE.getName());
            return model;
        } catch (IOException e) {
            throw new ProfileManagerException(e);
        }
    }

    private static GNode buildGraphFromModel(Model model, String rootNodeURI)
    {
        Map<String, GNode> nodes = new HashMap<>();
        StmtIterator iter = model.listStatements();
        while (iter.hasNext())
        {
            Statement st = iter.nextStatement();

            GNode sNode = nodes.computeIfAbsent(st.getSubject().getURI(), k -> new GNode(st.getSubject()));
            if (st.getPredicate().equals(RDF.type)) {
                sNode.setType(st.getObject().asResource().getURI());
            } else {
                GNode oNode;
                if (st.getObject().isResource()) {
                    oNode = nodes.computeIfAbsent(st.getObject().asResource().getURI(), k -> new GNode(st.getObject()));
                    oNode.addEdge(new Edge(st.getPredicate(), sNode, false));
                } else {
                    oNode = new GNode(st.getObject());
                    oNode.setType(RDFS.Literal.getURI());
                }
                sNode.addEdge(new Edge(st.getPredicate(), oNode, true));
            }
        }

        return nodes.get(rootNodeURI);
    }

    private static class GNode
    {
        private final RDFNode rdfNode;
        private final Map<PropName, Edge> edges = new HashMap<>();
        private String type;
        private boolean isVisited;

        public GNode(RDFNode rdfNode) {
            this.rdfNode = rdfNode;
        }

        public RDFNode getRdfNode() {
            return rdfNode;
        }

        public Map<PropName, Edge> getEdges() {
            return edges;
        }

        public void addEdge(Edge e) {
            edges.put(new PropName(e.getProp().getURI(), e.getEndNode().getRdfNode().isResource() ?
                    e.getEndNode().getRdfNode().asResource().getURI() : e.getEndNode().getRdfNode().asLiteral().getLexicalForm(), e.isDirect()), e);
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isVisited() {
            return isVisited;
        }

        public void setVisited(boolean visited) {
            isVisited = visited;
        }
    }


    private static class Edge
    {
        private final Property prop;
        private final GNode endNode;
        private final boolean isDirect;

        public Edge(Property prop, GNode endNode, boolean isDirect) {
            this.prop = prop;
            this.endNode = endNode;
            this.isDirect = isDirect;
        }

        public Property getProp() {
            return prop;
        }

        public GNode getEndNode() {
            return endNode;
        }

        public boolean isDirect() {
            return isDirect;
        }
    }


    private static class PropName
    {
        private final String edgeName;
        private final String nodeName;
        private final boolean isDirect;

        public PropName(String edgeName, String nodeName, boolean isDirect) {
            this.edgeName = edgeName;
            this.nodeName = nodeName;
            this.isDirect = isDirect;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PropName propName = (PropName) o;
            return isDirect == propName.isDirect &&
                    Objects.equals(edgeName, propName.edgeName) &&
                    Objects.equals(nodeName, propName.nodeName);
        }

        @Override
        public int hashCode() {

            return Objects.hash(edgeName, nodeName, isDirect);
        }
    }
}
