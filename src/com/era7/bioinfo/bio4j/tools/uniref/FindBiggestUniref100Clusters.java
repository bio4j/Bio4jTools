/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.era7.bioinfo.bio4j.tools.uniref;

import com.era7.bioinfo.bio4jmodel.nodes.ProteinNode;
import com.era7.bioinfo.bio4jmodel.relationships.uniref.UniRef100MemberRel;
import com.era7.bioinfo.bio4jmodel.util.Bio4jManager;
import com.google.common.collect.Collections2;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

/**
 *
 * @author Pablo Pareja Tobes <ppareja@era7.com>
 */
public class FindBiggestUniref100Clusters {

   
    public static void main(String[] args) throws IOException {

        if (args.length != 3) {
            System.out.println("This program expects the following parameters:\n"
                    + "1. Bio4j DB folder\n"
                    + "2. Bio4j DB config parameters file (neo4j.properties)\n"
                    + "3. Output results file name (txt)\n");
        } else {

            File outFile = new File(args[2]);

            BufferedWriter outBuff = new BufferedWriter(new FileWriter(outFile));

            Bio4jManager manager = new Bio4jManager(args[0], args[1], true);

            List<ProteinCounter> list = new LinkedList<ProteinCounter>();

            int protCounter = 0;

            //--looping through all proteins--
            Iterator<Node> iterator = manager.getNodeTypeIndex().get(Bio4jManager.NODE_TYPE_INDEX_NAME, ProteinNode.NODE_TYPE).iterator();

            UniRef100MemberRel uniRef100MemberRel = new UniRef100MemberRel(null);

            while (iterator.hasNext()) {
                ProteinNode protein = new ProteinNode(iterator.next());
                if (protein.isUniref100Representant()) {
                    int clusterLength = 1;

                    Iterator<Relationship> relIterator = protein.getNode().getRelationships(Direction.OUTGOING, uniRef100MemberRel).iterator();
                    while (relIterator.hasNext()) {
                        relIterator.next();
                        clusterLength++;
                    }

                    list.add(new ProteinCounter(protein.getAccession(), clusterLength));
                }

                protCounter++;

                if (protCounter % 10000 == 0) {
                    System.out.println(protCounter + " proteins analyzed... ");
                }
            }

            manager.shutDown();

            System.out.println("Done!");
            
            System.out.println("Sorting values...");
            
            Collections.sort(list);
            
            System.out.println("Writing now the size of all clusters!");

            for (int i = 0; i < 10; i++) {
                ProteinCounter proteinCounter = list.get(i);
                outBuff.write(proteinCounter.protein + "\t" + proteinCounter.counter + "\n");
            }
            
            
            outBuff.close();
            
            System.out.println("Done ;)");
        }

    }
}

class ProteinCounter implements Comparable<ProteinCounter> {
    
    public String protein;
    public int counter;
    
    public ProteinCounter(String prot, int c){
        protein = prot;
        counter = c;
    }

    public int compareTo(ProteinCounter o) {
        if(o.counter > this.counter){
            return -1;
        }else if(o.counter < this.counter){
            return 1;
        }else{
            return 0;
        }
    }
}
