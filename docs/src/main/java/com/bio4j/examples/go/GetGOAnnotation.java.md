
This program finds the Gene Ontology annotations for the proteins provided in the input TSV file.
Annotations are exported in JSON format.
The program expects the following parameters:

1. Bio4j DB folder
2. Input TSV file including UniProt accessions _(one accession per line)_
3. Output JSON file including the GO annotation



```java
package com.bio4j.examples.go;

import com.bio4j.examples.json.model.go.GOTerm;
import com.bio4j.examples.json.model.go.GoSet;
import com.bio4j.model.go.vertices.GoTerm;
import com.bio4j.model.uniprot.vertices.Protein;
import com.bio4j.titan.model.go.TitanGoGraph;
import com.bio4j.titan.model.uniprot.TitanUniProtGraph;
import com.bio4j.titan.model.uniprot_go.TitanUniProtGoGraph;
import com.bio4j.titan.util.DefaultTitanGraph;
import com.era7.bioinfo.bioinfoutil.Executable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thinkaurelius.titan.core.TitanEdge;
import com.thinkaurelius.titan.core.TitanFactory;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.TitanVertex;
import com.thinkaurelius.titan.core.schema.EdgeLabelMaker;
import com.thinkaurelius.titan.core.schema.VertexLabelMaker;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GetGOAnnotation implements Executable{

	@Override
	public void execute(ArrayList<String> array) {
		String[] args = new String[array.size()];
		for (int i = 0; i < array.size(); i++) {
			args[i] = array.get(i);
		}
		main(args);
	}

	public static void main(String[] args){

		if (args.length != 4) {
			System.out.println("This program expects the following parameters:\n"
					+ "1. Bio4j DB folder\n"
					+ "2. Input TSV file including UniProt accessions (one accession per line)\n"
					+ "3. Output JSON file including the GO annotation \n"
					+ "4. Include intermediate terms (true/false)");
		} else {

			String dbFolder = args[0];
			String inputFileSt = args[1];
			String outputFileSt = args[2];
			boolean includeIntermediateTerms = Boolean.parseBoolean(args[3]);

			//----------DB configuration------------------
			Configuration conf = new BaseConfiguration();
			conf.setProperty("storage.directory", dbFolder);
			conf.setProperty("storage.backend", "local");
			conf.setProperty("autotype", "none");
			//-------creating graph handlers---------------------
			TitanGraph titanGraph = TitanFactory.open(conf);
			DefaultTitanGraph defGraph = new DefaultTitanGraph(titanGraph);

			System.out.println("Creating the graph managers....");

			//====================================================================================
			TitanUniProtGraph titanUniProtGraph = new TitanUniProtGraph(defGraph);
			TitanGoGraph titanGoGraph = new TitanGoGraph(defGraph);

			TitanUniProtGoGraph titanUniProtGoGraph = new TitanUniProtGoGraph(defGraph, titanUniProtGraph, titanGoGraph);

			titanGoGraph.withUniProtGoGraph(titanUniProtGoGraph);
			titanUniProtGraph.withUniProtGoGraph(titanUniProtGoGraph);
			//====================================================================================

			System.out.println("Done!");

			try {

				List<String> proteinAcessions = new LinkedList<>();
				Map<String, GOTerm> goTermMap = new HashMap<>();

				BufferedReader reader = new BufferedReader(new FileReader(new File(inputFileSt)));
				BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputFileSt)));

				System.out.println("Retrieving protein IDs...");
				String line;
				while((line = reader.readLine()) != null){
					proteinAcessions.add(line.trim());
				}
				reader.close();
				System.out.println("Done!");

				System.out.println("Finding GO annotations....");

				for (String accession : proteinAcessions){
					Optional<Protein<DefaultTitanGraph, TitanVertex, VertexLabelMaker, TitanEdge, EdgeLabelMaker>> optionalProtein = titanUniProtGraph.proteinAccessionIndex().getVertex(accession);
					if(optionalProtein.isPresent()){
						Optional<Stream<GoTerm<DefaultTitanGraph, TitanVertex, VertexLabelMaker, TitanEdge, EdgeLabelMaker>>> goTermStreamOptional = optionalProtein.get().goAnnotation_outV();
						if(goTermStreamOptional.isPresent()){
							List<GoTerm<DefaultTitanGraph, TitanVertex, VertexLabelMaker, TitanEdge, EdgeLabelMaker>> goTermList = goTermStreamOptional.get().collect(Collectors.toList());
							for (GoTerm<DefaultTitanGraph, TitanVertex, VertexLabelMaker, TitanEdge, EdgeLabelMaker> goTerm : goTermList){
								GOTerm goJson = goTermMap.get(goTerm.id());
								if(goJson == null){
									goJson = new GOTerm(goTerm.id(), goTerm.name());
									goJson.setTermCount(0);
									//----Finding parent IDs------------
									goJson.setParentIds(new LinkedList<String>());
									Optional<Stream<GoTerm<DefaultTitanGraph, TitanVertex, VertexLabelMaker, TitanEdge, EdgeLabelMaker>>> parentsStreamOptional = goTerm.isA_outV();
									if(parentsStreamOptional.isPresent()){
										List<GoTerm<DefaultTitanGraph, TitanVertex, VertexLabelMaker, TitanEdge, EdgeLabelMaker>> parentTerms = parentsStreamOptional.get().collect((Collectors.toList()));
										List<String> parentIds = goJson.getParentIds();
										for (GoTerm<DefaultTitanGraph, TitanVertex, VertexLabelMaker, TitanEdge, EdgeLabelMaker> tempTerm : parentTerms){
											parentIds.add(tempTerm.id());
										}
									}
									//----------------------------------
								}
								goJson.setTermCount(goJson.getTermCount() + 1);
							}
						}

					}
				}
				System.out.println("Done!");

				Set<GOTerm> goTermSet = new HashSet<>();
				for(String goId : goTermMap.keySet()){
					goTermSet.add(goTermMap.get(goId));
				}

				if(includeIntermediateTerms){
					Set<String> termsToBeAdded = new HashSet<>();
					for(String goId : goTermMap.keySet()){
						Set<String> tempSetToBeAdded = new HashSet<>();
						GoTerm<DefaultTitanGraph, TitanVertex, VertexLabelMaker, TitanEdge, EdgeLabelMaker> currentTerm = titanGoGraph.goTermIdIndex().getVertex(goId).get();
						updateSetOfTermsToBeAdded(goTermMap, termsToBeAdded, currentTerm, titanGoGraph);
					}

				}

				GoSet goSet = new GoSet(goTermSet);


				System.out.println("Writing output file....");
				Gson gson = new GsonBuilder().setPrettyPrinting().create();
				writer.write(gson.toJson(goSet));
				System.out.println("Closing output file...");
				writer.close();

			} catch (IOException e) {
				e.printStackTrace();
			}


			System.out.println("Closing the database...");
			titanGraph.shutdown();
			System.out.println("Done ;)");
		}

	}

	private static void updateSetOfTermsToBeAdded(Map<String, GOTerm>  existingTermsMap,
	                                              Set<String> termsToBeAdded,
	                                              GoTerm<DefaultTitanGraph, TitanVertex, VertexLabelMaker, TitanEdge, EdgeLabelMaker> currentTerm,
	                                              TitanGoGraph titanGoGraph ){

		Optional<Stream<GoTerm<DefaultTitanGraph, TitanVertex, VertexLabelMaker, TitanEdge, EdgeLabelMaker>>> parentsOptionalStream = currentTerm.isA_outV();
		if(parentsOptionalStream.isPresent()){
			List<GoTerm<DefaultTitanGraph, TitanVertex, VertexLabelMaker, TitanEdge, EdgeLabelMaker>> parents = parentsOptionalStream.get().collect(Collectors.toList());
			for (GoTerm<DefaultTitanGraph, TitanVertex, VertexLabelMaker, TitanEdge, EdgeLabelMaker> parent : parents){
				if(existingTermsMap.containsKey(parent.id())){
					//====================================================================================================
					//if we already reached another term that was included in the initial set, it could mean that there are a set of intermediate terms
					//that should be added to the main set
					for(String termToBeAdded : termsToBeAdded){
						Optional<GoTerm<DefaultTitanGraph, TitanVertex, VertexLabelMaker, TitanEdge, EdgeLabelMaker>> optionalTerm = titanGoGraph.goTermIdIndex().getVertex(termToBeAdded);
						if(optionalTerm.isPresent()){
							GoTerm<DefaultTitanGraph, TitanVertex, VertexLabelMaker, TitanEdge, EdgeLabelMaker> goTerm = optionalTerm.get();
							GOTerm goJson = new GOTerm(goTerm.id(), goTerm.name());
							goJson.setTermCount(0);
							goJson.setComment(goTerm.comment());
							goJson.setSynonym(goTerm.synonym());
							goJson.setDefinition(goTerm.definition());
							//----Finding parent IDs------------
							goJson.setParentIds(new LinkedList<String>());
							Optional<Stream<GoTerm<DefaultTitanGraph, TitanVertex, VertexLabelMaker, TitanEdge, EdgeLabelMaker>>> parentsStreamOptional = goTerm.isA_outV();
							if(parentsStreamOptional.isPresent()){
								List<GoTerm<DefaultTitanGraph, TitanVertex, VertexLabelMaker, TitanEdge, EdgeLabelMaker>> parentTerms = parentsStreamOptional.get().collect((Collectors.toList()));
								List<String> parentIds = goJson.getParentIds();
								for (GoTerm<DefaultTitanGraph, TitanVertex, VertexLabelMaker, TitanEdge, EdgeLabelMaker> tempTerm : parentTerms){
									parentIds.add(tempTerm.id());
								}
							}
							//----------------------------------
							existingTermsMap.put(goJson.getId(), goJson);
						}
					}
					//====================================================================================================
				}else{
					termsToBeAdded.add(parent.id());
					updateSetOfTermsToBeAdded(existingTermsMap, termsToBeAdded, parent,titanGoGraph);
				}
			}
		}

	}
}

```


------

### Index

+ src
  + main
    + java
      + com
        + bio4j
          + examples
            + [BasicProteinManipulation.java][main\java\com\bio4j\examples\BasicProteinManipulation.java]
            + go
              + [ExportGOJSONToCSV.java][main\java\com\bio4j\examples\go\ExportGOJSONToCSV.java]
              + [GetCumulativeFrequenciesForGoSet.java][main\java\com\bio4j\examples\go\GetCumulativeFrequenciesForGoSet.java]
              + [GetGOAnnotation.java][main\java\com\bio4j\examples\go\GetGOAnnotation.java]
              + [TransformGOJSONtoHierarchicalJSON.java][main\java\com\bio4j\examples\go\TransformGOJSONtoHierarchicalJSON.java]
            + json
              + model
                + go
                  + [GoSet.java][main\java\com\bio4j\examples\json\model\go\GoSet.java]
                  + [GOTerm.java][main\java\com\bio4j\examples\json\model\go\GOTerm.java]
            + ncbi_taxonomy
              + [TaxonomyAlgo.java][main\java\com\bio4j\examples\ncbi_taxonomy\TaxonomyAlgo.java]
            + uniref
              + [FindLCAOfUniRefCluster.java][main\java\com\bio4j\examples\uniref\FindLCAOfUniRefCluster.java]

[main\java\com\bio4j\examples\BasicProteinManipulation.java]: ..\BasicProteinManipulation.java.md
[main\java\com\bio4j\examples\go\ExportGOJSONToCSV.java]: ExportGOJSONToCSV.java.md
[main\java\com\bio4j\examples\go\GetCumulativeFrequenciesForGoSet.java]: GetCumulativeFrequenciesForGoSet.java.md
[main\java\com\bio4j\examples\go\GetGOAnnotation.java]: GetGOAnnotation.java.md
[main\java\com\bio4j\examples\go\TransformGOJSONtoHierarchicalJSON.java]: TransformGOJSONtoHierarchicalJSON.java.md
[main\java\com\bio4j\examples\json\model\go\GoSet.java]: ..\json\model\go\GoSet.java.md
[main\java\com\bio4j\examples\json\model\go\GOTerm.java]: ..\json\model\go\GOTerm.java.md
[main\java\com\bio4j\examples\ncbi_taxonomy\TaxonomyAlgo.java]: ..\ncbi_taxonomy\TaxonomyAlgo.java.md
[main\java\com\bio4j\examples\uniref\FindLCAOfUniRefCluster.java]: ..\uniref\FindLCAOfUniRefCluster.java.md