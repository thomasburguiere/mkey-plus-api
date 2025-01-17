package fr.lis.mkeyplusAPI.services;

import fr.lis.xper3API.IO.parser.SDDSaxParser;
import fr.lis.xper3API.model.CategoricalDescriptor;
import fr.lis.xper3API.model.Dataset;
import fr.lis.xper3API.model.DescriptionElementState;
import fr.lis.xper3API.model.Descriptor;
import fr.lis.xper3API.model.DescriptorNode;
import fr.lis.xper3API.model.DescriptorTree;
import fr.lis.xper3API.model.Item;
import fr.lis.xper3API.model.QuantitativeMeasure;
import fr.lis.xper3API.model.State;
import fr.lis.xper3API.utils.Utils;
import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.ResourceBundle;

import static fr.lis.mkeyplusAPI.services.InteractiveIdentificationService.*;


public class IdentificationTestSDDGenetta {
    public Logger logger = Logger.getRootLogger();

    private static Dataset datasetInSDD;
    private static List<Item> itemsInSDD;
    private static List<Descriptor> descriptorsInSDD;
    private static DescriptorTree dependencyTreeInSDD;
    private static List<DescriptorTree> descriptorTreesInSDD;
    private static final String sddUrlString = "https://www.dropbox.com/s/tr7kon3uc4b7tpq/genetta.sdd.xml?dl=1";
    private static DescriptionElementState[][] descriptionMatrixInSDD;
    private static DescriptorNode[] descriptorNodeMapInSDD;

    /**
     * initial method which parses the original SDD file
     *
     * @throws Exception
     */
    @BeforeClass
    public static void parseAndInitializeIdsAndSelectOrInitializeDependencyTree() throws Exception {

        // set test properties file
        Utils.setBundleConf(ResourceBundle.getBundle("confTest"));

        datasetInSDD = null;
        URLConnection urlConnection = null;

        // testing the sdd URL validity
        final URL sddFileUrl = new URL(sddUrlString);
        // open URL (HTTP query)
        urlConnection = sddFileUrl.openConnection();
        // Open data stream to test the connection
        urlConnection.getInputStream();

        // parsing the sdd to retrieve thevi dataset
        datasetInSDD = new fr.lis.xper3API.IO.parser.SDDSaxParser(sddFileUrl).getDataset();

        itemsInSDD = datasetInSDD.getItems();
        descriptorsInSDD = datasetInSDD.getDescriptors();
        descriptorTreesInSDD = datasetInSDD.getDescriptorTrees();

        // initialize IDs

        long descriptorCounter = 0;
        long stateCounter = 0;
        for (final Descriptor dInSdd : descriptorsInSDD) {
            dInSdd.setId(descriptorCounter);
            descriptorCounter++;
            if (dInSdd.isCategoricalType()) {
                for (final State s : ((CategoricalDescriptor) dInSdd).getStates()) {
                    s.setId(stateCounter);
                    stateCounter++;
                }
            }
        }

        long itemCounter = 0;
        long measureCounter = 0;
        long descriptionCounter = 0;
        long descriptionElementStateCounter = 0;
        for (final Item itemInSDD : itemsInSDD) {
            itemInSDD.setId(itemCounter);
            itemCounter++;
            itemInSDD.getDescription().setId(descriptionCounter);
            descriptionCounter++;
            for (final DescriptionElementState des : itemInSDD.getDescription().getDescriptionElements().values()) {
                des.setId(descriptionElementStateCounter);
                descriptionElementStateCounter++;
                if (des.getQuantitativeMeasure() != null) {
                    des.getQuantitativeMeasure().setId(measureCounter);
                    measureCounter++;
                }
            }
        }

        long descriptorNodeCounter = 0;
        for (final DescriptorTree tree : datasetInSDD.getDescriptorTrees()) {
            for (final DescriptorNode node : tree.getNodes()) {
                node.setId(descriptorNodeCounter);
                descriptorNodeCounter++;
            }
        }
        DescriptorTree depTree = null;
        // select or initialize the dependency tree
        dependencyTreeInSDD = null;
        if (descriptorTreesInSDD.size() > 0) {
            depTree = descriptorTreesInSDD.get(0);
            for (int i = 1; i < descriptorTreesInSDD.size(); i++) {
                final DescriptorTree tree = descriptorTreesInSDD.get(i);
                if (tree.getType().equalsIgnoreCase(DescriptorTree.DEPENDENCY_TYPE)) {
                    depTree = tree;
                }
            }
        } else {
            depTree = new DescriptorTree();
            depTree.setType(DescriptorTree.DEPENDENCY_TYPE);
            for (final Descriptor descriptor : datasetInSDD.getDescriptors()) {
                depTree.addNode(new DescriptorNode(descriptor));
            }
        }

        dependencyTreeInSDD = depTree;

        // initialize empty descriptions
        for (final Item itemInSDD : itemsInSDD) {
            for (final Descriptor descriptor : descriptorsInSDD) {
                if (itemInSDD.getDescriptionElement(descriptor.getId()) == null) {
                    final DescriptionElementState descriptionElementState = new DescriptionElementState();
                    if (descriptor.isQuantitativeType()) {
                        descriptionElementState.setQuantitativeMeasure(new QuantitativeMeasure());
                    }
                    itemInSDD.addDescriptionElement(descriptor, descriptionElementState);
                }
            }
        }

        // initialize descriptionMatrix
        final int nItems = itemsInSDD.size();
        final int nDescriptors = descriptorsInSDD.size();
        descriptionMatrixInSDD = new DescriptionElementState[nItems][nDescriptors];
        descriptorNodeMapInSDD = new DescriptorNode[nDescriptors];
        for (int itemIndex = 0; itemIndex < nItems; itemIndex++) {
            for (int descriptorIndex = 0; descriptorIndex < nDescriptors; descriptorIndex++) {
                descriptionMatrixInSDD[itemIndex][descriptorIndex] = itemsInSDD.get(itemIndex)
                        .getDescriptionElement(descriptorsInSDD.get(descriptorIndex).getId());
                final int currentDescriptorIndex = (int) descriptorsInSDD.get(descriptorIndex).getId();
                descriptorNodeMapInSDD[currentDescriptorIndex] = dependencyTreeInSDD
                        .getNodeContainingDescriptor(descriptorsInSDD.get(descriptorIndex).getId());
            }
        }

    }

    /**
     * @throws Exception
     */
    @Test
    public void testParse() throws Exception {

        URLConnection urlConnection = null;

        // testing the sdd URL validity
        final URL sddFileUrl = new URL(sddUrlString);
        // open URL (HTTP query)
        urlConnection = sddFileUrl.openConnection();
        // Open data stream to test the connection
        urlConnection.getInputStream();

        // parsing the sdd to retrieve the dataset
        datasetInSDD = new SDDSaxParser(sddFileUrl).getDataset();

    }

    /**
     * @throws Exception
     */
    @Test
    public void testInitializeIds() throws Exception {
        long descriptorCounter = 0;
        long stateCounter = 0;
        for (final Descriptor dInSdd : descriptorsInSDD) {
            dInSdd.setId(descriptorCounter);
            descriptorCounter++;
            if (dInSdd.isCategoricalType()) {
                for (final State s : ((CategoricalDescriptor) dInSdd).getStates()) {
                    s.setId(stateCounter);
                    stateCounter++;
                }
            }
        }

        long itemCounter = 0;
        long measureCounter = 0;
        long descriptionCounter = 0;
        long descriptionElementStateCounter = 0;
        for (final Item itemInSDD : itemsInSDD) {
            itemInSDD.setId(itemCounter);
            itemCounter++;
            itemInSDD.getDescription().setId(descriptionCounter);
            descriptionCounter++;
            for (final DescriptionElementState des : itemInSDD.getDescription().getDescriptionElements().values()) {
                des.setId(descriptionElementStateCounter);
                descriptionElementStateCounter++;
                if (des.getQuantitativeMeasure() != null) {
                    des.getQuantitativeMeasure().setId(measureCounter);
                    measureCounter++;
                }
            }
        }

        long descriptorNodeCounter = 0;
        for (final DescriptorTree tree : datasetInSDD.getDescriptorTrees()) {
            for (final DescriptorNode node : tree.getNodes()) {
                node.setId(descriptorNodeCounter);
                descriptorNodeCounter++;
            }
        }
    }

    /**
     * @throws Exception
     */
    @Test
    public void testInitializeEmptyDescriptions() throws Exception {
        for (final Item itemInSDD : itemsInSDD) {
            for (final Descriptor descriptor : descriptorsInSDD) {
                if (itemInSDD.getDescriptionElement(descriptor.getId()) == null) {
                    final DescriptionElementState descriptionElementState = new DescriptionElementState();
                    if (descriptor.isQuantitativeType()) {
                        descriptionElementState.setQuantitativeMeasure(new QuantitativeMeasure());
                    }
                    itemInSDD.addDescriptionElement(descriptor, descriptionElementState);
                }
            }
        }
    }

    /**
     * @throws Exception
     */
    @Test
    public void testSelectOrInitializeDependencyTree() throws Exception {
        dependencyTreeInSDD = null;
        if (descriptorTreesInSDD.size() > 0) {
            dependencyTreeInSDD = descriptorTreesInSDD.get(0);
            for (int i = 1; i < descriptorTreesInSDD.size(); i++) {
                final DescriptorTree tree = descriptorTreesInSDD.get(i);
                if (tree.getType().equalsIgnoreCase(DescriptorTree.DEPENDENCY_TYPE)) {
                    dependencyTreeInSDD = tree;
                }
            }
        } else {
            dependencyTreeInSDD = new DescriptorTree();
            dependencyTreeInSDD.setType(DescriptorTree.DEPENDENCY_TYPE);
            for (final Descriptor descriptor : datasetInSDD.getDescriptors()) {
                dependencyTreeInSDD.addNode(new DescriptorNode(descriptor));
            }
        }
    }

    /**
     * @throws Exception
     */
    // @Test
    // public void testGetAllDescriptorScores() throws Exception {
    // for (Descriptor descriptor : descriptorsInSDD) {
    // InteractiveIdentificationService.getDiscriminantPower(descriptor, itemsInSDD, 0,
    // InteractiveIdentificationService.Score.XPER, true, dependencyTreeInSDD);
    // }
    // }

    /**
     * @throws Exception
     */
    // @Test
    // public void testGetScoreMap() throws Exception {
    // Object map = InteractiveIdentificationService.getDescriptorsScoreMap(descriptorsInSDD, itemsInSDD,
    // dependencyTreeInSDD, InteractiveIdentificationService.Score.XPER, true);
    // logger.info("done");
    // }
    @Test
    public void testScore8Threads() throws Exception {
        getDescriptorsScoreMapUsingNThreads(descriptorsInSDD, itemsInSDD,
                dependencyTreeInSDD, Score.XPER, true, 8,
                descriptionMatrixInSDD, false);
        logger.info("done");
    }

    @Test
    public void testScore7Threads() throws Exception {
        getDescriptorsScoreMapUsingNThreads(descriptorsInSDD, itemsInSDD,
                dependencyTreeInSDD, Score.XPER, true, 7,
                descriptionMatrixInSDD, false);
        logger.info("done");
    }

    @Test
    public void testScore6Threads() throws Exception {
        getDescriptorsScoreMapUsingNThreads(descriptorsInSDD, itemsInSDD,
                dependencyTreeInSDD, Score.XPER, true, 6,
                descriptionMatrixInSDD, false);
    }

    @Test
    public void testScore5Threads() throws Exception {
        getDescriptorsScoreMapUsingNThreads(descriptorsInSDD, itemsInSDD,
                dependencyTreeInSDD, Score.XPER, true, 5,
                descriptionMatrixInSDD, false);
        logger.info("done");
    }

    @Test
    public void testScore4Threads() throws Exception {
        getDescriptorsScoreMapUsingNThreads(descriptorsInSDD, itemsInSDD,
                dependencyTreeInSDD, Score.XPER, true, 4,
                descriptionMatrixInSDD, false);
    }

    @Test
    public void testScore3Threads() throws Exception {
        getDescriptorsScoreMapUsingNThreads(descriptorsInSDD, itemsInSDD,
                dependencyTreeInSDD, Score.XPER, true, 3,
                descriptionMatrixInSDD, false);
        logger.info("done");
    }

    @Test
    public void testScore2Threads() throws Exception {
        getDescriptorsScoreMapUsingNThreads(descriptorsInSDD, itemsInSDD,
                dependencyTreeInSDD, Score.XPER, true, 2,
                descriptionMatrixInSDD, false);
        logger.info("done");
    }

    @Test
    public void testScore() throws Exception {
        final Chrono c = new Chrono();
        c.start();
        getDescriptorsScoreMapFuture(descriptorsInSDD, itemsInSDD,
                dependencyTreeInSDD, Score.XPER, true,
                descriptionMatrixInSDD, descriptorNodeMapInSDD, false);
        logger.info("done");
        c.stop();
        System.out.println(c.delayString());
    }

}
