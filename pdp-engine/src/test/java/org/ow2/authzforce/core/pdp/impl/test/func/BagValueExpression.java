/**
 * Copyright 2012-2017 Thales Services SAS.
 *
 * This file is part of AuthzForce CE.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ow2.authzforce.core.pdp.impl.test.func;

import javax.xml.bind.JAXBElement;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.ExpressionType;

import org.ow2.authzforce.core.pdp.api.expression.ConstantExpression;
import org.ow2.authzforce.core.pdp.api.value.Bag;
import org.ow2.authzforce.core.pdp.api.value.Datatype;

/**
 * Bag value expression
 *
 * @param <BV>
 *            bag type
 */
public class BagValueExpression<BV extends Bag<?>> extends ConstantExpression<BV>
{

	protected BagValueExpression(final Datatype<BV> datatype, final BV v) throws IllegalArgumentException
	{
		super(datatype, v);
	}

	@Override
	public JAXBElement<? extends ExpressionType> getJAXBElement()
	{
		throw new UnsupportedOperationException();
	}

}