package fr.lis.mkeyplusAPI.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import model.CategoricalDescriptor;
import model.Description;
import model.DescriptionElementState;
import model.Descriptor;
import model.DescriptorNode;
import model.DescriptorTree;
import model.Item;
import model.QuantitativeDescriptor;
import model.QuantitativeMeasure;
import model.State;
import services.DescriptorManagementService;
import services.DescriptorTreeManagementService;
import services.ItemManagementService;
import utils.Utils;

/**
 * This class contains all the methods necessary to perform an Interactive Identification
 * 
 * @author Thomas Burguiere
 * 
 */
@Deprecated
public class InteractiveIdentificationService_old {

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

	/**
	 * returns a {@link LinkedHashMap} containing in keys the descriptors, and in values their discriminant
	 * power. This map is sorted by the discriminant power of the descriptors, in descending order
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
			List<Item> items, DescriptorTree dependencyTree, int scoreMethod, boolean considerChildScore) {
		LinkedHashMap<Descriptor, Float> descriptorsScoresMap = new LinkedHashMap<Descriptor, Float>();

		if (items.size() > 1) {
			HashMap<Descriptor, Float> tempMap = new HashMap<Descriptor, Float>();
			float discriminantPower = -1;
			for (Descriptor descriptor : descriptors) {
				if (!descriptor.isCalculatedType()) {
					if (descriptor.isCategoricalType()
							&& ((CategoricalDescriptor) descriptor).getStates().size() <= 0)
						discriminantPower = 0;
					else {
						if (descriptor.isCategoricalType()) {
							discriminantPower = categoricalDescriptorScore(
									(CategoricalDescriptor) descriptor, items, dependencyTree, 0);
						} else if (descriptor.isQuantitativeType())
							discriminantPower = quantitativeDescriptorScore(
									(QuantitativeDescriptor) descriptor, items, dependencyTree, scoreMethod);

						if (considerChildScore) {
							// asserting the discriminant power of the child
							// descriptors (if any) and setting
							// the
							// discriminant power of a child node to its father,
							// if it is greater
							discriminantPower = considerChildNodeDiscriminantPower(descriptors, items,
									scoreMethod, dependencyTree, discriminantPower, descriptor);
						}
						tempMap.put(descriptor, new Float(discriminantPower));
					}
				}
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

	/**
	 * @param dbName
	 * @param login
	 * @param password
	 * @return
	 * @throws Exception
	 */
	public static Map<String, Object> getPersistedDescriptiveData(String dbName, String login, String password)
			throws Exception {
		HashMap<String, Object> descriptiveData = new LinkedHashMap<String, Object>();

		List<Item> itemsInKB = ItemManagementService.readAll(true, true, dbName, login, password);
		descriptiveData.put("itemList", itemsInKB);

		List<Descriptor> descriptorsInKb = DescriptorManagementService.readAll(true, dbName, login, password);
		descriptiveData.put("descriptorList", descriptorsInKb);

		DescriptorTree dependencyTree = DescriptorTreeManagementService.read(DescriptorTree.DEPENDENCY_TYPE,
				true, dbName, login, password);
		descriptiveData.put("dependencyTree", dependencyTree);

		descriptiveData.put("descriptorsScoreMap",
				getDescriptorsScoreMap(descriptorsInKb, itemsInKB, dependencyTree, SCORE_XPER, true));

		return descriptiveData;
	}

	/**
	 * @param descriptors
	 * @param items
	 * @param scoreMethod
	 * @param dependencyTree
	 * @param discriminantPower
	 * @param descriptor
	 * @param tempDiscriminantPower
	 * @return
	 * @throws Exception
	 */
	private static float considerChildNodeDiscriminantPower(List<Descriptor> descriptors, List<Item> items,
			int scoreMethod, DescriptorTree dependencyTree, float discriminantPower, Descriptor descriptor) {

		float tempDiscriminantPower = 0;
		for (DescriptorNode childNode : dependencyTree.getNodeContainingDescriptor(descriptor.getId())
				.getChildNodes()) {
			Descriptor childDescriptorInList = null;
			long childDescriptorId = childNode.getDescriptor().getId();
			for (Descriptor temp : descriptors)
				if (childDescriptorId == temp.getId())
					childDescriptorInList = temp;

			if (childDescriptorInList.isCategoricalType()) {
				tempDiscriminantPower = categoricalDescriptorScore(
						(CategoricalDescriptor) childDescriptorInList, items, dependencyTree, scoreMethod);
			} else if (childDescriptorInList.isQuantitativeType()) {
				tempDiscriminantPower = quantitativeDescriptorScore(
						(QuantitativeDescriptor) childDescriptorInList, items, dependencyTree, scoreMethod);
			}
			if (tempDiscriminantPower > discriminantPower)
				discriminantPower = tempDiscriminantPower;
		}
		return discriminantPower;
	}

	/**
	 * This method receives a list of Items, and a Description, which contains the description of one
	 * unitentified Item, according to one or several descriptors. It then loops over the Items passed in
	 * parameter, eliminates those who are not compatible with the description of the unidentified Item, and
	 * returns the list of the remaining Items compatible with the description of the unidentified Item
	 * 
	 * @param description
	 * @param remainingItems
	 * @param dbName
	 * @param login
	 * @param password
	 * @return
	 */
	public static List<Item> getRemainingItems(Description description, List<Item> remainingItems,
			String dbName, String login, String password) {
		List<Item> itemsToRemove = new ArrayList<Item>();
		for (Item item : remainingItems) {
			for (Descriptor descriptor : description.getDescriptionElements().keySet()) {
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
		remainingItems.removeAll(itemsToRemove);
		return remainingItems;
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
			return referenceMeasure.contains(submittedMeasure);

		default:
			return false;
		}

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
	 * @param descriptor
	 * @param remainingItems
	 * @param dependencyTree
	 * @param scoreMethod
	 * @return
	 * @throws Exception
	 */
	public static float categoricalDescriptorScore(CategoricalDescriptor descriptor,
			List<Item> remainingItems, DescriptorTree dependencyTree, int scoreMethod) {
		int cpt = 0;
		float score = 0;
		boolean isAlwaysDescribed = true;
		DescriptorNode node = dependencyTree.getNodeContainingDescriptor(descriptor.getId());

		for (int i = 0; i < remainingItems.size() - 1; i++) {
			for (int j = i + 1; j < remainingItems.size(); j++) {

				if (remainingItems.get(i).getDescription() != null
						&& remainingItems.get(j).getDescription() != null) {

					// if the descriptor is applicable for both of these items
					if ((!isInapplicable(node, remainingItems.get(i)) && !isInapplicable(node,
							remainingItems.get(j)))) {

						List<State> statesList1 = remainingItems.get(i)
								.getDescriptionElement(descriptor.getId()).getStates();
						List<State> statesList2 = remainingItems.get(j)
								.getDescriptionElement(descriptor.getId()).getStates();

						// if at least one description is empty for the current
						// character
						if ((statesList1 != null && statesList1.size() == 0)
								|| (statesList2 != null && statesList2.size() == 0)) {
							isAlwaysDescribed = false;
						}

						// if one description is unknown and the other have 0
						// state checked
						if ((statesList1 == null && statesList2 != null && statesList2.size() == 0)
								|| (statesList2 == null && statesList1 != null && statesList1.size() == 0)) {
							score++;
						} else if (statesList1 != null && statesList2 != null) {

							// nb of common states which are absent
							float commonAbsent = 0;
							// nb of common states which are present
							float commonPresent = 0;
							float other = 0;

							// search common state
							for (int k = 0; k < descriptor.getStates().size(); k++) {
								State state = descriptor.getStates().get(k);

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
							score += applyScoreMethod(commonPresent, commonAbsent, other, scoreMethod);
						}
						cpt++;
					}
				}
			}
		}

		if (cpt >= 1) {
			score = score / cpt;
		}

		// increasing artificially the score of character containing only
		// described taxa
		// if (isAlwaysDescribed && score > 0) {
		// score = (float) ((float) score + (float) 2.0);
		// }

		// fewStatesCharacterFirst option handling
		// if (utils.isFewStatesCharacterFirst() && score > 0 &&
		// character.getStates().size() >= 2) {
		// // increasing artificially score of character with few states
		// float coeff = (float) 1
		// - ((float) character.getStates().size() / (float)
		// maxNbStatesPerCharacter);
		// score = (float) (score + coeff);
		// }

		return score;
	}

	/**
	 * @param descriptor
	 * @param remainingItems
	 * @param scoreMethod
	 * @param descriptorAlreadyUsed
	 * @return
	 * @throws Exception
	 */
	public static float quantitativeDescriptorScore(QuantitativeDescriptor descriptor,
			List<Item> remainingItems, DescriptorTree dependencyTree, int scoreMethod) {

		int cpt = 0;
		float score = 0;
		boolean isAlwaysDescribed = true;
		DescriptorNode node = dependencyTree.getNodeContainingDescriptor(descriptor.getId());

		for (int i = 0; i < remainingItems.size() - 1; i++) {
			for (int j = i + 1; j < remainingItems.size(); j++) {

				// if the descriptor is applicable for both of these items
				if ((!isInapplicable(node, remainingItems.get(i)) && !isInapplicable(node,
						remainingItems.get(j)))) {

					float tmp = -1;

					tmp = applyScoreMethodNum(remainingItems.get(i), remainingItems.get(j), descriptor,
							scoreMethod);

					if (tmp >= 0) {
						score += tmp;
						cpt++;
						// }
					}

				}
			}
		}

		return score;
	}

	/**
	 * @param commonPresent
	 * @param commonAbsent
	 * @param other
	 * @param scoreMethod
	 * @return float, the score using the method requested
	 */
	private static float applyScoreMethod(float commonPresent, float commonAbsent, float other,
			int scoreMethod) {

		float out = 0;

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
	 * @param item1
	 * @param item2
	 * @param descriptor
	 * @param scoreMethod
	 * @return
	 */
	private static float applyScoreMethodNum(Item item1, Item item2, QuantitativeDescriptor descriptor,
			int scoreMethod) {
		float out = 0;

		float commonPercentage = 0; // percentage of common values which are
									// shared

		QuantitativeMeasure quantitativeMeasure1 = item1.getDescription()
				.getDescriptionElement(descriptor.getId()).getQuantitativeMeasure();
		QuantitativeMeasure quantitativeMeasure2 = item2.getDescription()
				.getDescriptionElement(descriptor.getId()).getQuantitativeMeasure();
		if (quantitativeMeasure1 == null || quantitativeMeasure2 == null) {
			return 0;
		} else {
			if (quantitativeMeasure1.getCalculateMinimum() == null
					|| quantitativeMeasure1.getCalculateMaximum() == null
					|| quantitativeMeasure2.getCalculateMinimum() == null
					|| quantitativeMeasure2.getCalculateMaximum() == null) {
				return 0;
			} else {
				commonPercentage = calculateCommonPercentage(quantitativeMeasure1.getCalculateMinimum()
						.doubleValue(), quantitativeMeasure1.getCalculateMaximum().doubleValue(),
						quantitativeMeasure2.getCalculateMinimum().doubleValue(), quantitativeMeasure2
								.getCalculateMaximum().doubleValue());
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

	/**
	 * @param min1
	 * @param max1
	 * @param min2
	 * @param max2
	 * @return
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

	private static boolean isInapplicable(DescriptorNode descriptorNode, Item item) {
		if (descriptorNode.getParentNode() != null) {
			for (State state : descriptorNode.getInapplicableStates()) {
				if (item.getDescriptionElement(descriptorNode.getParentNode().getDescriptor().getId())
						.containsState(state.getId())) {
					return true;
				}
			}
			return isInapplicable(descriptorNode.getParentNode(), item);
		}
		return false;
	}

	/**
	 * @param descriptorNode
	 * @return
	 */
	private float getMaximumChildDescriptorScore(DescriptorNode descriptorNode, List<Item> remainingItems) {
		float max = -1;
		List<DescriptorNode> childrenDescriptorNodes = descriptorNode.getChildNodes();
		if (descriptorNode.getParentNode() != null)
			max = -1;
		else {
			for (DescriptorNode childDescriptorNode : childrenDescriptorNodes) {
				Descriptor childDescriptor = childDescriptorNode.getDescriptor();
			}
		}

		return max;
	}

}