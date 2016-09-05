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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.dmg.pmml.DataField;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.mining.MiningModel;
import org.jpmml.converter.Schema;
import org.jpmml.xgboost.Classification;
import org.jpmml.xgboost.FeatureMap;
import org.jpmml.xgboost.GBTree;
import org.jpmml.xgboost.Learner;
import org.jpmml.xgboost.ObjFunction;
import org.jpmml.xgboost.XGBoostUtil;

public class XGBoostConverter extends ModelConverter<RGenericVector> {

	public XGBoostConverter(RGenericVector booster){
		super(booster);
	}

	@Override
	public void encodeFeatures(FeatureMapper featureMapper){
		RGenericVector booster = getObject();

		RVector<?> fmap;

		try {
			fmap = (RVector<?>)booster.getValue("fmap");
		} catch(IllegalArgumentException iae){
			throw new IllegalArgumentException("No feature map information. Please initialize the \'fmap\' attribute");
		}

		FeatureMap featureMap;

		try {
			featureMap = loadFeatureMap(fmap);
		} catch(IOException ioe){
			throw new IllegalArgumentException(ioe);
		}

		List<DataField> dataFields = featureMap.getDataFields();

		// Dependent variable
		{
			featureMapper.append(FieldName.create("_target"), false);
		}

		// Independent variables
		for(DataField dataField : dataFields){
			featureMapper.append(dataField);
		}
	}

	@Override
	public MiningModel encodeModel(Schema schema){
		RGenericVector booster = getObject();

		RRaw raw = (RRaw)booster.getValue("raw");

		Learner learner;

		try {
			learner = loadLearner(raw);
		} catch(IOException ioe){
			throw new IllegalArgumentException(ioe);
		}

		ObjFunction obj = learner.getObj();

		float baseScore = learner.getBaseScore();

		if(obj instanceof Classification){
			Classification classification = (Classification)obj;

			List<String> targetCategories = new ArrayList<>();

			for(int i = 0; i < classification.getNumClass(); i++){
				targetCategories.add(String.valueOf(i + 1));
			}

			schema = new Schema(schema.getTargetField(), targetCategories, schema.getActiveFields(), schema.getFeatures());
		}

		GBTree gbt = learner.getGBTree();

		MiningModel miningModel = gbt.encodeMiningModel(obj, baseScore, schema);

		return miningModel;
	}

	static
	private FeatureMap loadFeatureMap(RVector<?> fmap) throws IOException {

		if(fmap instanceof RStringVector){
			return loadFeatureMap((RStringVector)fmap);
		} else

		if(fmap instanceof RGenericVector){
			return loadFeatureMap((RGenericVector)fmap);
		}

		throw new IllegalArgumentException();
	}

	static
	private FeatureMap loadFeatureMap(RStringVector fmap) throws IOException {
		File file = new File(fmap.asScalar());

		try(InputStream is = new FileInputStream(file)){
			return XGBoostUtil.loadFeatureMap(is);
		}
	}

	static
	private FeatureMap loadFeatureMap(RGenericVector fmap){
		RIntegerVector id = (RIntegerVector)fmap.getValue(0);
		RIntegerVector name = (RIntegerVector)fmap.getValue(1);
		RIntegerVector type = (RIntegerVector)fmap.getValue(2);

		FeatureMap featureMap = new FeatureMap();

		for(int i = 0; i < id.size(); i++){
			featureMap.load(String.valueOf(id.getValue(i)), name.getLevelValue(i), type.getLevelValue(i));
		}

		return featureMap;
	}

	static
	private Learner loadLearner(RRaw raw) throws IOException {
		byte[] value = raw.getValue();

		try(InputStream is = new ByteArrayInputStream(value)){
			return XGBoostUtil.loadLearner(is);
		}
	}
}