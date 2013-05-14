/**
 * Copyright (C) ${year} T0101841 <${email}>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.sun.xacml;

import java.net.URI;
import java.util.List;
import java.util.Set;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.AttributeType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.AttributesType;

import org.w3c.dom.Node;

import com.sun.xacml.attr.DateAttribute;
import com.sun.xacml.attr.DateTimeAttribute;
import com.sun.xacml.attr.TimeAttribute;
import com.sun.xacml.attr.xacmlv3.AttributeValue;
import com.sun.xacml.cond.xacmlv3.EvaluationResult;

/**
 * Manages the context of a single policy evaluation. Typically, an instance is
 * instantiated whenever the PDP gets a request and needs to perform an
 * evaluation as a result. The <code>BasicEvaluationCtx</code> class provides a
 * basic implementation that is used by default.
 * 
 * @since 1.0
 * @author Seth Proctor
 * @author Romain Ferrari
 */
public interface EvaluationCtx {

	/**
	 * The standard URI for listing a resource's id
	 */
	public static final String RESOURCE_ID = "urn:oasis:names:tc:xacml:1.0:resource:resource-id";

	/**
	 * The standard URI for listing a resource's scope
	 */
	public static final String RESOURCE_SCOPE = "urn:oasis:names:tc:xacml:1.0:resource:scope";

	/**
	 * Resource scope of Immediate (only the given resource)
	 */
	public static final int SCOPE_IMMEDIATE = 0;

	/**
	 * Resource scope of Children (the given resource and its direct children)
	 */
	public static final int SCOPE_CHILDREN = 1;

	/**
	 * Resource scope of Descendants (the given resource and all descendants at
	 * any depth or distance)
	 */
	public static final int SCOPE_DESCENDANTS = 2;

	/**
	 * Returns the DOM root of the original RequestType XML document, if this
	 * context is backed by an XACML Request. If this context is not backed by
	 * an XML representation, then an exception is thrown.
	 * 
	 * @return the DOM root node
	 * 
	 * @throws UnsupportedOperationException
	 *             if the context is not backed by an XML representation
	 */
	public Node getRequestRoot();

	/**
	 * Returns the resource scope, which will be one of the three fields
	 * denoting Immediate, Children, or Descendants.
	 * 
	 * @return the scope of the resource
	 */
	public int getScope();

	/**
	 * Returns the resource named in the request as resource-id.
	 * 
	 * @return the resourceMap
	 */
	// public Map<String, Set<AttributeType>> getResourceMap();

	/**
	 * Returns the identifier for the resource being requested.
	 * 
	 * @return the resource
	 */
	public AttributeValue getResourceId();

	/**
	 * Returns a set of attributes that need to be included in result. (i.e.
	 * IncludeInResult="true")
	 * 
	 * @return the set of attributes that need to be include in the result
	 */
	public List<AttributesType> getIncludeInResults();

	/**
	 * Changes the value of the resource-id attribute in this context. This is
	 * useful when you have multiple resources (ie, a scope other than
	 * IMMEDIATE), and you need to keep changing only the resource-id to
	 * evaluate the different effective requests.
	 * 
	 * @param resourceId
	 *            the new resource-id value
	 */
	public void setResourceId(AttributeValue resourceId);

	/**
	 * Returns the value for the current time as known by the PDP (if this value
	 * was also supplied in the Request, this will generally be a different
	 * value). Details of caching or location-based resolution are left to the
	 * underlying implementation.
	 * 
	 * @return the current time
	 */
	public TimeAttribute getCurrentTime();

	/**
	 * Returns the value for the current date as known by the PDP (if this value
	 * was also supplied in the Request, this will generally be a different
	 * value). Details of caching or location-based resolution are left to the
	 * underlying implementation.
	 * 
	 * @return the current date
	 */
	public DateAttribute getCurrentDate();

	/**
	 * Returns the value for the current dateTime as known by the PDP (if this
	 * value was also supplied in the Request, this will generally be a
	 * different value). Details of caching or location-based resolution are
	 * left to the underlying implementation.
	 * 
	 * @return the current date
	 */
	public DateTimeAttribute getCurrentDateTime();

	/**
	 * Returns available subject attribute value(s) ignoring the issuer.
	 * 
	 * @param type
	 *            the type of the attribute value(s) to find
	 * @param id
	 *            the id of the attribute value(s) to find
	 * @param category
	 *            the category the attribute value(s) must be in
	 * 
	 * @return a result containing a bag either empty because no values were
	 *         found or containing at least one value, or status associated with
	 *         an Indeterminate result
	 */
	public EvaluationResult getSubjectAttribute(URI type, URI id, URI category);

	/**
	 * Returns available subject attribute value(s).
	 * 
	 * @param type
	 *            the type of the attribute value(s) to find
	 * @param id
	 *            the id of the attribute value(s) to find
	 * @param issuer
	 *            the issuer of the attribute value(s) to find or null
	 * @param category
	 *            the category the attribute value(s) must be in
	 * 
	 * @return a result containing a bag either empty because no values were
	 *         found or containing at least one value, or status associated with
	 *         an Indeterminate result
	 */
	public EvaluationResult getSubjectAttribute(URI type, URI id, URI issuer,
			URI category);

	/**
	 * Returns available resource attribute value(s).
	 * 
	 * @param type
	 *            the type of the attribute value(s) to find
	 * @param id
	 *            the id of the attribute value(s) to find
	 * @param issuer
	 *            the issuer of the attribute value(s) to find or null
	 * 
	 * @return a result containing a bag either empty because no values were
	 *         found or containing at least one value, or status associated with
	 *         an Indeterminate result
	 */
	public EvaluationResult getResourceAttribute(URI type, URI id, URI issuer);

	/**
	 * Returns available action attribute value(s).
	 * 
	 * @param type
	 *            the type of the attribute value(s) to find
	 * @param id
	 *            the id of the attribute value(s) to find
	 * @param issuer
	 *            the issuer of the attribute value(s) to find or null
	 * 
	 * @return a result containing a bag either empty because no values were
	 *         found or containing at least one value, or status associated with
	 *         an Indeterminate result
	 */
	public EvaluationResult getActionAttribute(URI type, URI id, URI issuer);

	/**
	 * Returns available environment attribute value(s).
	 * <p>
	 * Note that if you want to resolve the correct current date, time, or
	 * dateTime as seen from an evaluation point of view, you should use this
	 * method and supply the corresponding identifier.
	 * 
	 * @param type
	 *            the type of the attribute value(s) to find
	 * @param id
	 *            the id of the attribute value(s) to find
	 * @param issuer
	 *            the issuer of the attribute value(s) to find or null
	 * 
	 * @return a result containing a bag either empty because no values were
	 *         found or containing at least one value, or status associated with
	 *         an Indeterminate result
	 */
	public EvaluationResult getEnvironmentAttribute(URI type, URI id, URI issuer);
	
	
	/**
	 * Returns available custom attribute value(s).
	 * <p>
	 * Note that if you want to resolve the correct current date, time, or
	 * dateTime as seen from an evaluation point of view, you should use this
	 * method and supply the corresponding identifier.
	 * 
	 * @param type
	 *            the type of the attribute value(s) to find
	 * @param id
	 *            the id of the attribute value(s) to find
	 * @param issuer
	 *            the issuer of the attribute value(s) to find or null
	 * 
	 * @return a result containing a bag either empty because no values were
	 *         found or containing at least one value, or status associated with
	 *         an Indeterminate result
	 */
	public EvaluationResult getCustomAttribute(URI type, URI id, URI issuer);

	/**
	 * Returns the attribute value(s) retrieved using the given XPath
	 * expression.
	 * 
	 * @param contextPath
	 *            the XPath expression to search
	 * @param namespaceNode
	 *            the DOM node defining namespace mappings to use, or null if
	 *            mappings come from the context root
	 * @param type
	 *            the type of the attribute value(s) to find
	 * @param xpathVersion
	 *            the version of XPath to use
	 * 
	 * @return a result containing a bag either empty because no values were
	 *         found or containing at least one value, or status associated with
	 *         an Indeterminate result
	 */
	public EvaluationResult getAttribute(String contextPath,
			Node namespaceNode, URI type, String xpathVersion);

	/**
	 * Return the attribute representing the version of the standard used in
	 * this evaluation context
	 * 
	 * @return version the type of xacml standard used (1.0, 1.1, 2.0 or 3.0)
	 */
	public int getVersion();
}
