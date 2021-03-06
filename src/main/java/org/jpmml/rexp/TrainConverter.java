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

import org.dmg.pmml.PMML;

public class TrainConverter extends Converter<RGenericVector> {

	public TrainConverter(RGenericVector train){
		super(train);
	}

	@Override
	public PMML encodePMML(){
		RGenericVector train = getObject();

		RExp finalModel = train.getValue("finalModel");
		RGenericVector preProcess = (RGenericVector)train.getValue("preProcess");

		ConverterFactory converterFactory = ConverterFactory.newInstance();

		ModelConverter<RExp> converter = (ModelConverter<RExp>)converterFactory.newConverter(finalModel);

		RExpEncoder encoder;

		if(preProcess != null){
			encoder = new PreProcessEncoder(preProcess);
		} else

		{
			encoder = new RExpEncoder();
		}

		return converter.encodePMML(encoder);
	}
}