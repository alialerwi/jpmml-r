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

import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LMConverterTest extends ConverterTest {

	@Test
	public void split(){
		assertEquals(Arrays.asList("", ""), LMConverter.split(":"));
		assertEquals(Arrays.asList("::"), LMConverter.split("::"));

		assertEquals(Arrays.asList("A"), LMConverter.split("A"));
		assertEquals(Arrays.asList("", "A"), LMConverter.split(":A"));
		assertEquals(Arrays.asList("A", ""), LMConverter.split("A:"));
		assertEquals(Arrays.asList("::A"), LMConverter.split("::A"));
		assertEquals(Arrays.asList("A::"), LMConverter.split("A::"));

		assertEquals(Arrays.asList("A::B::C"), LMConverter.split("A::B::C"));
		assertEquals(Arrays.asList("", "A::B::C", ""), LMConverter.split(":A::B::C:"));
		assertEquals(Arrays.asList("A::B", "C"), LMConverter.split("A::B:C"));
		assertEquals(Arrays.asList("A", "B::C"), LMConverter.split("A:B::C"));
		assertEquals(Arrays.asList("A", "B", "C"), LMConverter.split("A:B:C"));
		assertEquals(Arrays.asList("", "A", "B", "C", ""), LMConverter.split(":A:B:C:"));
	}

	@Test
	public void evaluateFormulaAuto() throws Exception {
		evaluate("LinearRegressionFormula", "Auto");
	}

	@Test
	public void evaluateCustFormulaAuto() throws Exception {
		evaluate("LinearRegressionCustFormula", "Auto");
	}

	@Test
	public void evaluateFormulaWineQuality() throws Exception {
		evaluate("LinearRegressionFormula", "WineQuality");
	}
}