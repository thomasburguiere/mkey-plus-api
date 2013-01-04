package fr.lis.mkeyplusAPI.services;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.ResourceBundle;

import model.CategoricalDescriptor;
import model.Dataset;
import model.Description;
import model.DescriptionElementState;
import model.Descriptor;
import model.DescriptorNode;
import model.DescriptorTree;
import model.Item;
import model.QuantitativeMeasure;
import model.State;

import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

import fr.lis.mkeyplusAPI.io.parser.SDDSaxParser;

import utils.Utils;

public class IdentificationTestSDDCichorieae {
	public Logger logger = Logger.getRootLogger();

	private static Dataset datasetInSDD;
	private static List<Item> itemsInSDD;
	private static List<Descriptor> descriptorsInSDD;
	private static DescriptorTree dependencyTreeInSDD;
	private static List<DescriptorTree> descriptorTreesInSDD;
	private static String sddUrlString = "http://localhost:8080/miscFiles/Cichorieae-fullSDD.xml";

	/**
	 * initial method which parses the original SDD file
	 * 
	 * @throws Exception
	 */
	@BeforeClass
	public static void parse() throws Exception {

		// set test properties file
		Utils.setBundleConf(ResourceBundle.getBundle("confTest"));

		datasetInSDD = null;
		URLConnection urlConnection = null;
		InputStream httpStream = null;

		// testing the sdd URL validity
		URL sddFileUrl = new URL(sddUrlString);
		// open URL (HTTP query)
		urlConnection = sddFileUrl.openConnection();
		// Open data stream to test the connection
		httpStream = urlConnection.getInputStream();

		// parsing the sdd to retrieve the dataset
		datasetInSDD = new SDDSaxParser(sddFileUrl).getDataset();

		itemsInSDD = datasetInSDD.getItems();
		descriptorsInSDD = datasetInSDD.getDescriptors();
		descriptorTreesInSDD = datasetInSDD.getDescriptorTrees();

	}

	/**
	 * @throws Exception
	 */
	public void testParse() throws Exception {

		URLConnection urlConnection = null;
		InputStream httpStream = null;

		// testing the sdd URL validity
		URL sddFileUrl = new URL(sddUrlString);
		// open URL (HTTP query)
		urlConnection = sddFileUrl.openConnection();
		// Open data stream to test the connection
		httpStream = urlConnection.getInputStream();

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
		for (Descriptor dInSdd : descriptorsInSDD) {
			dInSdd.setId(descriptorCounter);
			descriptorCounter++;
			if (dInSdd.isCategoricalType()) {
				for (State s : ((CategoricalDescriptor) dInSdd).getStates()) {
					s.setId(stateCounter);
					stateCounter++;
				}
			}
		}

		long itemCounter = 0;
		long measureCounter = 0;
		long descriptionCounter = 0;
		long descriptionElementStateCounter = 0;
		for (Item itemInSDD : itemsInSDD) {
			itemInSDD.setId(itemCounter);
			itemCounter++;
			itemInSDD.getDescription().setId(descriptionCounter);
			descriptionCounter++;
			for (DescriptionElementState des : itemInSDD.getDescription().getDescriptionElements().values()) {
				des.setId(descriptionElementStateCounter);
				descriptionElementStateCounter++;
				if (des.getQuantitativeMeasure() != null) {
					des.getQuantitativeMeasure().setId(measureCounter);
					measureCounter++;
				}
			}
		}

		long descriptorNodeCounter = 0;
		for (DescriptorTree tree : datasetInSDD.getDescriptorTrees())
			for (DescriptorNode node : tree.getNodes()) {
				node.setId(descriptorNodeCounter);
				descriptorNodeCounter++;
			}
	}

	/**
	 * @throws Exception
	 */
	@Test
	public void testInitializeEmptyDescriptions() throws Exception {
		for (Item itemInSDD : itemsInSDD) {
			for (Descriptor descriptor : descriptorsInSDD) {
				if (itemInSDD.getDescriptionElement(descriptor.getId()) == null) {
					DescriptionElementState descriptionElementState = new DescriptionElementState();
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
				DescriptorTree tree = descriptorTreesInSDD.get(i);
				if (tree.getType().equalsIgnoreCase(DescriptorTree.DEPENDENCY_TYPE))
					dependencyTreeInSDD = tree;
			}
		} else {
			dependencyTreeInSDD = new DescriptorTree();
			dependencyTreeInSDD.setType(DescriptorTree.DEPENDENCY_TYPE);
			for (Descriptor descriptor : datasetInSDD.getDescriptors())
				dependencyTreeInSDD.addNode(new DescriptorNode(descriptor));
		}
	}

	/**
	 * @throws Exception
	 */
	// @Test
	// public void testGetAllDescriptorScores() throws Exception {
	// for (Descriptor descriptor : descriptorsInSDD) {
	// InteractiveIdentificationService.getDiscriminantPower(descriptor, itemsInSDD, 0,
	// InteractiveIdentificationService.SCORE_XPER, true, dependencyTreeInSDD);
	// }
	// }

	/**
	 * @throws Exception
	 */
	// @Test
	// public void testGetScoreMap() throws Exception {
	// Object map = InteractiveIdentificationService.getDescriptorsScoreMap(descriptorsInSDD, itemsInSDD,
	// dependencyTreeInSDD, InteractiveIdentificationService.SCORE_XPER, true);
	// logger.info("done");
	// }
	@Test
	public void testScore8Threads() throws Exception {
		Object scoremap = InteractiveIdentificationService.getDescriptorsScoreMapUsingNThreads(
				descriptorsInSDD, itemsInSDD, dependencyTreeInSDD,
				InteractiveIdentificationService.SCORE_XPER, true, 8);
	}
	@Test
	public void testScore7Threads() throws Exception {
		Object scoremap = InteractiveIdentificationService.getDescriptorsScoreMapUsingNThreads(
				descriptorsInSDD, itemsInSDD, dependencyTreeInSDD,
				InteractiveIdentificationService.SCORE_XPER, true, 7);
	}

	@Test
	public void testScore6Threads() throws Exception {
		Object scoremap = InteractiveIdentificationService.getDescriptorsScoreMapUsingNThreads(
				descriptorsInSDD, itemsInSDD, dependencyTreeInSDD,
				InteractiveIdentificationService.SCORE_XPER, true, 6);
	}

	@Test
	public void testScore5Threads() throws Exception {
		Object scoremap = InteractiveIdentificationService.getDescriptorsScoreMapUsingNThreads(
				descriptorsInSDD, itemsInSDD, dependencyTreeInSDD,
				InteractiveIdentificationService.SCORE_XPER, true, 5);
	}

	@Test
	public void testScore4Threads() throws Exception {
		Object scoremap = InteractiveIdentificationService.getDescriptorsScoreMapUsingNThreads(
				descriptorsInSDD, itemsInSDD, dependencyTreeInSDD,
				InteractiveIdentificationService.SCORE_XPER, true, 4);
	}

	@Test
	public void testScore3Threads() throws Exception {
		Object scoremap = InteractiveIdentificationService.getDescriptorsScoreMapUsingNThreads(
				descriptorsInSDD, itemsInSDD, dependencyTreeInSDD,
				InteractiveIdentificationService.SCORE_XPER, true, 3);
	}

	@Test
	public void testScore2Threads() throws Exception {
		Object scoremap = InteractiveIdentificationService.getDescriptorsScoreMapUsingNThreads(
				descriptorsInSDD, itemsInSDD, dependencyTreeInSDD,
				InteractiveIdentificationService.SCORE_XPER, true, 2);
	}
	@Test
	public void testScore1Threads() throws Exception {
		Object scoremap = InteractiveIdentificationService.getDescriptorsScoreMapUsingNThreads(
				descriptorsInSDD, itemsInSDD, dependencyTreeInSDD,
				InteractiveIdentificationService.SCORE_XPER, true, 1);
	}

	@Test
	public void testIdentificationIteration1() throws Exception {
		Object scoremap = InteractiveIdentificationService.getDescriptorsScoreMapUsingNThreads(
				descriptorsInSDD, itemsInSDD, dependencyTreeInSDD,
				InteractiveIdentificationService.SCORE_XPER, true, 4);

		Descriptor d = null;
		State s = new State("glabrous");
		for (Descriptor desc : descriptorsInSDD) {
			if (desc.getName().toLowerCase().indexOf("rosette leaves <indumentum>") != -1)
				d = desc;
		}

		Description description = new Description();
		DescriptionElementState des = new DescriptionElementState();
		des.addState(s);
		description.addDescriptionElement(d, des);

		itemsInSDD = InteractiveIdentificationService.getRemainingItems(description, itemsInSDD);

		descriptorsInSDD.remove(d);
		logger.info("done");
	}

	@Test
	public void testIdentificationIteration2() throws Exception {
		Object scoremap = InteractiveIdentificationService.getDescriptorsScoreMapUsing4Threads(
				descriptorsInSDD, itemsInSDD, dependencyTreeInSDD,
				InteractiveIdentificationService.SCORE_XPER, true);

		Descriptor d = null;
		State s = new State("auriculate");
		for (Descriptor desc : descriptorsInSDD) {
			if (desc.getName().toLowerCase().equals("cauline leaves <base>"))
				d = desc;
		}

		Description description = new Description();
		DescriptionElementState des = new DescriptionElementState();
		des.addState(s);
		description.addDescriptionElement(d, des);

		itemsInSDD = InteractiveIdentificationService.getRemainingItems(description, itemsInSDD);

		descriptorsInSDD.remove(d);
		logger.info("done");
	}

	@Test
	public void testIdentificationIteration3() throws Exception {
		Object scoremap = InteractiveIdentificationService.getDescriptorsScoreMapUsing4Threads(
				descriptorsInSDD, itemsInSDD, dependencyTreeInSDD,
				InteractiveIdentificationService.SCORE_XPER, true);

		Descriptor d = null;
		State s = new State("terete");
		for (Descriptor desc : descriptorsInSDD) {
			if (desc.getName().toLowerCase().equals("flowering stems <section>"))
				d = desc;
		}

		Description description = new Description();
		DescriptionElementState des = new DescriptionElementState();
		des.addState(s);
		description.addDescriptionElement(d, des);

		itemsInSDD = InteractiveIdentificationService.getRemainingItems(description, itemsInSDD);

		descriptorsInSDD.remove(d);
		logger.info("done");
	}
}
