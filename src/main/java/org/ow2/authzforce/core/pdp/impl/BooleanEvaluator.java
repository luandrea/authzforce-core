/**
 * Copyright (C) 2012-2016 Thales Services SAS.
 *
 * This file is part of AuthZForce CE.
 *
 * AuthZForce CE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AuthZForce CE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AuthZForce CE.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.ow2.authzforce.core.pdp.impl;

import org.ow2.authzforce.core.pdp.api.EvaluationContext;
import org.ow2.authzforce.core.pdp.api.IndeterminateEvaluationException;

/**
 * Evaluator returning a boolean result
 */
public interface BooleanEvaluator
{

	/**
	 * Evaluates the condition
	 *
	 * @param context
	 *            the representation of the request
	 * @return true if and only if the condition holds
	 * @throws org.ow2.authzforce.core.pdp.api.IndeterminateEvaluationException
	 *             if error evaluating the condition
	 */
	boolean evaluate(EvaluationContext context) throws IndeterminateEvaluationException;

}