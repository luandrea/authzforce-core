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
package org.ow2.authzforce.core.pdp.impl.combining;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.EffectType;

import org.ow2.authzforce.core.pdp.api.Decidable;
import org.ow2.authzforce.core.pdp.api.combining.BaseCombiningAlg;
import org.ow2.authzforce.core.pdp.api.combining.CombiningAlg;
import org.ow2.authzforce.core.pdp.api.combining.CombiningAlgParameter;

import com.google.common.base.Preconditions;

/**
 * This is the standard Permit-Overrides policy/rule combining algorithm. It allows a single evaluation of Permit to take precedence over any number of deny, not applicable or indeterminate results.
 * Note that since this implementation may change the order of evaluation, compared to the order of declaration, for optimization purposes; therefore it is different from the
 * Ordered-Permit-Overrides-algorithm.
 * 
 * @version $Id: $
 */
final class PermitOverridesAlg extends BaseCombiningAlg<Decidable>
{

	/** {@inheritDoc} */
	@Override
	public CombiningAlg.Evaluator getInstance(final Iterable<CombiningAlgParameter<? extends Decidable>> params, final Iterable<? extends Decidable> combinedElements)
	{
		return new DPOverridesPolicyCombiningAlgEvaluator(Preconditions.checkNotNull(combinedElements), EffectType.PERMIT);
	}

	PermitOverridesAlg(final String algId)
	{
		super(algId, Decidable.class);
	}
}
