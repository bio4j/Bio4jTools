/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ohnosequences.bio4j.tools;

import com.ohnosequences.bio4j.CommonData;
import com.ohnosequences.bio4j.neo4j.model.nodes.GoTermNode;
import com.ohnosequences.bio4j.neo4j.model.relationships.go.IsAGoRel;
import com.ohnosequences.bio4j.neo4j.model.util.Bio4jManager;
import com.ohnosequences.xml.api.model.XMLElement;
import com.ohnosequences.xml.api.model.XMLElementException;
import com.ohnosequences.xml.model.gexf.*;
import com.ohnosequences.xml.model.gexf.viz.VizColorXML;
import com.ohnosequences.xml.model.go.GOSlimXML;
import com.ohnosequences.xml.model.go.GoAnnotationXML;
import com.ohnosequences.xml.model.go.GoTermXML;
import org.jdom2.Element;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.index.RelationshipIndex;

import java.io.*;
import java.util.*;

/**
 * @author Pablo Pareja Tobes <ppareja@era7.com>
 */
public class ExportGoSetsToGephi {

	//private static final GoParentRel goParentRel = new GoParentRel(null);
	private static long edgesIdCounter = 0;

	public static void main(String[] args) {

		File annotationFile = new File(args[0]);
		File goSlimFile = new File(args[1]);
		File outputFile = new File(args[2]);

		try {
			BufferedReader reader = new BufferedReader(new FileReader(annotationFile));
			StringBuilder stBuilder = new StringBuilder();
			String line = null;
			System.out.println("reading annotation file...");
			while ((line = reader.readLine()) != null) {
				stBuilder.append(line);
			}
			reader.close();
			System.out.println("done!");

			GoAnnotationXML goAnnotationXML = new GoAnnotationXML(stBuilder.toString());

			stBuilder.delete(0, stBuilder.length());
			reader = new BufferedReader(new FileReader(goSlimFile));
			System.out.println("reading goslim file...");
			while ((line = reader.readLine()) != null) {
				stBuilder.append(line);
			}
			reader.close();
			System.out.println("done!");

			GOSlimXML gOSlimXML = new GOSlimXML(stBuilder.toString());

			System.out.println("exporting to gexf...");

			exportGoSlim(gOSlimXML, goAnnotationXML, outputFile);

			System.out.println("done!!! :)");


		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void exportGoSlim(GOSlimXML goSlimXML,
	                                GoAnnotationXML goAnnotationXML,
	                                File outputFile) {


		VizColorXML annotatorTermsColor;
		VizColorXML slimTermsColor;
		VizColorXML coincidenceColor;
		VizColorXML generalTermsColor;

		//orange
		annotatorTermsColor = new VizColorXML();
		annotatorTermsColor.setR(241);
		annotatorTermsColor.setG(134);
		annotatorTermsColor.setB(21);
		annotatorTermsColor.setA(255);

		//blue
		slimTermsColor = new VizColorXML();
		slimTermsColor.setR(21);
		slimTermsColor.setG(155);
		slimTermsColor.setB(241);
		slimTermsColor.setA(243);

		//dark grey
		coincidenceColor = new VizColorXML();
		coincidenceColor.setR(21);
		coincidenceColor.setG(15);
		coincidenceColor.setB(23);
		coincidenceColor.setA(23);

		//green
		generalTermsColor = new VizColorXML();
		generalTermsColor.setR(34);
		generalTermsColor.setG(177);
		generalTermsColor.setB(76);
		generalTermsColor.setA(243);

		BufferedWriter outBuff = null;

		try {

			outBuff = new BufferedWriter(new FileWriter(outputFile));

			outBuff.write("<?xml version=\"1.0\" encoding=\"UTF8\"?>" + "\n");
			outBuff.write("<" + GexfXML.TAG_NAME + ">\n");
			outBuff.write("<" + GraphXML.TAG_NAME + " defaultedgetype=\"directed\" >\n");

			//node attributes
			AttributesXML attributesXML = new AttributesXML();
			attributesXML.setClass(AttributesXML.NODE_CLASS);
			AttributeXML idAttributeXML = new AttributeXML();
			idAttributeXML.setId("0");
			idAttributeXML.setTitle("ID");
			idAttributeXML.setType("string");
			attributesXML.addAttribute(idAttributeXML);
			AttributeXML nameAttributeXML = new AttributeXML();
			nameAttributeXML.setId("1");
			nameAttributeXML.setTitle("Name");
			nameAttributeXML.setType("string");
			attributesXML.addAttribute(nameAttributeXML);
			AttributeXML aspectAttributeXML = new AttributeXML();
			aspectAttributeXML.setId("2");
			aspectAttributeXML.setTitle("Aspect");
			aspectAttributeXML.setType("string");
			attributesXML.addAttribute(aspectAttributeXML);
			AttributeXML slimTermAttributeXML = new AttributeXML();
			slimTermAttributeXML.setId("3");
			slimTermAttributeXML.setTitle("SlimTerm");
			slimTermAttributeXML.setType("string");
			attributesXML.addAttribute(slimTermAttributeXML);

			outBuff.write(attributesXML.toString() + "\n");

			StringBuilder nodesXMLStBuilder = new StringBuilder("<nodes>\n");
			StringBuilder edgesXMLStBuilder = new StringBuilder("<edges>\n");

			System.out.println("creating neo4j manager...");
			Bio4jManager manager = new Bio4jManager(CommonData.DATABASE_FOLDER);

			RelationshipIndex isAGoRelIndex = manager.getIsAGoRelIndex();
			Index<Node> goTermIdIndex = manager.getGoTermIdIndex();

			HashMap<String, NodeXML> nodesToBeExported = new HashMap<String, NodeXML>();
			List<EdgeXML> edgesToBeExported = new ArrayList<EdgeXML>();

			Set<String> allTermIds = new HashSet<String>();

			//-------------creating annotator go terms nodes--------------
			List<GoTermXML> annotators = goAnnotationXML.getAnnotatorGoTerms();
			for (GoTermXML goTermXML : annotators) {

				NodeXML nodeXML = new NodeXML();
				nodeXML.setId(goTermXML.getId());
				nodeXML.setLabel(goTermXML.getGoName());
				nodeXML.setColor(new VizColorXML((Element) annotatorTermsColor.asJDomElement().clone()));

				AttValuesXML attValuesXML = new AttValuesXML();
				AttValueXML aspectAttValue = new AttValueXML();
				aspectAttValue.setFor(2);
				aspectAttValue.setValue(goTermXML.getAspect());
				attValuesXML.addAttValue(aspectAttValue);
				nodeXML.setAttvalues(attValuesXML);

				nodesToBeExported.put(goTermXML.getId(), nodeXML);

				allTermIds.add(goTermXML.getId());
			}

			//---------------creating slim terms nodes--------------
			List<XMLElement> slimTerms = goSlimXML.getSlimSet().getChildren();

			for (XMLElement xMLElement : slimTerms) {
				GoTermXML goTermXML = new GoTermXML(xMLElement.asJDomElement());

				NodeXML nodeXML = nodesToBeExported.get(goTermXML.getId());

				if (nodeXML == null) {
					nodeXML = new NodeXML();
					nodeXML.setId(goTermXML.getId());
					nodeXML.setLabel(goTermXML.getGoName());
					nodeXML.setColor(new VizColorXML((Element) slimTermsColor.asJDomElement().clone()));

					AttValuesXML attValuesXML = new AttValuesXML();
					AttValueXML aspectAttValue = new AttValueXML();
					aspectAttValue.setFor(2);
					aspectAttValue.setValue(goTermXML.getAspect());
					attValuesXML.addAttValue(aspectAttValue);
					nodeXML.setAttvalues(attValuesXML);

					nodesToBeExported.put(goTermXML.getId(), nodeXML);

					allTermIds.add(goTermXML.getId());
				} else {
					nodeXML.setColor(new VizColorXML((Element) coincidenceColor.asJDomElement().clone()));
				}

			}

			for (String termId : allTermIds) {
				GoTermNode currentGoTermNode = new GoTermNode(goTermIdIndex.get(GoTermNode.GO_TERM_ID_INDEX, termId).getSingle());
				goToRootSavingNodes(nodesToBeExported,
						edgesToBeExported,
						currentGoTermNode,
						isAGoRelIndex,
						generalTermsColor);
			}

			for (String nodeKey : nodesToBeExported.keySet()) {
				nodesXMLStBuilder.append((nodesToBeExported.get(nodeKey).toString() + "\n"));
			}

			for (EdgeXML edgeXML : edgesToBeExported) {
				edgesXMLStBuilder.append((edgeXML.toString() + "\n"));
			}


			outBuff.write(nodesXMLStBuilder.toString() + "</nodes>\n");
			outBuff.write(edgesXMLStBuilder.toString() + "</edges>\n");

			outBuff.write("</" + GraphXML.TAG_NAME + ">\n");
			outBuff.write("</" + GexfXML.TAG_NAME + ">\n");
			outBuff.close();


			System.out.println("done!! :)");


		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private static void goToRootSavingNodes(HashMap<String, NodeXML> nodesToBeExported,
	                                        List<EdgeXML> edgesToBeExported,
	                                        GoTermNode currentNode,
	                                        RelationshipIndex isAGoRelIndex,
	                                        VizColorXML generalNodeColor) throws XMLElementException {

		NodeXML currentNodeXML = nodesToBeExported.get(currentNode.getId());

		if (currentNodeXML == null) {

			currentNodeXML = new NodeXML();
			currentNodeXML.setId(currentNode.getId());
			currentNodeXML.setLabel(currentNode.getName());
			currentNodeXML.setColor(new VizColorXML((Element) generalNodeColor.asJDomElement().clone()));

			AttValuesXML attValuesXML = new AttValuesXML();
			AttValueXML aspectAttValue = new AttValueXML();
			aspectAttValue.setFor(2);
			aspectAttValue.setValue(currentNode.getNamespace());
			attValuesXML.addAttValue(aspectAttValue);
			currentNodeXML.setAttvalues(attValuesXML);

			nodesToBeExported.put(currentNode.getId(), currentNodeXML);
		}

		IndexHits<Relationship> hits = isAGoRelIndex.get(IsAGoRel.IS_A_REL_INDEX, String.valueOf(currentNode.getNode().getId()));

		Iterator<Relationship> relIterator = hits.iterator();

		while (relIterator.hasNext()) {
			GoTermNode parentNode = new GoTermNode(relIterator.next().getEndNode());

			EdgeXML edge = new EdgeXML();
			edge.setId(String.valueOf(edgesIdCounter++));
			edge.setSource(currentNode.getId());
			edge.setTarget(parentNode.getId());
			edge.setType(EdgeXML.DIRECTED_TYPE);

			boolean found = false;
			for (EdgeXML tempEdge : edgesToBeExported) {
				if (tempEdge.getSource().equals(edge.getSource()) && tempEdge.getTarget().equals(edge.getTarget())) {
					found = true;
					break;
				}
			}
			if (!found) {
				edgesToBeExported.add(edge);
			}


			goToRootSavingNodes(nodesToBeExported, edgesToBeExported, parentNode, isAGoRelIndex, generalNodeColor);

		}


	}
}
