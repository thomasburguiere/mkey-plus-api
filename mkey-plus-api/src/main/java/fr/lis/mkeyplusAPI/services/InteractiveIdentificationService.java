package fr.lis.mkeyplusAPI.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import fr.lis.xper3API.model.CategoricalDescriptor;
import fr.lis.xper3API.model.Description;
import fr.lis.xper3API.model.DescriptionElementState;
import fr.lis.xper3API.model.Descriptor;
import fr.lis.xper3API.model.DescriptorNode;
import fr.lis.xper3API.model.DescriptorTree;
import fr.lis.xper3API.model.Item;
import fr.lis.xper3API.model.QuantitativeDescriptor;
import fr.lis.xper3API.model.QuantitativeMeasure;
import fr.lis.xper3API.model.State;
import fr.lis.xper3API.utils.Utils;


/**
 * This class contains all the methods necessary to perform an Interactive Identification it is closely based
 * on the DiscriminatingPower class from Xper2
 * 
 * @author Thomas Burguiere
 */
public class InteractiveIdentificationService {

	public static final int SCORE_XPER = 0;
	public static final int SCORE_SOKAL_MICHENER = 1;
	public static final int SCORE_JACCARD = 2;

	public static final int LOGICAL_OPERATOR_AND = 3;
	public static final int LOGICAL_OPERATOR_OR = 4;

	public static final int COMPARISON_OPERATOR_GREATER_THAN = 5;
	public static final int COMPARISON_OPERATOR_STRICTLY_GREATER_THAN = 6;
	public static final int COMPARISON_OPERATOR_LOWER_THAN = 7;
	public static final int COMPARISON_OPERATOR_STRICTLY_LOWER_THAN = 8;
	public static final int COMPARISON_OPERATOR_CONTAINS = 9;
	public static final int COMPARISON_OPERATOR_DOES_NOT_CONTAIN = 10;

	// DISCRIMINANT POWER FUNCTIONS

	/**
	 * returns a {@link LinkedHashMap} containing in keys the descriptors, and in values their discriminant
	 * power. This map is sorted by the discriminant power of the descriptors, in descending order.
	 * Multi-Thread algorithm
	 * 
	 * @param descriptors
	 * @param items
	 * @param dbName
	 * @param login
	 * @param password
	 * @param scoreMethod
	 * @param considerChildScore
	 * @return
	 * @throws Exception
	 */
	public static LinkedHashMap<Descriptor, Float> getDescriptorsScoreMapFuture(List<Descriptor> descriptors,
			List<Item> items, DescriptorTree dependencyTree, int scoreMethod, boolean considerChildScores,
			DescriptionElementState[][] descriptionMatrix, DescriptorNode[] descriptorNodeMap,
			boolean withGlobalWeigth) {
		ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		LinkedHashMap<Descriptor, Float> descriptorsScoresMap = new LinkedHashMap<Descriptor, Float>();

		if (items.size() > 1) {
			@SuppressWarnings("unchecked")
			Future<Object[]>[] futures = new Future[descriptors.size()];
			int i = 0;
			for (Descriptor descriptor : descriptors) {
				futures[i] = exec.submit(new ThreadComputDescriptorsScoreMap(items, dependencyTree,
						scoreMethod, considerChildScores, descriptor, descriptionMatrix, descriptorNodeMap,
						withGlobalWeigth));
				i++;
			}
			try {
				for (Future<Object[]> fute : futures) {
					Object[] result = fute.get();
					descriptorsScoresMap.put((Descriptor) result[0], (Float) result[1]);
				}
				// InteractiveIdentificationService.exec.shutdown();

			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException ex) {
				ex.printStackTrace();
			} finally {
				exec.shutdown();
				exec = null;
			}
		} else {
			for (Descriptor descriptor : descriptors)
				descriptorsScoresMap.put(descriptor, new Float(-1));
		}

		return descriptorsScoresMap;
	}

	/**
	 * returns a {@link LinkedHashMap} containing in keys the descriptors, and in values their discriminant
	 * power. This map is sorted by the discriminant power of the descriptors, in descending order Mono-Thread
	 * algorithm
	 * 
	 * @param descriptors
	 * @param items
	 * @param dbName
	 * @param login
	 * @param password
	 * @param scoreMethod
	 * @param considerChildScore
	 * @return
	 * @throws Exception
	 */
	public static LinkedHashMap<Descriptor, Float> getDescriptorsScoreMap(List<Descriptor> descriptors,
			List<Item> items, DescriptorTree dependencyTree, int scoreMethod, boolean considerChildScores,
			DescriptionElementState[][] descriptionMatrix, DescriptorNode[] descriptorNodeMap,
			boolean withGlobalWeigth) {
		LinkedHashMap<Descriptor, Float> descriptorsScoresMap = new LinkedHashMap<Descriptor, Float>();

		if (items.size() > 1) {
			HashMap<Descriptor, Float> tempMap = new HashMap<Descriptor, Float>();
			float discriminantPower = -1;

			for (Descriptor descriptor : descriptors) {
				if (descriptor.isCategoricalType()
						&& ((CategoricalDescriptor) descriptor).getStates().size() <= 0)
					discriminantPower = 0;
				else {
					discriminantPower = getDiscriminantPower(descriptor, items, 0, scoreMethod,
							considerChildScores, dependencyTree, descriptionMatrix, descriptorNodeMap,
							withGlobalWeigth);
				}

				tempMap.put(descriptor, discriminantPower);
			}

			// sorting the final LinkedHashMap
			List<Float> mapValues = new ArrayList<Float>(tempMap.values());
			Collections.sort(mapValues, Collections.reverseOrder());

			for (Float dpScore : mapValues) {
				for (Descriptor desc : tempMap.keySet()) {
					float dp1 = tempMap.get(desc);
					float dp2 = dpScore;

					if (dp1 == dp2)
						descriptorsScoresMap.put(desc, dpScore);
				}
			}
		} else {
			for (Descriptor descriptor : descriptors)
				descriptorsScoresMap.put(descriptor, new Float(-1));
		}

		return descriptorsScoresMap;
	}

	@Deprecated
	private static class DescriptorScoreMapRunnable implements Runnable {
		private List<Descriptor> descriptorList;
		private List<Item> items;
		private int scoreMethod;
		private boolean considerChildScores;
		private DescriptorTree dependencyTree;
		private HashMap<Descriptor, Float> tempMap;
		private DescriptionElementState[][] descriptionMatrix;
		private boolean withGlobalWeight;

		public DescriptorScoreMapRunnable(List<Descriptor> descriptorList, List<Item> items, int scoreMethod,
				boolean considerChildScores, DescriptorTree dependencyTree,
				DescriptionElementState[][] descriptionMatrix, boolean withGlobalWeight) {
			this.descriptorList = descriptorList;
			this.items = items;
			this.scoreMethod = scoreMethod;
			this.considerChildScores = considerChildScores;
			this.dependencyTree = dependencyTree;
			this.tempMap = new HashMap<Descriptor, Float>();
			this.descriptionMatrix = descriptionMatrix;
			this.withGlobalWeight = withGlobalWeight;
		}

		@Override
		public void run() {
			float discriminantPower = -1;
			for (Descriptor descriptor : descriptorList) {
				if (descriptor.isCategoricalType()
						&& ((CategoricalDescriptor) descriptor).getStates().size() <= 0)
					discriminantPower = 0;
				else {
					discriminantPower = getDiscriminantPower(descriptor, items, 0, scoreMethod,
							considerChildScores, dependencyTree, descriptionMatrix, null, withGlobalWeight);
				}
				tempMap.put(descriptor, discriminantPower);
			}
		}

		public HashMap<Descriptor, Float> getTempMap() {
			return tempMap;
		}
	}

	/**
	 * 
	 * returns a {@link LinkedHashMap} containing in keys the descriptors, and in values their discriminant
	 * power. This map is sorted by the discriminant power of the descriptors, in descending order. The map is
	 * generated using 4 separate threads, each working on sublist of descriptors
	 * 
	 * @deprecated
	 * @param descriptors
	 * @param items
	 * @param dependencyTree
	 * @param scoreMethod
	 * @param considerChildScores
	 * @return
	 * @throws InterruptedException
	 */
	public static LinkedHashMap<Descriptor, Float> getDescriptorsScoreMapUsing4Threads(
			List<Descriptor> descriptors, List<Item> items, DescriptorTree dependencyTree, int scoreMethod,
			boolean considerChildScores, DescriptionElementState[][] descriptionMatrix)
			throws InterruptedException {
		LinkedHashMap<Descriptor, Float> descriptorsScoresMap = new LinkedHashMap<Descriptor, Float>();

		if (items.size() > 1) {

			int quarter = descriptors.size() / 4;
			List<Descriptor> descriptorList1 = descriptors.subList(0, quarter);
			List<Descriptor> descriptorList2 = descriptors.subList(quarter, quarter * 2);
			List<Descriptor> descriptorList3 = descriptors.subList(quarter * 2, quarter * 3);
			List<Descriptor> descriptorList4 = descriptors.subList(quarter * 3, descriptors.size());
			// descriptors.

			HashMap<Descriptor, Float> tempMap = new HashMap<Descriptor, Float>();

			HashMap<Descriptor, Float> tempMap1 = new HashMap<Descriptor, Float>();
			HashMap<Descriptor, Float> tempMap2 = new HashMap<Descriptor, Float>();
			HashMap<Descriptor, Float> tempMap3 = new HashMap<Descriptor, Float>();
			HashMap<Descriptor, Float> tempMap4 = new HashMap<Descriptor, Float>();

			DescriptorScoreMapRunnable r1 = new DescriptorScoreMapRunnable(descriptorList1, items,
					scoreMethod, considerChildScores, dependencyTree, descriptionMatrix, false);
			Thread t1 = new Thread(r1);

			DescriptorScoreMapRunnable r2 = new DescriptorScoreMapRunnable(descriptorList2, items,
					scoreMethod, considerChildScores, dependencyTree, descriptionMatrix, false);
			Thread t2 = new Thread(r2);

			DescriptorScoreMapRunnable r3 = new DescriptorScoreMapRunnable(descriptorList3, items,
					scoreMethod, considerChildScores, dependencyTree, descriptionMatrix, false);
			Thread t3 = new Thread(r3);

			DescriptorScoreMapRunnable r4 = new DescriptorScoreMapRunnable(descriptorList4, items,
					scoreMethod, considerChildScores, dependencyTree, descriptionMatrix, false);
			Thread t4 = new Thread(r4);
			t1.start();
			t2.start();
			t3.start();
			t4.start();
			t1.join();
			t2.join();
			t3.join();
			t4.join();
			tempMap1 = r1.getTempMap();
			tempMap2 = r2.getTempMap();
			tempMap3 = r3.getTempMap();
			tempMap4 = r4.getTempMap();
			tempMap.putAll(tempMap1);
			tempMap.putAll(tempMap2);
			tempMap.putAll(tempMap3);
			tempMap.putAll(tempMap4);

			// sorting the final LinkedHashMap
			List<Float> mapValues = new ArrayList<Float>(tempMap.values());
			Collections.sort(mapValues, Collections.reverseOrder());

			for (Float dpScore : mapValues) {
				for (Descriptor desc : tempMap.keySet()) {
					float dp1 = tempMap.get(desc);
					float dp2 = dpScore;

					if (dp1 == dp2)
						descriptorsScoresMap.put(desc, dpScore);
				}
			}

		}

		return descriptorsScoresMap;
	}

	@Deprecated
	public static LinkedHashMap<Descriptor, Float> getDescriptorsScoreMapUsingNThreads(
			List<Descriptor> descriptors, List<Item> items, DescriptorTree dependencyTree, int scoreMethod,
			boolean considerChildScores, int nThreads, DescriptionElementState[][] descriptionMatrix,
			boolean withGlobalWeigth) throws InterruptedException {
		LinkedHashMap<Descriptor, Float> descriptorsScoresMap = new LinkedHashMap<Descriptor, Float>();

		if (items.size() > 1) {

			int slicer = descriptors.size() / nThreads;

			List<List<Descriptor>> subLists = new ArrayList<List<Descriptor>>();

			int i = 0;
			for (i = 0; i < nThreads - 1; i++) {
				subLists.add(descriptors.subList(slicer * i, slicer * (i + 1)));
			}
			subLists.add(descriptors.subList(slicer * i, descriptors.size()));

			HashMap<Descriptor, Float> tempMap = new HashMap<Descriptor, Float>();

			DescriptorScoreMapRunnable[] runnables = new DescriptorScoreMapRunnable[nThreads];
			Thread[] threads = new Thread[nThreads];

			for (int j = 0; j < nThreads; j++) {
				runnables[j] = new DescriptorScoreMapRunnable(subLists.get(j), items, scoreMethod,
						considerChildScores, dependencyTree, descriptionMatrix, withGlobalWeigth);
				threads[j] = new Thread(runnables[j]);
			}

			for (int j = 0; j < nThreads; j++)
				threads[j].start();

			for (int j = 0; j < nThreads; j++)
				threads[j].join();

			for (int j = 0; j < nThreads; j++)
				tempMap.putAll(runnables[j].getTempMap());

			// sorting the final LinkedHashMap
			List<Float> mapValues = new ArrayList<Float>(tempMap.values());
			Collections.sort(mapValues, Collections.reverseOrder());

			for (Float dpScore : mapValues) {
				for (Descriptor desc : tempMap.keySet()) {
					float dp1 = tempMap.get(desc);
					float dp2 = dpScore;

					if (dp1 == dp2)
						descriptorsScoresMap.put(desc, dpScore);
				}
			}

		}

		return descriptorsScoresMap;
	}

	/**
	 * Basic function used to compute discriminant Power. This function is called recursively on the child
	 * descriptor.
	 * 
	 * @param descriptor
	 *            {@link Descriptor}, the {@link Descriptor} to evaluate.
	 * @param items
	 *            {@link List}, the ArrayList of {@link Item} used to evaluate this descriptor.
	 * @param value
	 *            {@link float}, the current score in the recursive call
	 * @param scoreMethod
	 *            {@link int}, the score methode to be use
	 * @param considerChildScores
	 * @param dependencyTree
	 * @param descriptionMatrix
	 * @param descriptorNodeMap
	 * @param withGlobalWeigth
	 * @return {@link float} the discriminant power of this descriptor
	 */
	public static float getDiscriminantPower(Descriptor descriptor, List<Item> items, float value,
			int scoreMethod, boolean considerChildScores, DescriptorTree dependencyTree,
			DescriptionElementState[][] descriptionMatrix, DescriptorNode[] descriptorNodeMap,
			boolean withGlobalWeigth) {
		float out = 0;
		int cpt = 0;

		if (descriptor.isQuantitativeType()) {
			for (int i1 = 0; i1 < items.size() - 1; i1++) {
				Item item1 = items.get(i1);
				for (int i2 = i1 + 1; i2 < items.size(); i2++) {
					Item item2 = items.get(i2);
					float tmp = -1;
					tmp = compareWithQuantitativeDescriptor((QuantitativeDescriptor) descriptor, item1,
							item2, scoreMethod, dependencyTree, descriptionMatrix, descriptorNodeMap);
					if (tmp >= 0) {
						out += tmp;
						cpt++;
					}
				}
			}
		} else if (descriptor.isCategoricalType()) {
			if (((CategoricalDescriptor) descriptor).getStates().size() <= 0) {
				out += 0;
				cpt++;
			} else {
				for (int i1 = 0; i1 < items.size() - 1; i1++) {
					Item item1 = items.get(i1);
					for (int i2 = i1 + 1; i2 < items.size(); i2++) {
						Item item2 = items.get(i2);
						float tmp = -1;
						tmp = compareWithCategoricalDescriptor((CategoricalDescriptor) descriptor, item1,
								item2, scoreMethod, dependencyTree, descriptionMatrix, descriptorNodeMap);
						if (tmp >= 0) {
							out += tmp;
							cpt++;
						}
					}
				}
			}
		}
		if (out != 0 && cpt != 0)
			// to normalize the number
			out = out / cpt;

		if (withGlobalWeigth) {
			if (out != 0) {
				out += descriptor.getGlobalWeight();
			} else {
				out = -1;
			}
		}

		// recursive DP calculation of child descriptors

		DescriptorNode node = null;
		if (descriptorNodeMap != null)
			node = descriptorNodeMap[(int) descriptor.getId()];
		else
			node = dependencyTree.getNodeContainingDescriptor(descriptor.getId(), false);

		if (considerChildScores && node != null) {
			for (DescriptorNode childNode : node.getChildNodes()) {
				Descriptor childDescriptor = childNode.getDescriptor(); // WILL NOT WORK WITH HIBERNATE (lazy
																		// instanciation exception)
				out = Math.max(
						value,
						getDiscriminantPower(childDescriptor, items, out, scoreMethod, true, dependencyTree,
								descriptionMatrix, descriptorNodeMap, withGlobalWeigth));
			}
		}
		return Math.max(out, value);

	}

	/**
	 * Compare two item based on a categorical descriptor, return a float between 0 (no match) and 1 (same
	 * value)
	 * 
	 * @param descriptor
	 * @param item1
	 * @param item2
	 * @param scoreMethod
	 * @param dependencyTree
	 * @param descriptionMatrix
	 * @param descriptorNodeMap
	 * @return
	 */
	private static float compareWithCategoricalDescriptor(CategoricalDescriptor descriptor, Item item1,
			Item item2, int scoreMethod, DescriptorTree dependencyTree,
			DescriptionElementState[][] descriptionMatrix, DescriptorNode[] descriptorNodeMap) {
		float out = 0;
		// boolean isAlwaysDescribed = true;

		// int all = v.getNbModes();
		float commonAbsent = 0; // nb of common points which are absent
		float commonPresent = 0; // nb of common points which are present
		float other = 0;

		DescriptorNode node;
		if (descriptorNodeMap == null)
			node = dependencyTree.getNodeContainingDescriptor(descriptor.getId(), false);
		else
			node = descriptorNodeMap[(int) descriptor.getId()];

		if ((isInapplicable(node, item1, descriptionMatrix) || isInapplicable(node, item2, descriptionMatrix)))
			return -1;

		DescriptionElementState des1 = null;
		DescriptionElementState des2 = null;
		if (descriptionMatrix == null) {
			des1 = item1.getDescriptionElement(descriptor.getId());
			des2 = item2.getDescriptionElement(descriptor.getId());
		} else {
			des1 = descriptionMatrix[(int) item1.getId()][(int) descriptor.getId()];
			des2 = descriptionMatrix[(int) item2.getId()][(int) descriptor.getId()];
		}
		List<State> statesList1 = des1.getStates();
		List<State> statesList2 = des2.getStates();
		List<State> everyStates = descriptor.getStates();

		// if at least one description is empty for the current
		// character
		// if ((statesList1 != null && statesList1.size() == 0)
		// || (statesList2 != null && statesList2.size() == 0)) {
		// isAlwaysDescribed = false;
		// }

		if (des1.isUnknown())
			statesList1 = everyStates;
		if (des2.isUnknown())
			statesList2 = everyStates;

		for (State state : everyStates) {
			if (statesList1.contains(state)) {
				if (statesList2.contains(state)) {
					commonPresent++;
				} else {
					other++;
				}
				// !(statesList2.contains(state))
			} else {
				if (statesList2.contains(state)) {
					other++;
				} else {
					commonAbsent++;
				}
			}
		}
		// // Sokal & Michener method
		if (scoreMethod == SCORE_SOKAL_MICHENER) {
			out = 1 - ((commonPresent + commonAbsent) / (commonPresent + commonAbsent + other));
			// round to 10^-3
			out = Utils.roundFloat(out, 3);
		}
		// // Jaccard Method
		else if (scoreMethod == SCORE_SOKAL_MICHENER) {
			try {
				// // case where description are empty
				out = 1 - (commonPresent / (commonPresent + other));
				// // round to 10^-3
				out = Utils.roundFloat(out, 3);
			} catch (ArithmeticException a) {
				out = 0;
			}
		}
		// // yes or no method (Xper)
		else {
			if ((commonPresent == 0) && (other > 0)) {
				out = 1;
			} else {
				out = 0;
			}
		}
		return out;
	}

	/**
	 * Compare two item based on a quantitative descriptor, return a float between 0 (no match) and 1 (same
	 * value)
	 * 
	 * @param descriptor
	 * @param item1
	 * @param item2
	 * @param scoreMethod
	 * @param dependencyTree
	 * @param descriptionMatrix
	 * @param descriptorNodeMap
	 * @return float
	 */
	private static float compareWithQuantitativeDescriptor(QuantitativeDescriptor descriptor, Item item1,
			Item item2, int scoreMethod, DescriptorTree dependencyTree,
			DescriptionElementState[][] descriptionMatrix, DescriptorNode[] descriptorNodeMap) {
		float out = 0;
		float commonPercentage = 0; // percentage of common values which are
		// shared

		DescriptorNode node = null;
		if (descriptorNodeMap == null)
			node = dependencyTree.getNodeContainingDescriptor(descriptor.getId(), false);
		else
			node = descriptorNodeMap[(int) descriptor.getId()];

		if ((isInapplicable(node, item1, descriptionMatrix) || isInapplicable(node, item2, descriptionMatrix)))
			return -1;

		DescriptionElementState des1 = null;
		DescriptionElementState des2 = null;

		if (descriptionMatrix == null) {
			des1 = item1.getDescription().getDescriptionElement(descriptor.getId());
			des2 = item2.getDescription().getDescriptionElement(descriptor.getId());
		} else {
			des1 = descriptionMatrix[(int) item1.getId()][(int) descriptor.getId()];
			des2 = descriptionMatrix[(int) item2.getId()][(int) descriptor.getId()];
		}
		QuantitativeMeasure quantitativeMeasure1 = des1.getQuantitativeMeasure();
		QuantitativeMeasure quantitativeMeasure2 = des2.getQuantitativeMeasure();

		if (quantitativeMeasure1 == null || quantitativeMeasure2 == null) {
			return 0;
		} else {
			if (quantitativeMeasure1.getCalculatedMinimum() == null
					|| quantitativeMeasure1.getCalculatedMaximum() == null
					|| quantitativeMeasure2.getCalculatedMinimum() == null
					|| quantitativeMeasure2.getCalculatedMaximum() == null) {
				return 0;
			} else {
				commonPercentage = calculateCommonPercentage(quantitativeMeasure1.getCalculatedMinimum()
						.doubleValue(), quantitativeMeasure1.getCalculatedMaximum().doubleValue(),
						quantitativeMeasure2.getCalculatedMinimum().doubleValue(), quantitativeMeasure2
								.getCalculatedMaximum().doubleValue());
			}

		}

		if (commonPercentage <= 0) {
			commonPercentage = 0;
		}

		switch (scoreMethod) {
		case SCORE_XPER:
			if ((commonPercentage <= 0)) {
				out = 1;
			} else {
				out = 0;
			}
			break;
		//
		case SCORE_SOKAL_MICHENER:
			out = 1 - (commonPercentage / 100);
			break;
		//
		case SCORE_JACCARD:
			out = 1 - (commonPercentage / 100);
			break;

		default:
			if ((commonPercentage <= 0)) {
				out = 1;
			} else {
				out = 0;
			}
			break;
		}

		return out;

	}

	private static boolean isInapplicable(DescriptorNode descriptorNode, Item item,
			DescriptionElementState[][] descriptionMatrix) {
		if (descriptorNode != null && descriptorNode.getParentNode() != null) {
			List<State> inapplicableStates = descriptorNode.getInapplicableStates();
			DescriptionElementState description = descriptionMatrix[(int) item.getId()][(int) descriptorNode
					.getParentNode().getDescriptor().getId()];
			int numberOfDescriptionStates = description.getStates().size();

			for (int i = 0; i < inapplicableStates.size(); i++) {
				State state = inapplicableStates.get(i);
				if (description.containsState(state.getId())) {
					numberOfDescriptionStates--;
				}

			}

			if (numberOfDescriptionStates == 0) {
				return true;
			}

			return isInapplicable(descriptorNode.getParentNode(), item, descriptionMatrix);

		}
		return false;
	}

	/**
	 * Return True if this descriptor Node has is parent already in the descriptors list, or if this
	 * descriptor node dont have parents.
	 * 
	 * @param descriptorNode
	 * @param description
	 * @param descriptors
	 * @return
	 */
	public static boolean getIsInaplicable(DescriptorNode descriptorNode, List<Descriptor> descriptors) {
		DescriptorNode descriptorNodeParent = descriptorNode.getParentNode();
		if (descriptorNodeParent == null) {
			return false;
		}
		return !descriptors.contains(descriptorNodeParent.getDescriptor());
	}

	/**
	 * @param min1
	 * @param max1
	 * @param min2
	 * @param max2
	 * @return the common percentage
	 */
	private static float calculateCommonPercentage(double min1, double max1, double min2, double max2) {
		double minLowerTmp = 0;
		double maxUpperTmp = 0;
		double minUpperTmp = 0;
		double maxLowerTmp = 0;
		float res = 0;

		if (min1 <= min2) {
			minLowerTmp = min1;
			minUpperTmp = min2;
		} else {
			minLowerTmp = min2;
			minUpperTmp = min1;
		}

		if (max1 >= max2) {
			maxUpperTmp = max1;
			maxLowerTmp = max2;
		} else {
			maxUpperTmp = max2;
			maxLowerTmp = max1;
		}

		res = new Double((maxLowerTmp - minUpperTmp) / (maxUpperTmp - minLowerTmp)).floatValue();

		if (res < 0) {
			res = 0;
		}
		return res;
	}

	// END OF DISCRIMINANT POWER CALCULATION FUNCTIONS

	// IDENTIFICATION FUNCTIONS
	/**
	 * This method receives a list of {@link Item}s, and a {@link Description}, which contains the description
	 * of one unitentified Item, according to one or several {@link Descriptor}s. It then loops over the Items
	 * passed in parameter, eliminates those who are not compatible with the description of the unidentified
	 * Item, and returns the list of the remaining Items compatible with the description of the unidentified
	 * Item
	 * 
	 * @param description
	 * @param remainingItems
	 * @return
	 */
	public static List<Item> getRemainingItems(Description description, List<Item> remainingItems) {
		List<Item> itemsToRemove = new ArrayList<Item>();
		for (Item item : remainingItems) {
			for (Descriptor descriptor : description.getDescriptionElements().keySet()) {
				if (!item.getDescription().getDescriptionElement(descriptor.getId()).isUnknown()) {

					if (descriptor.isCategoricalType()) {
						List<State> checkedStatesInSubmittedDescription = description.getDescriptionElement(
								descriptor.getId()).getStates();
						List<State> checkedStatesInKnowledgeBaseDescription = item.getDescription()
								.getDescriptionElement(descriptor.getId()).getStates();

						if (!matchDescriptionStates(checkedStatesInSubmittedDescription,
								checkedStatesInKnowledgeBaseDescription, LOGICAL_OPERATOR_OR))
							itemsToRemove.add(item);

					} else if (descriptor.isQuantitativeType()) {
						QuantitativeMeasure submittedMeasure = description.getDescriptionElement(
								descriptor.getId()).getQuantitativeMeasure();
						QuantitativeMeasure knowledgeBaseMeasure = item.getDescription()
								.getDescriptionElement(descriptor.getId()).getQuantitativeMeasure();

						if (!matchDescriptionsQuantitativeMeasures(submittedMeasure, knowledgeBaseMeasure,
								COMPARISON_OPERATOR_CONTAINS))
							itemsToRemove.add(item);
					}
				}
			}
		}
		remainingItems.removeAll(itemsToRemove);
		return remainingItems;
	}

	/**
	 * This method loops over the states checked in a submitted description (e.g. by a user), compares them
	 * with the states checked in a reference description (e.g. a knowledge base description) an returns true
	 * if the states from the first description are compatible with the reference description, using a
	 * specified logical operator
	 * 
	 * @param selectedStatesInSubmittedDescription
	 * @param checkedStatesInReferenceDescription
	 * @param logicalOperator
	 * @return
	 */
	private static boolean matchDescriptionStates(List<State> selectedStatesInSubmittedDescription,
			List<State> checkedStatesInReferenceDescription, int logicalOperator) {
		int commonValues = 0;

		for (State selectedStateInSubmittedDescription : selectedStatesInSubmittedDescription)
			for (State checkedStateInReferenceDescription : checkedStatesInReferenceDescription)
				if (checkedStateInReferenceDescription
						.hasSameNameAsState(selectedStateInSubmittedDescription))
					commonValues++;

		switch (logicalOperator) {
		case LOGICAL_OPERATOR_AND:
			if (checkedStatesInReferenceDescription.size() == commonValues)
				return true;
			return false;
		case LOGICAL_OPERATOR_OR:
			if (commonValues >= 1)
				return true;
			return false;

		default:
			return false;
		}
	}

	/**
	 * This methods compares the {@link QuantitativeMeasure} in a submitted description (e.g. by a user)
	 * 
	 * @param submittedMeasure
	 * @param referenceMeasure
	 * @param comparisonOperator
	 * @return
	 */
	private static boolean matchDescriptionsQuantitativeMeasures(QuantitativeMeasure submittedMeasure,
			QuantitativeMeasure referenceMeasure, int comparisonOperator) {
		switch (comparisonOperator) {
		case COMPARISON_OPERATOR_CONTAINS:

			if ((referenceMeasure.isNotFilled() || submittedMeasure.isNotFilled())
					&& referenceMeasure.getMean() != null && submittedMeasure.getMean() != null)
				return referenceMeasure.getMean().equals(submittedMeasure.getMean());
			else
				return referenceMeasure.contains(submittedMeasure);

		case COMPARISON_OPERATOR_GREATER_THAN:
			return referenceMeasure.isGreaterOrEqualTo(submittedMeasure, true);

		case COMPARISON_OPERATOR_LOWER_THAN:
			return referenceMeasure.isLowerOrEqualTo(submittedMeasure, true);

		default:
			return false;
		}

	}

	/**
	 * Naive integration of getSimilarityMap
	 * 
	 * @param description
	 * @param discardedItem
	 * @return LinkedHashMap, association between an item and the similarity score
	 */
	public static LinkedHashMap<Item, Float> getSimilarityMap(Description description,
			List<Item> discardedItem) {
		LinkedHashMap<Item, Float> descriptorsScoresMap = new LinkedHashMap<Item, Float>();
		// for each discardedItem
		for (Item item : discardedItem) {
			descriptorsScoresMap.put(item, computeSimilarity(description, item));
		}
		return descriptorsScoresMap;
	}

	/**
	 * Multi-thread integration of getSimilarityMap
	 * 
	 * @param description
	 * @param discardedItem
	 * @return LinkedHashMap, association between an item and the similarity score
	 */
	public static LinkedHashMap<Long, Float> getSimilarityMapFuture(Description description,
			List<Item> discardedItem) {
		LinkedHashMap<Long, Float> itemSimilarityMap = new LinkedHashMap<Long, Float>();
		ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		@SuppressWarnings("unchecked")
		Future<Object[]>[] futures = new Future[discardedItem.size()];
		int i = 0;

		// for each discardedItem
		for (Item item : discardedItem) {
			futures[i] = exec.submit(new ThreadComputeSimilarity(description, item));
			i++;
		}
		try {
			for (Future<Object[]> fute : futures) {
				Object[] result = fute.get();
				itemSimilarityMap.put((Long) result[0], (Float) result[1]);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException ex) {
			ex.printStackTrace();
		} finally {
			exec.shutdown();
			exec = null;
		}

		return itemSimilarityMap;
	}

	/**
	 * Compute a float between 0 and 1 that represents the similarity between this discardedItem and this
	 * description. 1 is for complete matching, and 0 for no match The way this function compute the
	 * similarity take for bases that the description is the reference.
	 * 
	 * @param description
	 * @param discardedItem
	 * @return float, between 0 (min) and 1 (max) the similarity between this description and this
	 *         discardedItem
	 */
	public static float computeSimilarity(Description description, Item discardedItem) {
		Map<Descriptor, DescriptionElementState> descriptionElements = description.getDescriptionElements();
		Map<Descriptor, DescriptionElementState> ItemdescriptionElements = discardedItem.getDescription()
				.getDescriptionElements();

		float commonValues = 0;
		float result = 0;

		// For each descriptor in this reference description
		for (Descriptor descriptor : descriptionElements.keySet()) {
			// Get the discardedItem and the reference description DescriptionElementState for this descriptor
			DescriptionElementState descriptionElement = descriptionElements.get(descriptor);
			DescriptionElementState ItemdescriptionElement = ItemdescriptionElements.get(descriptor);
			// CategoricalType
			if (descriptor.isCategoricalType()) {
				commonValues = 0;
				// For every State in the reference description
				for (State selectedStateReferenceDescription : descriptionElement.getStates()) {
					// For every State in the discardedItem
					for (State stateInTheDiscardedItem : ItemdescriptionElement.getStates()) {
						// If the two States have the same name, assume there are equals commomValues + 1
						if (selectedStateReferenceDescription.hasSameNameAsState(stateInTheDiscardedItem)) {
							commonValues++;
						}
					}
				}
				// if commonValues == max ( description.nS , discardedItem.nS ) => return 1
				// else if commonValues = 0 => return 0,
				// else return ]0,1[
				// Result = commonValues / maximum(description.numberOfState or discardedItem.numberOfState)
				result += commonValues
						/ ((float) Math.max(descriptionElement.getStates().size(), ItemdescriptionElement
								.getStates().size()));
			}
			// QuantitativeType
			else if (descriptor.isQuantitativeType()) {
				QuantitativeMeasure descriptionQm = descriptionElement.getQuantitativeMeasure();
				QuantitativeMeasure discardedItemQm = ItemdescriptionElement.getQuantitativeMeasure();

				// If the ref description quantitaive measure contains the discardedItem quantative measure,
				// return 1; ( no diff )
				result += descriptionQm.containsPercent(discardedItemQm);

			} else if (descriptor.isCalculatedType()) {
				// TODO add calculated Type
			}

		}

		result = result / ((float) descriptionElements.keySet().size());

		return result;
	}

}