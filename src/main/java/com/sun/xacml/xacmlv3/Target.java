/**
 * Copyright (C) 2011-2015 Thales Services SAS - All rights reserved.
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
package com.sun.xacml.xacmlv3;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.Marshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.xacml.DOMHelper;
import com.sun.xacml.EvaluationCtx;
import com.sun.xacml.Indenter;
import com.sun.xacml.MatchResult;
import com.sun.xacml.ParsingException;
import com.sun.xacml.PolicyMetaData;
import com.thalesgroup.authzforce.core.PdpModelHandler;

/**
 * Represents the TargetType XML type in XACML. This also stores several other
 * XML types: Subjects, Resources, Actions, and Environments (in XACML 2.0 and
 * later). The target is used to quickly identify whether the parent element (a
 * policy set, policy, or rule) is applicable to a given request.
 * 
 * @since 1.0
 * @author Seth Proctor
 */
public class Target extends oasis.names.tc.xacml._3_0.core.schema.wd_17.Target {

	// the version of XACML of the policy containing this target
	// private int xacmlVersion = XACMLVersion.V3_0.value();

	/**
	 * Logger used for all classes
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(Target.class);

	public Target(AnyOf anyof, int version) {
		anyOves = new ArrayList<oasis.names.tc.xacml._3_0.core.schema.wd_17.AnyOf>();
		// this.xacmlVersion = version;
		this.anyOves.add(anyof);
	}

	public Target(AnyOf anyof) {
		anyOves = new ArrayList<oasis.names.tc.xacml._3_0.core.schema.wd_17.AnyOf>();
		this.anyOves.add(anyof);
	}

	public Target(List<AnyOf> anyof, int version) {
		anyOves = new ArrayList<oasis.names.tc.xacml._3_0.core.schema.wd_17.AnyOf>();
		// this.xacmlVersion = version;
		this.anyOves.addAll(anyof);
	}

	public Target(List<AnyOf> anyof) {
		anyOves = new ArrayList<oasis.names.tc.xacml._3_0.core.schema.wd_17.AnyOf>();
		this.anyOves.addAll(anyof);
	}

	/**
	 * Creates Target handler from Target element as defined in OASIS XACML
	 * model
	 * 
	 * @param targetElement
	 * @param metadata
	 * @throws ParsingException
	 *             if Target element is invalid
	 */
	public Target(
			oasis.names.tc.xacml._3_0.core.schema.wd_17.Target targetElement,
			PolicyMetaData metadata) throws ParsingException {
		anyOves = new ArrayList<>();
		// this.xacmlVersion = version;
		for (oasis.names.tc.xacml._3_0.core.schema.wd_17.AnyOf anyOfElement : targetElement
				.getAnyOves()) {
			final AnyOf anyOf = AnyOf.getInstance(anyOfElement, metadata);
			this.anyOves.add(anyOf);
		}
	}

	/**
	 * Creates a <code>Target</code> by parsing a node.
	 * 
	 * @param root
	 *            the node to parse for the <code>Target</code>
	 * @param metaData
	 * @return a new <code>Target</code> constructed by parsing
	 * 
	 * @throws ParsingException
	 *             if the DOM node is invalid
	 */
	public static Target getInstance(Node root, PolicyMetaData metaData)
			throws ParsingException {
		List<AnyOf> anyOf = new ArrayList<>();

		int version = metaData.getXACMLVersion();
		NodeList myChildren = root.getChildNodes();

		for (int i = 0; i < myChildren.getLength(); i++) {
			Node child = myChildren.item(i);
			if ("AnyOf".equals(DOMHelper.getLocalName(child))) {
				anyOf.add(AnyOf.getInstance(child, metaData));
			}
		}

		return new Target(anyOf, version);
	}

	/**
	 * Returns whether or not this <code>Target</code> matches any request. If
	 * the list of anyOf elements is empty it means that the target match any
	 * context.
	 * 
	 * @param version
	 *            the version of the context
	 * 
	 * @return true if this Target matches any request, false otherwise
	 */
	public boolean matchesAny(int version) {
		boolean matchAny = true;
		for (oasis.names.tc.xacml._3_0.core.schema.wd_17.AnyOf anyOf : this.anyOves) {
			for (oasis.names.tc.xacml._3_0.core.schema.wd_17.AllOf allOf : anyOf
					.getAllOves()) {
				matchAny = allOf.getMatches().isEmpty();
			}
		}

		return matchAny;
	}

	/**
	 * Determines whether this <code>Target</code> matches the input request
	 * (whether it is applicable). If any of the AnyOf doesn't match the request
	 * context so it's a NO_MATCH result. Here is the table shown in the
	 * specification: 
	 * <code>
	 * 		<AnyOf> values 				<Target> value
	 * 		All “Match”					“Match”
	 * 		At Least one "No Match"		“No Match”
	 * 		Otherwise					“Indeterminate”
	 * </code>
	 * 
	 * @param context
	 *            the representation of the request
	 * 
	 * @return the result of trying to match the {@link oasis.names.tc.xacml._3_0.core.schema.wd_17.AnyOf} and the request
	 */
	public MatchResult match(EvaluationCtx context) {
		MatchResult result = new MatchResult(MatchResult.INDETERMINATE);

		// before matching, see if this target matches any request
		if (matchesAny(context.getVersion())) {
			return new MatchResult(MatchResult.MATCH);
		}

		for (oasis.names.tc.xacml._3_0.core.schema.wd_17.AnyOf jaxbAnyOf : this.getAnyOves()) {
			AnyOf anyOfTmp = (AnyOf) jaxbAnyOf;
			result = anyOfTmp.match(context);
			// We check that the Match element is a Match. Otherwise we return
			// the result
			if (result == null || result.getResult() != MatchResult.MATCH) {
				return result;
			}
		}

		// if we got here, then everything matched
		return result;
	}

	/**
	 * Encodes this <code>Target</code> into its XML representation and writes
	 * this encoding to the given <code>OutputStream</code> with no indentation.
	 * 
	 * @param output
	 *            a stream into which the XML-encoded data is written
	 */
	public void encode(OutputStream output) {
		encode(output, new Indenter(0));
	}

	/**
	 * Encodes this <code>Target</code> into its XML representation and writes
	 * this encoding to the given <code>OutputStream</code> with indentation.
	 * 
	 * @param output
	 *            a stream into which the XML-encoded data is written
	 * @param indenter
	 *            an object that creates indentation strings
	 */
	public void encode(OutputStream output, Indenter indenter) {
		PrintStream out = new PrintStream(output);
		try {
			Marshaller u = PdpModelHandler.XACML_3_0_JAXB_CONTEXT
					.createMarshaller();
			u.marshal(this, out);
		} catch (Exception e) {
			LOGGER.error("Error Marshalling Target", e);
		}
	}

}
