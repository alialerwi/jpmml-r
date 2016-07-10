/*
 * Copyright (c) 2015 Villu Ruusmann
 *
 * This file is part of JPMML-R
 *
 * JPMML-R is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-R is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-R.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.rexp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.dmg.pmml.DataType;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FeatureType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.MiningFunctionType;
import org.dmg.pmml.MiningModel;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.Model;
import org.dmg.pmml.MultipleModelMethodType;
import org.dmg.pmml.Node;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Output;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.RegressionNormalizationMethodType;
import org.dmg.pmml.Segment;
import org.dmg.pmml.Segmentation;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.SimpleSetPredicate;
import org.dmg.pmml.Targets;
import org.dmg.pmml.TreeModel;
import org.dmg.pmml.TreeModel.SplitCharacteristic;
import org.dmg.pmml.True;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ListFeature;
import org.jpmml.converter.MiningModelUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;

public class GBMConverter extends ModelConverter<RGenericVector> {

	private LoadingCache<ElementKey, Predicate> predicateCache = CacheBuilder.newBuilder()
		.build(new CacheLoader<ElementKey, Predicate>(){

			@Override
			public Predicate load(ElementKey key){
				Object[] content = key.getContent();

				return encodeCategoricalSplit((ListFeature)content[0], (List<Integer>)content[1], (Boolean)content[2]);
			}
		});


	@Override
	public void encodeFeatures(RGenericVector gbm, FeatureMapper featureMapper){
		RGenericVector distribution = (RGenericVector)gbm.getValue("distribution");
		RGenericVector var_levels = (RGenericVector)gbm.getValue("var.levels");
		RStringVector var_names = (RStringVector)gbm.getValue("var.names");
		RNumberVector<?> var_type = (RNumberVector<?>)gbm.getValue("var.type");

		RStringVector response_name;

		try {
			response_name = (RStringVector)gbm.getValue("response.name");
		} catch(IllegalArgumentException iae){
			response_name = null;
		}

		RStringVector classes;

		try {
			classes = (RStringVector)gbm.getValue("classes");
		} catch(IllegalArgumentException iae){
			classes = null;
		}

		RStringVector name = (RStringVector)distribution.getValue("name");

		// Dependent variable
		{
			FieldName responseName;

			if(response_name != null){
				responseName = FieldName.create(response_name.asScalar());
			} else

			{
				responseName = FieldName.create("y");
			}

			switch(name.asScalar()){
				case "gaussian":
					featureMapper.append(responseName, false);
					break;
				case "adaboost":
				case "bernoulli":
					featureMapper.append(responseName, GBMConverter.BINARY_CLASSES);
					break;
				case "multinomial":
					featureMapper.append(responseName, classes.getValues());
					break;
				default:
					throw new IllegalArgumentException();
			}
		}

		// Independent variables
		for(int i = 0; i < var_names.size(); i++){
			FieldName varName = FieldName.create(var_names.getValue(i));

			boolean categorical = (ValueUtil.asInt(var_type.getValue(i)) > 0);
			if(categorical){
				RStringVector var_level = (RStringVector)var_levels.getValue(i);

				featureMapper.append(varName, var_level.getValues());
			} else

			{
				featureMapper.append(varName, false);
			}
		}
	}

	@Override
	public Schema createSchema(FeatureMapper featureMapper){
		return featureMapper.createSupervisedSchema();
	}

	@Override
	public MiningModel encodeModel(RGenericVector gbm, Schema schema){
		RDoubleVector initF = (RDoubleVector)gbm.getValue("initF");
		RGenericVector trees = (RGenericVector)gbm.getValue("trees");
		RGenericVector c_splits = (RGenericVector)gbm.getValue("c.splits");
		RGenericVector distribution = (RGenericVector)gbm.getValue("distribution");

		RStringVector name = (RStringVector)distribution.getValue("name");

		Schema segmentSchema = schema.toAnonymousSchema();

		List<TreeModel> treeModels = new ArrayList<>();

		for(int i = 0; i < trees.size(); i++){
			RGenericVector tree = (RGenericVector)trees.getValue(i);

			TreeModel treeModel = encodeTreeModel(MiningFunctionType.REGRESSION, tree, c_splits, segmentSchema);

			treeModels.add(treeModel);
		}

		Segmentation segmentation = MiningModelUtil.createSegmentation(MultipleModelMethodType.SUM, treeModels);

		MiningModel miningModel = encodeMiningModel(name, segmentation, initF.asScalar(), schema);

		return miningModel;
	}

	private MiningModel encodeMiningModel(RStringVector name, Segmentation segmentation, Double initF, Schema schema){

		switch(name.asScalar()){
			case "gaussian":
				return encodeRegression(segmentation, initF, schema);
			case "adaboost":
				return encodeBinaryClassification(segmentation, initF, -2d, schema);
			case "bernoulli":
				return encodeBinaryClassification(segmentation, initF, -1d, schema);
			case "multinomial":
				return encodeMultinomialClassification(segmentation, initF, schema);
			default:
				break;
		}

		throw new IllegalArgumentException();
	}

	private MiningModel encodeRegression(Segmentation segmentation, Double initF, Schema schema){
		MiningSchema miningSchema = ModelUtil.createMiningSchema(schema);

		Targets targets = new Targets()
			.addTargets(ModelUtil.createRescaleTarget(schema.getTargetField(), null, initF));

		MiningModel miningModel = new MiningModel(MiningFunctionType.REGRESSION, miningSchema)
			.setSegmentation(segmentation)
			.setTargets(targets);

		return miningModel;
	}

	private MiningModel encodeBinaryClassification(Segmentation segmentation, Double initF, double coefficient, Schema schema){
		Schema segmentSchema = schema.toAnonymousSchema();

		MiningSchema miningSchema = ModelUtil.createMiningSchema(segmentSchema);

		OutputField rawGbmValue = ModelUtil.createPredictedField(FieldName.create("rawGbmValue"));

		OutputField scaledGbmValue = new OutputField(FieldName.create("scaledGbmValue"))
			.setFeature(FeatureType.TRANSFORMED_VALUE)
			.setDataType(DataType.DOUBLE)
			.setOpType(OpType.CONTINUOUS)
			.setExpression(encodeScalingExpression(rawGbmValue.getName(), initF));

		Output output = new Output()
			.addOutputFields(rawGbmValue, scaledGbmValue);

		MiningModel miningModel = new MiningModel(MiningFunctionType.REGRESSION, miningSchema)
			.setSegmentation(segmentation)
			.setOutput(output);

		return MiningModelUtil.createBinaryLogisticClassification(schema, miningModel, coefficient, true);
	}

	private MiningModel encodeMultinomialClassification(Segmentation segmentation, Double initF, Schema schema){
		List<Segment> segments = segmentation.getSegments();

		List<Model> miningModels = new ArrayList<>();

		Schema segmentSchema = schema.toAnonymousSchema();

		List<String> targetCategories = schema.getTargetCategories();
		for(int i = 0; i < targetCategories.size(); i++){
			String targetCategory = targetCategories.get(i);

			OutputField rawGbmValue = ModelUtil.createPredictedField(FieldName.create("rawGbmValue_" + targetCategory));

			OutputField transformedGbmValue = new OutputField(FieldName.create("transformedGbmValue_" + targetCategory))
				.setFeature(FeatureType.TRANSFORMED_VALUE)
				.setDataType(DataType.DOUBLE)
				.setOpType(OpType.CONTINUOUS)
				.setExpression(encodeScalingExpression(rawGbmValue.getName(), initF));

			List<Segment> segmentSegments = getColumn(segments, i, (segments.size() / targetCategories.size()), targetCategories.size());

			Segmentation segmentSegmentation = new Segmentation(MultipleModelMethodType.SUM, segmentSegments);

			MiningSchema miningSchema = ModelUtil.createMiningSchema(segmentSchema);

			Output output = new Output()
				.addOutputFields(rawGbmValue, transformedGbmValue);

			MiningModel miningModel = new MiningModel(MiningFunctionType.REGRESSION, miningSchema)
				.setSegmentation(segmentSegmentation)
				.setOutput(output);

			miningModels.add(miningModel);
		}

		return MiningModelUtil.createClassification(schema, miningModels, RegressionNormalizationMethodType.SOFTMAX, true);
	}

	private TreeModel encodeTreeModel(MiningFunctionType miningFunction, RGenericVector tree, RGenericVector c_splits, Schema schema){
		Node root = new Node()
			.setId("1")
			.setPredicate(new True());

		encodeNode(root, 0, tree, c_splits, schema);

		MiningSchema miningSchema = ModelUtil.createMiningSchema(schema, root);

		TreeModel treeModel = new TreeModel(miningFunction, miningSchema, root)
			.setSplitCharacteristic(SplitCharacteristic.MULTI_SPLIT);

		return treeModel;
	}

	private void encodeNode(Node node, int i, RGenericVector tree, RGenericVector c_splits, Schema schema){
		RIntegerVector splitVar = (RIntegerVector)tree.getValue(0);
		RDoubleVector splitCodePred = (RDoubleVector)tree.getValue(1);
		RIntegerVector leftNode = (RIntegerVector)tree.getValue(2);
		RIntegerVector rightNode = (RIntegerVector)tree.getValue(3);
		RIntegerVector missingNode = (RIntegerVector)tree.getValue(4);
		RDoubleVector prediction = (RDoubleVector)tree.getValue(7);

		Predicate missingPredicate = null;

		Predicate leftPredicate = null;
		Predicate rightPredicate = null;

		Integer var = splitVar.getValue(i);
		if(var != -1){
			Feature feature = schema.getFeature(var);

			missingPredicate = encodeIsMissingSplit(feature);

			Double split = splitCodePred.getValue(i);

			if(feature instanceof ListFeature){
				int index = ValueUtil.asInt(split);

				RIntegerVector c_split = (RIntegerVector)c_splits.getValue(index);

				List<Integer> splitValues = c_split.getValues();

				leftPredicate = this.predicateCache.getUnchecked(new ElementKey(feature, splitValues, Boolean.TRUE));
				rightPredicate = this.predicateCache.getUnchecked(new ElementKey(feature, splitValues, Boolean.FALSE));
			} else

			if(feature instanceof ContinuousFeature){
				leftPredicate = encodeContinuousSplit(feature, split, true);
				rightPredicate = encodeContinuousSplit(feature, split, false);
			} else

			{
				throw new IllegalArgumentException();
			}
		} else

		{
			Double value = prediction.getValue(i);

			node.setScore(ValueUtil.formatValue(value));
		}

		Integer missing = missingNode.getValue(i);
		if(missing != -1){
			Node missingChild = new Node()
				.setId(String.valueOf(missing + 1))
				.setPredicate(missingPredicate);

			encodeNode(missingChild, missing, tree, c_splits, schema);

			node.addNodes(missingChild);
		}

		Integer left = leftNode.getValue(i);
		if(left != -1){
			Node leftChild = new Node()
				.setId(String.valueOf(left + 1))
				.setPredicate(leftPredicate);

			encodeNode(leftChild, left, tree, c_splits, schema);

			node.addNodes(leftChild);
		}

		Integer right = rightNode.getValue(i);
		if(right != -1){
			Node rightChild = new Node()
				.setId(String.valueOf(right + 1))
				.setPredicate(rightPredicate);

			encodeNode(rightChild, right, tree, c_splits, schema);

			node.addNodes(rightChild);
		}
	}

	static
	private Predicate encodeIsMissingSplit(Feature feature){
		SimplePredicate simplePredicate = new SimplePredicate()
			.setField(feature.getName())
			.setOperator(SimplePredicate.Operator.IS_MISSING);

		return simplePredicate;
	}

	static
	private Predicate encodeCategoricalSplit(ListFeature listFeature, List<Integer> splitValues, boolean left){
		List<String> values = selectValues(listFeature.getValues(), splitValues, left);

		if(values.size() == 1){
			String value = values.get(0);

			SimplePredicate simplePredicate = new SimplePredicate()
				.setField(listFeature.getName())
				.setOperator(SimplePredicate.Operator.EQUAL)
				.setValue(value);

			return simplePredicate;
		}

		SimpleSetPredicate simpleSetPredicate = new SimpleSetPredicate()
			.setField(listFeature.getName())
			.setBooleanOperator(SimpleSetPredicate.BooleanOperator.IS_IN)
			.setArray(FeatureUtil.createArray(listFeature, values));

		return simpleSetPredicate;
	}

	static
	private Predicate encodeContinuousSplit(Feature feature, Double split, boolean left){
		SimplePredicate simplePredicate = new SimplePredicate()
			.setField(feature.getName())
			.setOperator(left ? SimplePredicate.Operator.LESS_THAN : SimplePredicate.Operator.GREATER_OR_EQUAL)
			.setValue(ValueUtil.formatValue(split));

		return simplePredicate;
	}

	static
	private Expression encodeScalingExpression(FieldName name, Double initF){
		Expression expression = new FieldRef(name);

		if(!ValueUtil.isZero(initF)){
			expression = PMMLUtil.createApply("+", expression, PMMLUtil.createConstant(initF));
		}

		return expression;
	}

	static
	private <E> List<E> selectValues(List<E> values, List<Integer> splitValues, boolean left){

		if(values.size() != splitValues.size()){
			throw new IllegalArgumentException();
		}

		List<E> result = new ArrayList<>();

		for(int i = 0; i < values.size(); i++){
			E value = values.get(i);

			boolean append;

			if(left){
				append = (splitValues.get(i) == -1);
			} else

			{
				append = (splitValues.get(i) == 1);
			} // End if

			if(append){
				result.add(value);
			}
		}

		return result;
	}

	static
	private <E> List<E> getColumn(List<E> values, int index, int rows, int columns){

		if(values.size() != (rows * columns)){
			throw new IllegalArgumentException();
		}

		List<E> result = new ArrayList<>(rows);

		for(int row = 0; row < rows; row++){
			E value = values.get((row * columns) + index);

			result.add(value);
		}

		return result;
	}

	private static final List<String> BINARY_CLASSES = Arrays.asList("0", "1");
}