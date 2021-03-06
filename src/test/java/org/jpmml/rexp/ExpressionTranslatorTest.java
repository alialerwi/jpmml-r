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

import java.util.ArrayList;
import java.util.List;

import org.dmg.pmml.Apply;
import org.dmg.pmml.Constant;
import org.dmg.pmml.DataType;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.Interval;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ExpressionTranslatorTest {

	@Test
	public void translate(){
		Apply apply = (Apply)ExpressionTranslator.translateExpression("(1.0 + log(A / B)) ^ 2");

		List<Expression> expressions = checkApply(apply, "pow", Apply.class, Constant.class);

		Expression left = expressions.get(0);
		Expression right = expressions.get(1);

		checkConstant((Constant)right, "2", null);

		expressions = checkApply((Apply)left, "+", Constant.class, Apply.class);

		left = expressions.get(0);
		right = expressions.get(1);

		checkConstant((Constant)left, "1.0", DataType.DOUBLE);

		expressions = checkApply((Apply)right, "ln", Apply.class);

		left = expressions.get(0);

		expressions = checkApply((Apply)left, "/", FieldRef.class, FieldRef.class);

		left = expressions.get(0);
		right = expressions.get(1);

		checkFieldRef((FieldRef)left, FieldName.create("A"));
		checkFieldRef((FieldRef)right, FieldName.create("B"));
	}

	@Test
	public void translateIfExpression(){
		Apply apply = (Apply)ExpressionTranslator.translateExpression("if(is.na(x)) TRUE else FALSE");

		List<Expression> expressions = checkApply(apply, "if", Apply.class, Constant.class, Constant.class);

		Expression condition = expressions.get(0);

		checkApply((Apply)condition, "isMissing", FieldRef.class);

		Expression first = expressions.get(1);
		Expression second = expressions.get(2);

		checkConstant((Constant)first, "true", DataType.BOOLEAN);
		checkConstant((Constant)second, "false", DataType.BOOLEAN);
	}

	@Test
	public void translateLogicalExpression(){
		Apply apply = (Apply)ExpressionTranslator.translateExpression("a >= 0.0 & b >= 0.0 | c <= 0.0");

		List<Expression> expressions = checkApply(apply, "or", Apply.class, Apply.class);

		Expression left = expressions.get(0);
		Expression right = expressions.get(1);

		expressions = checkApply((Apply)left, "and", Apply.class, Apply.class);
		checkApply((Apply)right, "lessOrEqual", FieldRef.class, Constant.class);

		left = expressions.get(0);
		right = expressions.get(1);

		checkApply((Apply)left, "greaterOrEqual", FieldRef.class, Constant.class);
		checkApply((Apply)right, "greaterOrEqual", FieldRef.class, Constant.class);
	}

	@Test
	public void translateLogicalExpressionChain(){
		String string = "(x == 0) | ((x == 1) | (x == 2)) | x == 3";

		Apply apply = (Apply)ExpressionTranslator.translateExpression(string, false);

		List<Expression> expressions = checkApply(apply, "or", Apply.class, Apply.class);

		Expression left = expressions.get(0);
		Expression right = expressions.get(1);

		checkApply((Apply)left, "or", Apply.class, Apply.class);
		checkApply((Apply)right, "equal", FieldRef.class, Constant.class);

		apply = (Apply)ExpressionTranslator.translateExpression(string, true);

		checkApply(apply, "or", Apply.class, Apply.class, Apply.class, Apply.class);
	}

	@Test
	public void translateRelationalExpression(){
		Apply apply = (Apply)ExpressionTranslator.translateExpression("if(x < 0) \"negative\" else if(x > 0) \"positive\" else \"zero\"");

		List<Expression> expressions = checkApply(apply, "if", Apply.class, Constant.class, Apply.class);

		Expression condition = expressions.get(0);

		checkApply((Apply)condition, "lessThan", FieldRef.class, Constant.class);

		Expression first = expressions.get(1);
		Expression second = expressions.get(2);

		checkConstant((Constant)first, "negative", null);

		expressions = checkApply((Apply)second, "if", Apply.class, Constant.class, Constant.class);

		condition = expressions.get(0);

		checkApply((Apply)condition, "greaterThan", FieldRef.class, Constant.class);

		first = expressions.get(1);
		second = expressions.get(2);

		checkConstant((Constant)first, "positive", null);
		checkConstant((Constant)second, "zero", null);
	}

	@Test
	public void translateArithmeticExpressionChain(){
		Apply apply = (Apply)ExpressionTranslator.translateExpression("A + B - X + C");

		List<Expression> expressions = checkApply(apply, "+", Apply.class, FieldRef.class);

		Expression left = expressions.get(0);
		Expression right = expressions.get(1);

		expressions = checkApply((Apply)left, "-", Apply.class, FieldRef.class);
		checkFieldRef((FieldRef)right, FieldName.create("C"));

		left = expressions.get(0);
		right = expressions.get(1);

		expressions = checkApply((Apply)left, "+", FieldRef.class, FieldRef.class);
		checkFieldRef((FieldRef)right, FieldName.create("X"));

		left = expressions.get(0);
		right = expressions.get(1);

		checkFieldRef((FieldRef)left, FieldName.create("A"));
		checkFieldRef((FieldRef)right, FieldName.create("B"));
	}

	@Test
	public void translateExponentiationExpression(){
		Apply apply = (Apply)ExpressionTranslator.translateExpression("-2^-3");

		List<Expression> expressions = checkApply(apply, "*", Constant.class, Apply.class);

		Expression left = expressions.get(0);
		Expression right = expressions.get(1);

		checkConstant((Constant)left, "-1", null);

		expressions = checkApply((Apply)right, "pow", Constant.class, Constant.class);

		left = expressions.get(0);
		right = expressions.get(1);

		checkConstant((Constant)left, "2", null);
		checkConstant((Constant)right, "-3", null);

		apply = (Apply)ExpressionTranslator.translateExpression("-2^-2*1.5");

		expressions = checkApply(apply, "*", Apply.class, Constant.class);

		left = expressions.get(0);
		right = expressions.get(1);

		expressions = checkApply((Apply)left, "*", Constant.class, Apply.class);
		checkConstant((Constant)right, "1.5", DataType.DOUBLE);

		left = expressions.get(0);
		right = expressions.get(1);

		checkConstant((Constant)left, "-1", null);
		expressions = checkApply((Apply)right, "pow", Constant.class, Constant.class);

		left = expressions.get(0);
		right = expressions.get(1);

		checkConstant((Constant)left, "2", null);
		checkConstant((Constant)right, "-2", null);
	}

	@Test
	public void translateFunctionExpression(){
		FunctionExpression functionExpression = (FunctionExpression)ExpressionTranslator.translateExpression("parent(first = child(A, log(A)), child(1 + B, right = 0), \"third\" = child(left = 0, c(A, B, C)))");

		checkFunctionExpression(functionExpression, "parent", "first", null, "third");

		FunctionExpression.Argument first = functionExpression.getArgument("first");
		FunctionExpression.Argument second;

		try {
			second = functionExpression.getArgument("second");

			fail();
		} catch(IllegalArgumentException iae){
			second = functionExpression.getArgument(1);
		}

		FunctionExpression.Argument third = functionExpression.getArgument("third");

		assertEquals("first = child(A, log(A))", first.format());
		assertEquals("child(A, log(A))", first.formatExpression());

		assertEquals("child(1 + B, right = 0)", second.format());
		assertEquals("child(1 + B, right = 0)", second.formatExpression());

		assertEquals("\"third\" = child(left = 0, c(A, B, C))", third.format());
		assertEquals("child(left = 0, c(A, B, C))", third.formatExpression());

		List<Expression> expressions = checkFunctionExpression((FunctionExpression)first.getExpression(), "child", null, null);

		Expression left = expressions.get(0);
		Expression right = expressions.get(1);

		checkFieldRef((FieldRef)left, FieldName.create("A"));
		checkApply((Apply)right, "ln", FieldRef.class);

		expressions = checkFunctionExpression((FunctionExpression)second.getExpression(), "child", null, "right");

		left = expressions.get(0);
		right = expressions.get(1);

		checkApply((Apply)left, "+", Constant.class, FieldRef.class);
		checkConstant((Constant)right, "0", null);

		expressions = checkFunctionExpression((FunctionExpression)third.getExpression(), "child", "left", null);

		left = expressions.get(0);
		right = expressions.get(1);

		checkConstant((Constant)left, "0", null);
		checkFunctionExpression((FunctionExpression)right, "c", null, null, null);
	}

	@Test
	public void translateParenthesizedExpression(){
		Apply apply = (Apply)ExpressionTranslator.translateExpression("TRUE | TRUE & FALSE");

		checkApply(apply, "or", Constant.class, Apply.class);

		apply = (Apply)ExpressionTranslator.translateExpression("(TRUE | TRUE) & FALSE");

		checkApply(apply, "and", Apply.class, Constant.class);
	}

	@Test
	public void translateInterval(){
		Interval interval = ExpressionTranslator.translateInterval("(-10.0E+0, +10.0E-0]");

		assertEquals(Interval.Closure.OPEN_CLOSED, interval.getClosure());
		assertEquals(new Double("-10.0E0"), interval.getLeftMargin());
		assertEquals(new Double("+10.0E0"), interval.getRightMargin());

		try {
			interval = ExpressionTranslator.translateInterval("(0, NaN)");

			fail();
		} catch(IllegalArgumentException iae){
			// Ignored
		}

		interval = ExpressionTranslator.translateInterval("[-Inf, +Inf]");

		assertEquals(Interval.Closure.CLOSED_CLOSED, interval.getClosure());
		assertEquals(null, interval.getLeftMargin());
		assertEquals(null, interval.getRightMargin());
	}

	static
	private List<Expression> checkApply(Apply apply, String function, Class<? extends Expression>... expressionClazzes){
		assertEquals(function, apply.getFunction());

		List<Expression> expressions = apply.getExpressions();
		assertEquals(expressionClazzes.length, expressions.size());

		for(int i = 0; i < expressionClazzes.length; i++){
			Class<? extends Expression> expressionClazz = expressionClazzes[i];
			Expression expression = expressions.get(i);

			assertEquals(expressionClazz, expression.getClass());
		}

		return expressions;
	}

	static
	private List<Expression> checkFunctionExpression(FunctionExpression functionExpression, String function, String... tags){
		assertEquals(function, functionExpression.getFunction());

		List<FunctionExpression.Argument> arguments = functionExpression.getArguments();
		assertEquals(tags.length, arguments.size());

		List<Expression> expressions = new ArrayList<>();

		for(int i = 0; i < arguments.size(); i++){
			FunctionExpression.Argument argument = arguments.get(i);

			String tag = argument.getTag();
			Expression expression = argument.getExpression();

			assertEquals(tag, tags[i]);

			expressions.add(expression);
		}

		return expressions;
	}

	static
	private void checkFieldRef(FieldRef fieldRef, FieldName name){
		assertEquals(name, fieldRef.getField());
	}

	static
	private void checkConstant(Constant constant, String value, DataType dataType){
		assertEquals(value, constant.getValue());
		assertEquals(dataType, constant.getDataType());
	}
}