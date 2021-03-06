/*
 * Copyright (c) 2016 Villu Ruusmann
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

import java.util.List;

import org.dmg.pmml.Apply;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.OpType;
import org.dmg.pmml.general_regression.GeneralRegressionModel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.general_regression.GeneralRegressionModelUtil;

public class MVRConverter extends ModelConverter<RGenericVector> {

	public MVRConverter(RGenericVector mvr){
		super(mvr);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector mvr = getObject();

		RDoubleVector coefficients = (RDoubleVector)mvr.getValue("coefficients");
		RDoubleVector scale = (RDoubleVector)mvr.getValue("scale", true);
		RExp terms = mvr.getValue("terms");
		final
		RGenericVector model = (RGenericVector)mvr.getValue("model");

		RStringVector rowNames = coefficients.dimnames(0);
		RStringVector columnNames = coefficients.dimnames(1);

		FormulaContext context = new FormulaContext(){

			@Override
			public List<String> getCategories(String variable){

				if(model.hasValue(variable)){
					RVector<?> vector = (RVector<?>)model.getValue(variable);

					if(vector instanceof RIntegerVector){
						RIntegerVector factor = (RIntegerVector)vector;

						if(factor.isFactor()){
							return factor.getLevelValues();
						}
					}
				}

				return null;
			}

			@Override
			public RGenericVector getData(){
				return model;
			}
		};

		Formula formula = new Formula(encoder);

		FormulaUtil.encodeFeatures(formula, context, terms, encoder);

		// Dependent variable
		{
			FieldName name = FieldName.create(columnNames.asScalar());

			DataField dataField = (DataField)encoder.getField(name);

			encoder.setLabel(dataField);
		}

		// Independent variables
		for(int i = 0; i < rowNames.size(); i++){
			FieldName name = FieldName.create(rowNames.getValue(i));

			Feature feature = formula.resolveFeature(name);

			if(scale != null){
				name = FieldName.create("scale(" + name.getValue() + ")");

				Apply apply = PMMLUtil.createApply("/", (feature.toContinuousFeature()).ref(), PMMLUtil.createConstant(scale.getValue(i)));

				DerivedField derivedField = encoder.createDerivedField(name, OpType.CONTINUOUS, DataType.DOUBLE, apply);

				feature = new ContinuousFeature(encoder, derivedField);
			}

			encoder.addFeature(feature);
		}
	}

	@Override
	public GeneralRegressionModel encodeModel(Schema schema){
		RGenericVector mvr = getObject();

		RDoubleVector coefficients = (RDoubleVector)mvr.getValue("coefficients");
		RDoubleVector xMeans = (RDoubleVector)mvr.getValue("Xmeans");
		RDoubleVector yMeans = (RDoubleVector)mvr.getValue("Ymeans");
		RNumberVector<?> ncomp = (RNumberVector<?>)mvr.getValue("ncomp");

		RStringVector rowNames = coefficients.dimnames(0);
		RStringVector columnNames = coefficients.dimnames(1);
		RStringVector compNames = coefficients.dimnames(2);

		int rows = rowNames.size();
		int columns = columnNames.size();
		int components = compNames.size();

		List<Feature> features = schema.getFeatures();

		List<Double> featureCoefficients = FortranMatrixUtil.getColumn(coefficients.getValues(), rows, (columns * components), 0 + (ValueUtil.asInt(ncomp.asScalar()) - 1));

		Double intercept = yMeans.getValue(0);

		for(int j = 0; j < rowNames.size(); j++){
			intercept -= (featureCoefficients.get(j) * xMeans.getValue(j));
		}

		GeneralRegressionModel generalRegressionModel = new GeneralRegressionModel(GeneralRegressionModel.ModelType.GENERALIZED_LINEAR, MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema), null, null, null)
			.setLinkFunction(GeneralRegressionModel.LinkFunction.IDENTITY);

		GeneralRegressionModelUtil.encodeRegressionTable(generalRegressionModel, features, intercept, featureCoefficients, null);

		return generalRegressionModel;
	}
}