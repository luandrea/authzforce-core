/**
 *
 *  Copyright 2003-2004 Sun Microsystems, Inc. All Rights Reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 *    1. Redistribution of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *
 *    2. Redistribution in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *
 *  Neither the name of Sun Microsystems, Inc. or the names of contributors may
 *  be used to endorse or promote products derived from this software without
 *  specific prior written permission.
 *
 *  This software is provided "AS IS," without a warranty of any kind. ALL
 *  EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 *  ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 *  OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN MICROSYSTEMS, INC. ("SUN")
 *  AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE
 *  AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
 *  DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST
 *  REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 *  INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 *  OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE,
 *  EVEN IF SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 *  You acknowledge that this software is not designed or intended for use in
 *  the design, construction, operation or maintenance of any nuclear facility.
 */
package com.sun.xacml;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.DocumentBuilder;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.Attribute;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.AttributeValueType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Attributes;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.sun.xacml.attr.BagAttribute;
import com.sun.xacml.attr.DateAttribute;
import com.sun.xacml.attr.DateTimeAttribute;
import com.sun.xacml.attr.StringAttribute;
import com.sun.xacml.attr.TimeAttribute;
import com.sun.xacml.attr.xacmlv3.AttributeDesignator;
import com.sun.xacml.attr.xacmlv3.AttributeValue;
import com.sun.xacml.cond.xacmlv3.EvaluationResult;
import com.sun.xacml.finder.AttributeFinder;
import com.thalesgroup.appsec.util.Utils;
import com.thalesgroup.authzforce.core.PdpModelHandler;
import com.thalesgroup.authzforce.xacml.schema.XACMLCategory;

/**
 * A basic implementation of <code>EvaluationCtx</code> that is created from an XACML Request and
 * falls back on an AttributeFinder if a requested value isn't available in the Request.
 * <p>
 * Note that this class can do some optional caching for current date, time, and dateTime values
 * (defined by a boolean flag to the constructors). The XACML specification requires that these
 * values always be available, but it does not specify whether or not they must remain constant over
 * the course of an evaluation if the values are being generated by the PDP (if the values are
 * provided in the Request, then obviously they will remain constant). The default behavior is for
 * these environment values to be cached, so that (for example) the current time remains constant
 * over the course of an evaluation.
 * 
 * @since 1.2
 * @author Seth Proctor
 */
public class BasicEvaluationCtx implements EvaluationCtx
{
	// the finder to use if a value isn't in the request
	private AttributeFinder finder;
	Request request = null;
	Node requestRoot = null;

	// the 4 maps that contain the attribute data
	private final Map<String, Map<String, Set<Attribute>>> subjectMap = new HashMap<>();
	private Map<String, Set<Attribute>> resourceMap;
	private Map<String, Set<Attribute>> actionMap;
	private Map<String, Set<Attribute>> environmentMap;
	private Map<String, Set<Attribute>> customMap;

	// Attributes that needs to be included in the result
	private List<Attributes> includeInResults;

	// the resource and its scope
	private List<AttributeValue> resourceId;
	private int scope;

	// Version of the standard used: 1.0, 1.1, 2.0 or 3.0
	private int version;

	// the cached current date, time, and datetime, which we may or may
	// not be using depending on how this object was constructed
	private DateAttribute currentDate;
	private TimeAttribute currentTime;
	private DateTimeAttribute currentDateTime;
	private boolean useCachedEnvValues;

	/**
	 * Logger used for all classes
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(BasicEvaluationCtx.class);

	/**
	 * Constructs a new <code>BasicEvaluationCtx</code> based on the given request. The resulting
	 * context will cacheManager current date, time, and dateTime values so they remain constant for
	 * this evaluation.
	 * 
	 * @param request
	 *            the request
	 * 
	 * @throws ParsingException
	 *             if a required attribute is missing, or if there are any problems dealing with the
	 *             request data
	 * @throws UnknownIdentifierException
	 * @throws NumberFormatException
	 */
	public BasicEvaluationCtx(Request request) throws ParsingException, NumberFormatException, UnknownIdentifierException
	{
		this(request, null, true, PolicyMetaData.XACML_VERSION_3_0);
	}

	/**
	 * Constructs a new <code>BasicEvaluationCtx</code> based on the given request.
	 * 
	 * @param request
	 *            the request
	 * @param cacheEnvValues
	 *            whether or not to cacheManager the current time, date, and dateTime so they are
	 *            constant for the scope of this evaluation
	 * @param version
	 * 
	 * @throws ParsingException
	 *             if a required attribute is missing, or if there are any problems dealing with the
	 *             request data
	 * @throws UnknownIdentifierException
	 */
	public BasicEvaluationCtx(Request request, boolean cacheEnvValues, int version) throws ParsingException, UnknownIdentifierException
	{
		this(request, null, cacheEnvValues, version);
	}

	/**
	 * Constructs a new <code>BasicEvaluationCtx</code> based on the given request, and supports
	 * looking outside the original request for attribute values using the
	 * <code>AttributeFinder</code>. The resulting context will cacheManager current date, time, and
	 * dateTime values so they remain constant for this evaluation.
	 * 
	 * @param request
	 *            the request
	 * @param finder
	 *            an <code>AttributeFinder</code> to use in looking for attributes that aren't in
	 *            the request
	 * @param version
	 * 
	 * @throws ParsingException
	 *             if a required attribute is missing, or if there are any problems dealing with the
	 *             request data
	 * @throws UnknownIdentifierException
	 */
	public BasicEvaluationCtx(Request request, AttributeFinder finder, int version) throws ParsingException, UnknownIdentifierException
	{
		this(request, finder, true, version);
	}

	/**
	 * Constructs a new <code>BasicEvaluationCtx</code> based on the given request, and supports
	 * looking outside the original request for attribute values using the
	 * <code>AttributeFinder</code>.
	 * 
	 * @param request
	 *            the request
	 * @param finder
	 *            an <code>AttributeFinder</code> to use in looking for attributes that aren't in
	 *            the request
	 * @param cacheEnvValues
	 *            whether or not to cacheManager the current time, date, and dateTime so they are
	 *            constant for the scope of this evaluation
	 * @param version
	 * 
	 * @throws ParsingException
	 *             if a required attribute is missing, or if there are any problems dealing with the
	 *             request data
	 * @throws UnknownIdentifierException
	 */
	public BasicEvaluationCtx(Request request, AttributeFinder finder, boolean cacheEnvValues, int version) throws ParsingException,
			UnknownIdentifierException
	{

		this.request = request;

		// keep track of the finder
		this.finder = finder;

		// initialize the cached date/time values so it's clear we haven't
		// retrieved them yet
		this.useCachedEnvValues = cacheEnvValues;
		currentDate = null;
		currentTime = null;
		currentDateTime = null;

		this.version = version;
		actionMap = new HashMap<>();
		resourceMap = new HashMap<>();
		environmentMap = new HashMap<>();
		customMap = new HashMap<>();

		final List<Attributes> subjects = new ArrayList<>();
		for (Attributes myAttributes : request.getAttributes())
		{
			try
			{
				final XACMLCategory category = XACMLCategory.fromValue(myAttributes.getCategory());
				switch (category)
				{
					case XACML_1_0_SUBJECT_CATEGORY_ACCESS_SUBJECT:
					case XACML_1_0_SUBJECT_CATEGORY_CODEBASE:
					case XACML_1_0_SUBJECT_CATEGORY_INTERMEDIARY_SUBJECT:
					case XACML_1_0_SUBJECT_CATEGORY_RECIPIENT_SUBJECT:
					case XACML_1_0_SUBJECT_CATEGORY_REQUESTING_MACHINE:
						subjects.add(myAttributes);
						break;
					case XACML_3_0_RESOURCE_CATEGORY_RESOURCE:
						/* Searching for resource */
						setupResources(myAttributes.getAttributes());
						break;
					case XACML_3_0_ACTION_CATEGORY_ACTION:
						/* Searching for action */
						mapAttributes(myAttributes.getAttributes(), actionMap);
						break;
					case XACML_3_0_ENVIRONMENT_CATEGORY_ENVIRONMENT:
						// finally, set up the environment data, which is also generic
						mapAttributes(myAttributes.getAttributes(), environmentMap);
						break;
				}
			} catch (IllegalArgumentException e)
			{
				// Attribute category didn't match any known category so we store
				// the attributes in an custom list
				mapAttributes(myAttributes.getAttributes(), customMap);
			}

			// get the subjects, make sure they're correct, and setup tables
			setupSubjects(subjects);

			// Store attributes who needs to be included in the result
			for (Attribute attr : myAttributes.getAttributes())
			{
				storeAttrIncludeInResult(attr, myAttributes.getCategory());
			}
		}
	}

	/**
	 * This is quick helper function to provide a little structure for the subject attributes so we
	 * can search for them (somewhat) quickly. The basic idea is to have a map indexed by
	 * SubjectCategory that keeps Maps that in turn are indexed by id and keep the unique
	 * ctx.Attribute objects.
	 */
	private void setupSubjects(List<Attributes> subjects)
	{
		/*
		 * Having subject(s) is not mandatory per XACML spec, so this arg may be empty list.
		 */
		// now go through the subjects
		for (final Attributes subject : subjects)
		{
			final String category = subject.getCategory();
			final Map<String, Set<Attribute>> categoryMap;
			// see if we've already got a map for the category
			if (subjectMap.containsKey(category))
			{
				categoryMap = subjectMap.get(category);
			} else
			{
				categoryMap = new HashMap<>();
				subjectMap.put(category, categoryMap);
			}

			// iterate over the set of attributes of the subject
			final List<Attribute> subjectAttributes = subject.getAttributes();
			for (Attribute attr : subjectAttributes)
			{
				final String id = attr.getAttributeId();
				final Set<Attribute> attributes;
				if (categoryMap.containsKey(id))
				{
					// add to the existing set of Attributes w/this id
					attributes = categoryMap.get(id);
				} else
				{
					// this is the first Attr w/this id
					attributes = new HashSet<>();
					categoryMap.put(id, attributes);
				}

				attributes.add(attr);
			}
		}
	}

	/**
	 * This basically does the same thing that the other types need to do, except that we also look
	 * for a resource-id attribute, not because we're going to use, but only to make sure that it's
	 * actually there, and for the optional scope attribute, to see what the scope of the attribute
	 * is
	 * 
	 * @throws UnknownIdentifierException
	 */
	private void setupResources(List<Attribute> list) throws ParsingException, UnknownIdentifierException
	{
		mapAttributes(list, resourceMap);

		// make sure there resource-id attribute was included
		if (!resourceMap.containsKey(RESOURCE_ID))
		{
			LOGGER.error("Resource missing resource-id attr");
			throw new ParsingException("resource missing resource-id");
		}

		if (resourceId == null)
		{
			resourceId = new ArrayList<>();
		}
		resourceId.clear();
		for (Attribute Attribute : resourceMap.get(RESOURCE_ID))
		{
			resourceId.addAll(AttributeValue.convertFromJAXB(Attribute.getAttributeValues()));
		}

		// see if a resource-scope attribute was included
		if (resourceMap.containsKey(RESOURCE_SCOPE))
		{
			Set<Attribute> set = resourceMap.get(RESOURCE_SCOPE);

			// make sure there's only one value for resource-scope
			if (set.size() > 1)
			{
				LOGGER.error("Resource may contain only one resource-scope Attribute");
				throw new ParsingException("too many resource-scope attrs");
			}

			Attribute attr = set.iterator().next();
			AttributeValueType attrValue = attr.getAttributeValues().get(0);
			// scope must be a string, so throw an exception otherwise
			if (!attrValue.getDataType().equals(StringAttribute.identifier))
			{
				throw new ParsingException("scope attr must be a string");
			}

			String value = attrValue.getContent().get(0).toString();

			if (value.equals("Immediate"))
			{
				scope = SCOPE_IMMEDIATE;
			} else if (value.equals("Children"))
			{
				scope = SCOPE_CHILDREN;
			} else if (value.equals("Descendants"))
			{
				scope = SCOPE_DESCENDANTS;
			} else
			{
				LOGGER.error("Unknown scope type: {}", value);
				throw new ParsingException("invalid scope type: " + value);
			}
		} else
		{
			// by default, the scope is always Immediate
			scope = SCOPE_IMMEDIATE;
		}
	}

	private void storeAttrIncludeInResult(Attribute attr, String category)
	{
		if (includeInResults == null)
		{
			includeInResults = new ArrayList<>();
		}
		if (attr.isIncludeInResult())
		{
			boolean alreadyPresent = false;
			Attributes myAttr = new Attributes();
			myAttr.getAttributes().add(attr);
			myAttr.setCategory(category);
			for (Attributes attrs : includeInResults)
			{
				if (attrs.getCategory().equalsIgnoreCase(category))
				{
					alreadyPresent = true;
					attrs.getAttributes().add(attr);
					break;
				}
			}
			if (!alreadyPresent)
			{
				includeInResults.add(myAttr);
			}
		}
	}

	/**
	 * Generic routine for resource, attribute and environment attributes to build the lookup map
	 * for each. The Form is a Map that is indexed by the String form of the attribute ids, and that
	 * contains Sets at each entry with all attributes that have that id
	 */
	private static void mapAttributes(List<Attribute> attributes, Map<String, Set<Attribute>> map) throws ParsingException
	{
		for (Attribute attribute : attributes)
		{
			String id = attribute.getAttributeId();

			if (id == null)
			{
				throw new ParsingException("No AttributeId defined for attribute");
			}

			if (map.containsKey(id))
			{
				Set<Attribute> set = map.get(id);
				set.add(attribute);
			} else
			{
				Set<Attribute> set = new HashSet<>();
				set.add(attribute);
				map.put(id, set);
			}
		}
	}

	/**
	 * @return the request
	 */
	public Request getRequest()
	{
		return request;
	}

	/**
	 * Returns the resource scope of the request, which will be one of the three fields denoting
	 * Immediate, Children, or Descendants.
	 * 
	 * @return the scope of the resource in the request
	 */
	@Override
	public int getScope()
	{
		return scope;
	}

	/**
	 * Returns the resource named in the request as resource-id.
	 * 
	 * @return the resourceMap
	 */
	public Map<String, Set<Attribute>> getResourceMap()
	{
		return resourceMap;
	}

	/**
	 * Returns the resource named in the request as resource-id. Using resourceId as a pointer to
	 * the evaluated resource
	 * 
	 * @return the resource
	 */
	@Override
	public AttributeValue getResourceId()
	{
		if (resourceId != null && resourceId.size() >= 1)
		{
			return resourceId.get(0);
		}

		return null;
	}

	@Override
	public List<Attributes> getIncludeInResults()
	{
		return includeInResults;
	}

	/**
	 * Changes the value of the resource-id attribute in this context. This is useful when you have
	 * multiple resources (ie, a scope other than IMMEDIATE), and you need to keep changing only the
	 * resource-id to evaluate the different effective requests.
	 * 
	 * @param resourceId
	 *            the new resource-id value
	 */
	@Override
	public void setResourceId(AttributeValue resourceId)
	{
		this.resourceId.add(resourceId);
		// there will always be exactly one value for this attribute
		Set<Attribute> attrSet = resourceMap.get(RESOURCE_ID);
		Attribute attr = attrSet.iterator().next();
		AttributeValueType attrValue = attr.getAttributeValues().get(0);
		attrValue.getContent().clear();
		attrValue.getContent().add(resourceId.encode());
	}

	public void setResourceId(List<AttributeValue> resourceId)
	{
		for (AttributeValue avts : resourceId)
		{
			this.setResourceId(avts);
		}
	}

	/**
	 * Returns the value for the current time. The current time, current date, and current dateTime
	 * are consistent, so that they all represent the same moment. If this is the first time that
	 * one of these three values has been requested, and caching is enabled, then the three values
	 * will be resolved and stored.
	 * <p>
	 * Note that the value supplied here applies only to dynamically resolved values, not those
	 * supplied in the Request. In other words, this always returns a dynamically resolved value
	 * local to the PDP, even if a different value was supplied in the Request. This is handled
	 * correctly when the value is requested by its identifier.
	 * 
	 * @return the current time
	 */
	@Override
	public synchronized TimeAttribute getCurrentTime()
	{
		long millis = dateTimeHelper();

		if (useCachedEnvValues)
		{
			return currentTime;
		}

		return new TimeAttribute(new Date(millis));
	}

	/**
	 * Returns the value for the current date. The current time, current date, and current dateTime
	 * are consistent, so that they all represent the same moment. If this is the first time that
	 * one of these three values has been requested, and caching is enabled, then the three values
	 * will be resolved and stored.
	 * <p>
	 * Note that the value supplied here applies only to dynamically resolved values, not those
	 * supplied in the Request. In other words, this always returns a dynamically resolved value
	 * local to the PDP, even if a different value was supplied in the Request. This is handled
	 * correctly when the value is requested by its identifier.
	 * 
	 * @return the current date
	 */
	@Override
	public synchronized DateAttribute getCurrentDate()
	{
		long millis = dateTimeHelper();

		if (useCachedEnvValues)
		{
			return currentDate;
		}

		return new DateAttribute(new Date(millis));
	}

	/**
	 * Returns the value for the current dateTime. The current time, current date, and current
	 * dateTime are consistent, so that they all represent the same moment. If this is the first
	 * time that one of these three values has been requested, and caching is enabled, then the
	 * three values will be resolved and stored.
	 * <p>
	 * Note that the value supplied here applies only to dynamically resolved values, not those
	 * supplied in the Request. In other words, this always returns a dynamically resolved value
	 * local to the PDP, even if a different value was supplied in the Request. This is handled
	 * correctly when the value is requested by its identifier.
	 * 
	 * @return the current dateTime
	 */
	@Override
	public synchronized DateTimeAttribute getCurrentDateTime()
	{
		long millis = dateTimeHelper();

		if (useCachedEnvValues)
		{
			return currentDateTime;
		}

		return new DateTimeAttribute(new Date(millis));
	}

	/**
	 * Private helper that figures out if we need to resolve new values, and returns either the
	 * current moment (if we're not caching) or -1 (if we are caching)
	 */
	private long dateTimeHelper()
	{
		// if we already have current values, then we can stop (note this
		// always means that we're caching)
		if (currentTime != null)
		{
			return -1;
		}

		// get the current moment
		Date time = new Date();
		long millis = time.getTime();

		// if we're not caching then we just return the current moment
		if (!useCachedEnvValues)
		{
			return millis;
		}

		// we're caching, so resolve all three values, making sure
		// to use clean copies of the date object since it may be
		// modified when creating the attributes
		currentTime = new TimeAttribute(time);
		currentDate = new DateAttribute(new Date(millis));
		currentDateTime = new DateTimeAttribute(new Date(millis));

		return -1;
	}

	/**
	 * Returns attribute value(s) from the subject section of the request that have no issuer.
	 * 
	 * @param type
	 *            the type of the attribute value(s) to find
	 * @param id
	 *            the id of the attribute value(s) to find
	 * @param category
	 *            the category the attribute value(s) must be in
	 * 
	 * @return a result containing a bag either empty because no values were found or containing at
	 *         least one value, or status associated with an Indeterminate result
	 */
	@Override
	public EvaluationResult getSubjectAttribute(URI type, URI id, URI category)
	{
		return getSubjectAttribute(type, id, null, category);
	}

	/**
	 * Returns attribute value(s) from the subject section of the request.
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
	 * @return a result containing a bag either empty because no values were found or containing at
	 *         least one value, or status associated with an Indeterminate result
	 */
	@Override
	public EvaluationResult getSubjectAttribute(URI type, URI id, URI issuer, URI category)
	{
		final String catStr = category.toString();
		// This is the same as the other three lookups except that this
		// has an extra level of indirection that needs to be handled first
		/*
		 * Check if some subject attribute map already in current evaluation context for category
		 * arg (initially based on input Request)
		 */
		final Map<String, Set<Attribute>> oldAttrMap = subjectMap.get(catStr);
		/*
		 * Map to be actually updated with new attribute (different from old one only if no map
		 * found in current context for category arg)
		 */
		final Map<String, Set<Attribute>> newAttrMap;
		if (oldAttrMap == null)
		{
			// the request didn't have that category, so we should try asking
			// the attribute finder
			newAttrMap = new HashMap<>();
			subjectMap.put(catStr, newAttrMap);
		} else
		{
			newAttrMap = oldAttrMap;
		}

		return getGenericAttributes(type, id, issuer, newAttrMap, category, AttributeDesignator.SUBJECT_TARGET);
	}

	/**
	 * Returns attribute value(s) from the resource section of the request.
	 * 
	 * @param type
	 *            the type of the attribute value(s) to find
	 * @param id
	 *            the id of the attribute value(s) to find
	 * @param issuer
	 *            the issuer of the attribute value(s) to find or null
	 * 
	 * @return a result containing a bag either empty because no values were found or containing at
	 *         least one value, or status associated with an Indeterminate result
	 */
	@Override
	public EvaluationResult getResourceAttribute(URI type, URI id, URI issuer)
	{
		return getGenericAttributes(type, id, issuer, resourceMap, null, AttributeDesignator.RESOURCE_TARGET);
	}

	/**
	 * Returns attribute value(s) from the action section of the request.
	 * 
	 * @param type
	 *            the type of the attribute value(s) to find
	 * @param id
	 *            the id of the attribute value(s) to find
	 * @param issuer
	 *            the issuer of the attribute value(s) to find or null
	 * 
	 * @return a result containing a bag either empty because no values were found or containing at
	 *         least one value, or status associated with an Indeterminate result
	 */
	@Override
	public EvaluationResult getActionAttribute(URI type, URI id, URI issuer)
	{
		return getGenericAttributes(type, id, issuer, actionMap, null, AttributeDesignator.ACTION_TARGET);
	}

	/**
	 * Returns attribute value(s) from the environment section of the request.
	 * 
	 * @param type
	 *            the type of the attribute value(s) to find
	 * @param id
	 *            the id of the attribute value(s) to find
	 * @param issuer
	 *            the issuer of the attribute value(s) to find or null
	 * 
	 * @return a result containing a bag either empty because no values were found or containing at
	 *         least one value, or status associated with an Indeterminate result
	 */
	@Override
	public EvaluationResult getEnvironmentAttribute(URI type, URI id, URI issuer)
	{
		return getGenericAttributes(type, id, issuer, environmentMap, null, AttributeDesignator.ENVIRONMENT_TARGET);
	}

	/**
	 * Returns attribute value(s) from the environment section of the request.
	 * 
	 * @param type
	 *            the type of the attribute value(s) to find
	 * @param id
	 *            the id of the attribute value(s) to find
	 * @param issuer
	 *            the issuer of the attribute value(s) to find or null
	 * 
	 * @return a result containing a bag either empty because no values were found or containing at
	 *         least one value, or status associated with an Indeterminate result
	 */
	@Override
	public EvaluationResult getCustomAttribute(URI type, URI id, URI issuer)
	{
		return getGenericAttributes(type, id, issuer, customMap, null, -1);
	}

	/**
	 * Get attribute values from attributes but only with matching issuer and matching value
	 * datatype
	 * 
	 * @param issuer
	 *            attribute issuer
	 * @param type
	 *            attribute value datatype
	 * @param attrs
	 *            set of source attributes
	 * @return attribute values
	 * @throws ParsingException
	 *             if parsing of attribute values failed
	 */
	private static List<AttributeValue> getAttributeValues(Set<Attribute> attrs, String issuer, String type) throws ParsingException
	{
		final List<AttributeValue> resultList = new ArrayList<>();
		for (final Attribute attr : attrs)
		{
			// Check issuer: if issuer arg is defined but attribute issuer is not or not equal,
			// then NO MATCH, skip it.
			if (issuer != null && (attr.getIssuer() == null || !attr.getIssuer().equals(issuer)))
			{
				continue;
			}

			for (AttributeValueType attributeValue : attr.getAttributeValues())
			{
				// make sure the datatype matches
				if (attributeValue.getDataType().equals(type))
				{
					/*
					 * If we got here, then we found a match. So we want to pull out the value and
					 * put it in output list.
					 */
					try
					{
						/**
						 * FIXME: this conversion can be avoided if we simply change return type of
						 * Attribute#getAttributeValues() to List<AttributeValue> (no need to catch
						 * any exception in this case).
						 */
						resultList.add(AttributeValue.getInstance(attributeValue));
					} catch (Exception e)
					{
						throw new ParsingException("Error converting instance of " + AttributeValueType.class + " (JAXB) to " + AttributeValue.class,
								e);
					}
				}
			}
		}

		return resultList;
	}

	/**
	 * Helper function for the resource, action and environment methods to get an attribute. The
	 * input map is updated with the new attribute corresponding to input (id,type,issuer) if it was
	 * not already in the map
	 */
	private EvaluationResult getGenericAttributes(URI type, URI id, URI issuer, Map<String, Set<Attribute>> map, URI category, int designatorType)
	{
		/**
		 * Requirement #1: When the attribute finder is called via callHelper(), the result must be
		 * saved in the evaluation context to guarantee the same result/value next time it is used
		 * in the same context. At least 3 reasons:
		 * 
		 * 1. Performance optimization: calling attribute finder costs more than getting it from the
		 * context attribute map.
		 * 
		 * 2. ACCOUNTING/AUDIT: if you call the attribute finder more than once for the same
		 * attribute, you may have value changes from a call to another. If you log attribute values
		 * for accounting/audit reasons, which value do you log? This is critical to have the
		 * correct information in order to understand/investigate a posteriori why a given access
		 * was permitted/denied.
		 * 
		 * 3. Policy decision consistency: this is pretty close to the previous reason, expressed in
		 * more generic terms. If the value of a given attribute changed from one attribute finding
		 * to another when it is used/required more than once in the same decision request context,
		 * the PDP ends up evaluating different parts of the <PolicySet> based on different values
		 * of the same attribute, again, for the same decision request. And the longer the
		 * evaluation takes time to process, the more changes can occur, and the more inconsistent
		 * the evaluation will be.
		 */
		// try to find the id
		final String idStr = id.toString();
		final String issuerStr = (issuer == null) ? null : issuer.toString();
		final String typeStr = type.toString();
		/*
		 * Check if some attributes already (found) in current evaluation context (map) for
		 * attribute 'id' arg
		 */
		final Set<Attribute> oldAttrSet = map.get(idStr);
		final List<AttributeValue> attributeValues;
		/*
		 * newAttrSet will contain the existing attribute values if any in the current map (eval
		 * context), or the new attributes resulting from callHelper() (call to AttributeFinder) if
		 * no attribute found for input (id,type,issuer) in the map yet
		 */
		final Set<Attribute> newAttrSet;
		if (oldAttrSet == null)
		{
			// the request didn't have an attribute with that id, so no attribute values yet
			attributeValues = new ArrayList<>();
			newAttrSet = new HashSet<>();
			map.put(idStr, newAttrSet);
		} else
		{
			/*
			 * Found Attributes matching input attribute 'id' in current evaluation context. Now we
			 * have to select the ones matching the (data-)'type' and - if issuer != null - 'issuer'
			 * args only. Note: result attribute values may be empty as well if nothing matched.
			 */
			try
			{
				attributeValues = getAttributeValues(oldAttrSet, issuerStr, typeStr);
			} catch (ParsingException e)
			{
				throw new RuntimeException(e);
			}

			newAttrSet = oldAttrSet;
		}

		// see if we found any acceptable attributes
		if (!attributeValues.isEmpty())
		{
			// yes we found
			return new EvaluationResult(new BagAttribute(type, attributeValues));
		}

		// No acceptable attribute found matching type/issuer... so ask the finder via callHelper()
		LOGGER.debug("Attribute id='{}' not in request context... querying AttributeFinder", id);
		/**
		 * <code>newAttrSet</code> is linked to the eval context map. So this context is updated by
		 * storing the new value from callHelper() (call to AttributeFInder) in <code>newAttrSet</code>.
		 */
		final EvaluationResult result = callHelper(type, id, issuer, category, designatorType);
		final AttributeValueType resultAttrVal = result.getAttributeValue();
		if (resultAttrVal instanceof BagAttribute)
		{
			final BagAttribute bagAttribute = (BagAttribute) resultAttrVal;
			for (final AttributeValue attributeValue : bagAttribute.getValues())
			{
				// FIXME: do something better than creating one attribute per resulting value
				final Attribute attribute = new Attribute();
				attribute.setAttributeId(idStr);
				attribute.setIssuer(issuerStr);
				attribute.getAttributeValues().add(attributeValue);
				newAttrSet.add(attribute);
			}

			return result;
		}

		throw new IllegalArgumentException("CallHelper didn't return BagAttribute");
	}

	/**
	 * Private helper that calls the finder if it's non-null, or else returns an empty bag
	 */
	private EvaluationResult callHelper(URI type, URI id, URI issuer, URI category, int adType)
	{
		if (finder != null)
		{
			return finder.findAttribute(type, id, issuer, category, this, adType);
		}

		LOGGER.warn("Context tried to invoke AttributeFinder but was not configured with one");

		return new EvaluationResult(BagAttribute.createEmptyBag(type));
	}

	/**
	 * Returns the attribute value(s) retrieved using the given XPath expression.
	 * 
	 * @param contextPath
	 *            the XPath expression to search
	 * @param namespaceNode
	 *            the DOM node defining namespace mappings to use, or null if mappings come from the
	 *            context root
	 * @param type
	 *            the type of the attribute value(s) to find
	 * @param xpathVersion
	 *            the version of XPath to use
	 * 
	 * @return a result containing a bag either empty because no values were found or containing at
	 *         least one value, or status associated with an Indeterminate result
	 */
	@Override
	public EvaluationResult getAttribute(String contextPath, Node namespaceNode, URI type, String xpathVersion)
	{
		if (finder != null)
		{
			return finder.findAttribute(contextPath, namespaceNode, type, this, xpathVersion);
		}

		LOGGER.warn("Context tried to invoke AttributeFinder but was " + "not configured with one");

		return new EvaluationResult(BagAttribute.createEmptyBag(type));
	}

	@Override
	public Node getRequestRoot()
	{
		final DocumentBuilder docBuilder = Utils.THREAD_LOCAL_NS_AWARE_DOC_BUILDER.get();
		try
		{
			if (requestRoot == null)
			{
				Marshaller m = PdpModelHandler.XACML_3_0_JAXB_CONTEXT.createMarshaller();
				Document doc = docBuilder.newDocument();
				m.marshal(request, doc);
				requestRoot = doc.getDocumentElement();
			}
		} catch (JAXBException ex)
		{
			throw new RuntimeException(ex);
		} finally
		{
			docBuilder.reset();
		}

		return requestRoot;
	}

	@Override
	public int getVersion()
	{
		return version;
	}

	private Map<String, Object> updatableProperties = new HashMap<>();

	@Override
	public Object get(String key)
	{
		return updatableProperties.get(key);
	}

	@Override
	public boolean containsKey(String key)
	{
		return updatableProperties.containsKey(key);
	}

	@Override
	public void put(String key, Object val)
	{
		updatableProperties.put(key, val);
	}

	@Override
	public Object remove(String key)
	{
		return updatableProperties.remove(key);
	}
}
