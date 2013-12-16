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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.validation.Schema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.sun.xacml.attr.AttributeFactory;
import com.sun.xacml.attr.AttributeFactoryProxy;
import com.sun.xacml.attr.AttributeProxy;
import com.sun.xacml.attr.BaseAttributeFactory;
import com.sun.xacml.attr.StandardAttributeFactory;
import com.sun.xacml.combine.BaseCombiningAlgFactory;
import com.sun.xacml.combine.CombiningAlgFactory;
import com.sun.xacml.combine.CombiningAlgFactoryProxy;
import com.sun.xacml.combine.CombiningAlgorithm;
import com.sun.xacml.combine.StandardCombiningAlgFactory;
import com.sun.xacml.cond.BaseFunctionFactory;
import com.sun.xacml.cond.BasicFunctionFactoryProxy;
import com.sun.xacml.cond.Function;
import com.sun.xacml.cond.FunctionFactory;
import com.sun.xacml.cond.FunctionFactoryProxy;
import com.sun.xacml.cond.FunctionProxy;
import com.sun.xacml.cond.StandardFunctionFactory;
import com.sun.xacml.cond.cluster.FunctionCluster;
import com.sun.xacml.finder.AttributeFinder;
import com.sun.xacml.finder.PolicyFinder;
import com.sun.xacml.finder.ResourceFinder;
import com.thalesgroup.authzforce.BindingUtility;

/**
 * This class supports run-time loading of configuration data. It loads the
 * configurations from an XML file that conforms to the configuration schema. By
 * design this class does not get used automatically, nor does it change the
 * state of the system directly. A programmer must choose to support this
 * mechanism in their program, and then must explicitly use loaded elements.
 * This way, the programmer still has full control over their security model,
 * but also has the convenience of re-using a common configuration mechanism.
 * See http://sunxacml.sourceforge.net/schema/config-0.4.xsd for the valid
 * schema.
 * <p>
 * Note that becuase this doesn't tie directly into the rest of the code, you
 * are still free to design your own run-time configuration mechanisms. This is
 * simply provided as a convenience, and so that all programmers can start from
 * a common point.
 * 
 * @since 1.2
 * @author Seth Proctor
 */
public class ConfigurationStore {

	/**
	 * Property used to specify the configuration file.
	 */
	public static final String PDP_CONFIG_PROPERTY = "com.sun.xacml.PDPConfigFile";

	// pdp elements
	private PDPConfig defaultPDPConfig;
	private HashMap pdpConfigMap;

	// attribute factory elements
	private AttributeFactory defaultAttributeFactory;
	private HashMap attributeMap;

	// combining algorithm factory elements
	private CombiningAlgFactory defaultCombiningFactory;
	private HashMap combiningMap;

	// function factory elements
	private FunctionFactoryProxy defaultFunctionFactoryProxy;
	private HashMap functionMap;

	private HashMap cacheMap;

	// the classloader we'll use for loading classes
	private ClassLoader loader;

	/**
	 * Logger used for all classes
	 */
	private static final Logger LOGGER = LoggerFactory
			.getLogger(ConfigurationStore.class);

	/**
	 * Default constructor. This constructor uses the
	 * <code>PDP_CONFIG_PROPERTY</code> property to load the configuration. If
	 * the property isn't set, if it names a file that can't be accessed, or if
	 * the file is invalid, then an exception is thrown.
	 * 
	 * @throws ParsingException
	 *             if anything goes wrong during the parsing of the
	 *             configuration file, the class loading, or the factory and pdp
	 *             setup
	 */
	public ConfigurationStore() throws ParsingException {
		String configFile = System.getProperty(PDP_CONFIG_PROPERTY);

		// make sure that the right property was set
		if (configFile == null) {
			LOGGER.error("A property defining a config file was expected, "
					+ "but none was provided");

			throw new ParsingException("Config property " + PDP_CONFIG_PROPERTY
					+ " needs to be set");
		}

		try {
			setupConfig(new File(configFile), null, null);
		} catch (ParsingException pe) {
			LOGGER.error("Runtime config file couldn't be loaded"
					+ " so no configurations will be available", pe);
			throw pe;
		}
	}

	/**
	 * Constructor that explicitly specifies the configuration file to load.
	 * This is useful if your security model doesn't allow the use of
	 * properties, if you don't want to use a property to specify a
	 * configuration file, or if you want to use more then one configuration
	 * file. If the file can't be accessed, or if the file is invalid, then an
	 * exception is thrown.
	 * 
	 * @throws ParsingException
	 *             if anything goes wrong during the parsing of the
	 *             configuration file, the class loading, or the factory and pdp
	 *             setup
	 * 
	 * @deprecated Use ConfigurationStore(File configFile, JAXBContext jaxbctx,
	 *             Schema schema) instead
	 */
	public ConfigurationStore(File configFile) throws ParsingException {
		this(configFile, null, null);
	}

	/**
	 * Constructor that explicitly specifies the configuration file to load.
	 * This is useful if your security model doesn't allow the use of
	 * properties, if you don't want to use a property to specify a
	 * configuration file, or if you want to use more then one configuration
	 * file. If the file can't be accessed, or if the file is invalid, then an
	 * exception is thrown.
	 * 
	 * @param configFile
	 *            PDP XML configuration file
	 * @param jaxbctx
	 *            JAXB context for unmarshalling XML configuration of the finder
	 *            modules/classes with configuration enclosed in 'xml' tag. Such
	 *            classes must have at least one constructor with argument types
	 *            in this order: org.w3c.dom.Element, Javax.xml.bind.JAXContext,
	 *            javax.xml.validation.Schema. This argument may be null if not
	 *            using JAXB for loading module configurations (i.e. not
	 *            enclosed in 'xml'tag). Example:
	 * 
	 *            <pre>
	 * {@code 
	 * <attributeFinderModule class="com.example.MyAttributeFinderModule">
	 *   <xml>
	 *     <az:attributeFinder xmlns:az="http://thalesgroup.com/authz/model/3.0" ...>...</az>
	 *   </xml>
	 * </attributeFinderModule>
	 * 	}
	 * </pre>
	 * @param schema
	 *            XML schema for validating XML configurations of finder modules
	 *            (enclosed in 'xml' tag). This argument is optional: use a null
	 *            value to cancel schema validation.
	 * 
	 * @throws ParsingException
	 *             if anything goes wrong during the parsing of the
	 *             configuration file, the class loading, or the factory and pdp
	 *             setup
	 */
	public ConfigurationStore(File configFile, JAXBContext jaxbctx,
			Schema schema) throws ParsingException {
		try {
			setupConfig(configFile, jaxbctx, schema);
		} catch (ParsingException pe) {
			LOGGER.error("Runtime config file couldn't be loaded"
					+ " so no configurations will be available", pe);
			throw pe;
		}
	}

	/**
	 * TODO: Use JAXB schema model
	 * 
	 * Private helper function used by both constructors to actually load the
	 * configuration data. This is the root of several private methods used to
	 * setup all the pdps and factories.
	 * 
	 * @param jaxbctx
	 *            JAXB context for unmarshalling XML configuration of the finder
	 *            modules/classes with configuration enclosed in 'xml' tag. Such
	 *            classes must have at least one constructor with argument types
	 *            in this order: org.w3c.dom.Element, Javax.xml.bind.JAXContext,
	 *            javax.xml.validation.Schema. This argument may be null if not
	 *            using JAXB for loading module configurations (i.e. not
	 *            enclosed in 'xml'tag).
	 * 
	 * @param schema
	 *            schema for validating XML configuration of PDP (finder)
	 *            modules
	 */
	private void setupConfig(File configFile, JAXBContext jaxbctx, Schema schema)
			throws ParsingException {
		LOGGER.info("Loading runtime configuration");

		// load our classloader
		loader = getClass().getClassLoader();

		// get the root node from the configuration file
		Node root = getRootNode(configFile);

		// initialize all the maps
		pdpConfigMap = new HashMap();
		attributeMap = new HashMap();
		combiningMap = new HashMap();
		functionMap = new HashMap();
		cacheMap = new HashMap();

		// get the default names
		NamedNodeMap attrs = root.getAttributes();
		String defaultPDP = attrs.getNamedItem("defaultPDP").getNodeValue();
		String defaultAF = getDefaultFactory(attrs, "defaultAttributeFactory");
		String defaultCAF = getDefaultFactory(attrs,
				"defaultCombiningAlgFactory");
		String defaultFF = getDefaultFactory(attrs, "defaultFunctionFactory");

		// loop through all the root-level elements, for each one getting its
		// name and then loading the right kind of element
		NodeList children = root.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			String childName = child.getNodeName();
			String elementName = null;

			// get the element's name
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				elementName = child.getAttributes().getNamedItem("name")
						.getNodeValue();
			}

			// see if this is a pdp or a factory, and load accordingly,
			// putting the new element into the respective map...make sure
			// that we're never loading something with the same name twice
			if (childName.equals("pdp")) {
				LOGGER.info("Loading PDP: {}", elementName);
				if (pdpConfigMap.containsKey(elementName)) {
					throw new ParsingException("more that one pdp with "
							+ "name \"" + elementName + "\"");
				}
				pdpConfigMap.put(elementName,
						parsePDPConfig(child, configFile, jaxbctx, schema));
			} else if (childName.equals("attributeFactory")) {
				LOGGER.info("Loading AttributeFactory: {}", elementName);
				if (attributeMap.containsKey(elementName)) {
					throw new ParsingException("more that one "
							+ "attributeFactory with name " + elementName
							+ "\"");
				}
				attributeMap.put(elementName, parseAttributeFactory(child));
			} else if (childName.equals("combiningAlgFactory")) {
				LOGGER.info("Loading CombiningAlgFactory: {}", elementName);
				if (combiningMap.containsKey(elementName)) {
					throw new ParsingException("more that one "
							+ "combiningAlgFactory with " + "name \""
							+ elementName + "\"");
				}
				combiningMap.put(elementName, parseCombiningAlgFactory(child));
			} else if (childName.equals("functionFactory")) {
				LOGGER.info("Loading FunctionFactory: {}", elementName);
				if (functionMap.containsKey(elementName)) {
					throw new ParsingException("more that one functionFactory"
							+ " with name \"" + elementName + "\"");
				}
				functionMap.put(elementName, parseFunctionFactory(child));
			}
		}

		// finally, extract the default elements
		defaultPDPConfig = (PDPConfig) (pdpConfigMap.get(defaultPDP));

		defaultAttributeFactory = (AttributeFactory) (attributeMap
				.get(defaultAF));
		if (defaultAttributeFactory == null) {
			try {
				defaultAttributeFactory = AttributeFactory
						.getInstance(defaultAF);
			} catch (Exception e) {
				throw new ParsingException("Unknown AttributeFactory", e);
			}
		}

		defaultCombiningFactory = (CombiningAlgFactory) (combiningMap
				.get(defaultCAF));
		if (defaultCombiningFactory == null) {
			try {
				defaultCombiningFactory = CombiningAlgFactory
						.getInstance(defaultCAF);
			} catch (Exception e) {
				throw new ParsingException("Unknown CombininAlgFactory", e);
			}
		}

		defaultFunctionFactoryProxy = (FunctionFactoryProxy) (functionMap
				.get(defaultFF));
		if (defaultFunctionFactoryProxy == null) {
			try {
				defaultFunctionFactoryProxy = FunctionFactory
						.getInstance(defaultFF);
			} catch (Exception e) {
				throw new ParsingException("Unknown FunctionFactory", e);
			}
		}
	}

	/**
	 * Private helper that gets a default factory identifier, or fills in the
	 * default value if no identifier is provided.
	 */
	private String getDefaultFactory(NamedNodeMap attrs, String factoryName) {
		Node node = attrs.getNamedItem(factoryName);
		if (node != null) {
			return node.getNodeValue();
		} else {
			return PolicyMetaData.XACML_1_0_IDENTIFIER;
		}
	}

	/**
	 * Private helper that parses the file and sets up the DOM tree.
	 */
	private Node getRootNode(File configFile) throws ParsingException {
		final DocumentBuilder threadLocalDocBuilder = BindingUtility
				.getDocumentBuilder(true);

		Document doc = null;
		try {
			doc = threadLocalDocBuilder.parse(configFile);
		} catch (IOException ioe) {
			throw new ParsingException("failed to load the file ", ioe);
		} catch (SAXException saxe) {
			throw new ParsingException("error parsing the XML tree", saxe);
		} catch (IllegalArgumentException iae) {
			throw new ParsingException("no data to parse", iae);
		}

		Element root = doc.getDocumentElement();

		if (!root.getTagName().equals("config")) {
			throw new ParsingException("unknown document type: "
					+ root.getTagName());
		}

		return root;
	}

	/**
	 * Private helper that handles the pdp elements.
	 * 
	 * TODO: use XML schema and JAXB for configuration parsing to clean the code
	 * 
	 * @param jaxbctx
	 *            JAXB context for unmarshalling XML configuration of the finder
	 *            modules/classes with configuration enclosed in 'xml' tag. Such
	 *            classes must have at least one constructor with argument types
	 *            in this order: org.w3c.dom.Element, Javax.xml.bind.JAXContext,
	 *            javax.xml.validation.Schema. This argument may be null if not
	 *            using JAXB for loading module configurations (i.e. not
	 *            enclosed in 'xml'tag).
	 * @param schema
	 *            XML schema for validating configurations of PDP modules (esp.
	 *            finders), when enclosed with 'xml' tag
	 */
	private PDPConfig parsePDPConfig(Node root, File configFile,
			JAXBContext jaxbctx, Schema schema) throws ParsingException {
		ArrayList attrModules = new ArrayList();
		ArrayList policyModules = new ArrayList();
		ArrayList rsrcModules = new ArrayList();
		ArrayList cacheModules = new ArrayList();

		// go through all elements of the pdp, loading the specified modules
		NodeList children = root.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			String name = child.getNodeName();

			if (name.equals("policyFinderModule")) {
				policyModules.add(loadClass("module", child, jaxbctx, schema));
			} else if (name.equals("attributeFinderModule")) {
				attrModules.add(loadClass("module", child, jaxbctx, schema));
			} else if (name.equals("resourceFinderModule")) {
				// TODO:
				rsrcModules.add(loadClass("module", child, jaxbctx, schema));
			} else if (name.equals("cache")) {
				cacheModules.add(loadClass("module", child));
			}
		}

		// after loading the modules, use the collections to setup a
		// PDPConfig based on this pdp element

		AttributeFinder attrFinder = new AttributeFinder();
		attrFinder.setModules(attrModules);

		/*
		 * Sets the policyFinder's base directory for finder modules to resolve
		 * relative policy file paths as relative to the same parent directory
		 * of the PDP config file
		 */
		PolicyFinder policyFinder = new PolicyFinder(configFile.getParentFile());
		policyFinder.setModules(policyModules);

		ResourceFinder rsrcFinder = new ResourceFinder();
		rsrcFinder.setModules(rsrcModules);

		CacheManager cacheManager = cacheModules.isEmpty() ? null
				: (CacheManager) cacheModules.get(0);

		// CacheManager cacheManager = CacheManager.getInstance();
		// return new PDPConfig(attrFinder, policyFinder, rsrcFinder);

		return new PDPConfig(attrFinder, policyFinder, rsrcFinder, cacheManager);
	}

	/**
	 * Private helper that handles the attributeFactory elements.
	 */
	private AttributeFactory parseAttributeFactory(Node root)
			throws ParsingException {
		AttributeFactory factory = null;

		// check if we're starting with the standard factory setup
		if (useStandard(root, "useStandardDatatypes")) {
			LOGGER.info("Starting with standard Datatypes");

			factory = StandardAttributeFactory.getNewFactory();
		} else {
			factory = new BaseAttributeFactory();
		}

		// now look for all datatypes specified for this factory, adding
		// them as we go
		NodeList children = root.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);

			if (child.getNodeName().equals("datatype")) {
				// a datatype is a class with an identifier
				String identifier = child.getAttributes()
						.getNamedItem("identifier").getNodeValue();
				AttributeProxy proxy = (AttributeProxy) (loadClass("datatype",
						child));

				try {
					factory.addDatatype(identifier, proxy);
				} catch (IllegalArgumentException iae) {
					throw new ParsingException("duplicate datatype: "
							+ identifier, iae);
				}
			}
		}

		return factory;
	}

	/**
	 * Private helper that handles the combiningAlgFactory elements.
	 */
	private CombiningAlgFactory parseCombiningAlgFactory(Node root)
			throws ParsingException {
		CombiningAlgFactory factory = null;

		// check if we're starting with the standard factory setup
		if (useStandard(root, "useStandardAlgorithms")) {
			LOGGER.info("Starting with standard Combining Algorithms");

			factory = StandardCombiningAlgFactory.getNewFactory();
		} else {
			factory = new BaseCombiningAlgFactory();
		}

		// now look for all algorithms specified for this factory, adding
		// them as we go
		NodeList children = root.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);

			if (child.getNodeName().equals("algorithm")) {
				// an algorithm is a simple class element
				CombiningAlgorithm alg = (CombiningAlgorithm) (loadClass(
						"algorithm", child));
				try {
					factory.addAlgorithm(alg);
				} catch (IllegalArgumentException iae) {
					throw new ParsingException("duplicate combining "
							+ "algorithm: " + alg.getIdentifier().toString(),
							iae);
				}
			}
		}

		return factory;
	}

	/**
	 * Private helper that handles the functionFactory elements. This one is a
	 * little more complex than the other two factory helper methods, since it
	 * consists of three factories (target, condition, and general).
	 */
	private FunctionFactoryProxy parseFunctionFactory(Node root)
			throws ParsingException {
		FunctionFactoryProxy proxy = null;
		FunctionFactory generalFactory = null;
		FunctionFactory conditionFactory = null;
		FunctionFactory targetFactory = null;

		// check if we're starting with the standard factory setup, and
		// make sure that the proxy is pre-configured
		if (useStandard(root, "useStandardFunctions")) {
			LOGGER.info("Starting with standard Functions");

			proxy = StandardFunctionFactory.getNewFactoryProxy();

			targetFactory = proxy.getTargetFactory();
			conditionFactory = proxy.getConditionFactory();
			generalFactory = proxy.getGeneralFactory();
		} else {
			generalFactory = new BaseFunctionFactory();
			conditionFactory = new BaseFunctionFactory(generalFactory);
			targetFactory = new BaseFunctionFactory(conditionFactory);

			proxy = new BasicFunctionFactoryProxy(targetFactory,
					conditionFactory, generalFactory);
		}

		// go through and load the three sections, putting the loaded
		// functions into the appropriate factory
		NodeList children = root.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			String name = child.getNodeName();

			if (name.equals("target")) {
				LOGGER.info("Loading [TARGET] functions");
				functionParserHelper(child, targetFactory);
			} else if (name.equals("condition")) {
				LOGGER.info("Loading [CONDITION] functions");
				functionParserHelper(child, conditionFactory);
			} else if (name.equals("general")) {
				LOGGER.info("Loading [GENERAL] functions");
				functionParserHelper(child, generalFactory);
			}
		}

		return proxy;
	}

	/**
	 * Private helper used by the function factory code to load a specific
	 * target, condition, or general section.
	 */
	private void functionParserHelper(Node root, FunctionFactory factory)
			throws ParsingException {
		// go through all elements in the section
		NodeList children = root.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			String name = child.getNodeName();

			if (name.equals("function")) {
				// a function section is a simple class element
				Function function = (Function) (loadClass("function", child));
				try {
					factory.addFunction(function);
				} catch (IllegalArgumentException iae) {
					throw new ParsingException("duplicate function", iae);
				}
			} else if (name.equals("abstractFunction")) {
				// an abstract function is a class with an identifier
				URI identifier = null;
				try {
					identifier = new URI(child.getAttributes()
							.getNamedItem("identifier").getNodeValue());
				} catch (URISyntaxException urise) {
					throw new ParsingException("invalid function identifier",
							urise);
				}

				FunctionProxy proxy = (FunctionProxy) (loadClass(
						"abstract function", child));
				try {
					factory.addAbstractFunction(proxy, identifier);
				} catch (IllegalArgumentException iae) {
					throw new ParsingException("duplicate abstract function",
							iae);
				}
			} else if (name.equals("functionCluster")) {
				// a cluster is a class that will give us a collection of
				// functions that need to be added one by one into the factory
				FunctionCluster cluster = (FunctionCluster) (loadClass(
						"function cluster", child));

				Iterator it = cluster.getSupportedFunctions().iterator();
				while (it.hasNext()) {
					try {
						factory.addFunction((Function) (it.next()));
					} catch (IllegalArgumentException iae) {
						throw new ParsingException("duplicate function", iae);
					}
				}
			}
		}
	}

	private Object loadClass(String prefix, Node root) throws ParsingException {
		return loadClass(prefix, root, null, null);
	}

	/**
	 * Private helper that is used by all the code to load an instance of the
	 * given class...this assumes that the class is in the classpath, both for
	 * simplicity and for stronger security
	 * 
	 * @param jaxbctx
	 *            JAXB context for unmarshalling XML configuration of the finder
	 *            modules/classes with configuration enclosed in 'xml' tag. Such
	 *            classes must have at least one constructor with argument types
	 *            in this order: org.w3c.dom.Element, Javax.xml.bind.JAXContext,
	 *            javax.xml.validation.Schema. This argument may be null if not
	 *            using JAXB for loading module configurations (i.e. not
	 *            enclosed in 'xml'tag).
	 * 
	 * @param schema
	 *            XML schema for validating XML elements enclosed with node
	 *            named 'xml' (configuration of PDP modules), argument is
	 *            optional (may be null)
	 */
	private Object loadClass(String prefix, Node root, JAXBContext jaxbctx,
			Schema schema) throws ParsingException {
		// get the name of the class
		String className = root.getAttributes().getNamedItem("class")
				.getNodeValue();

		LOGGER.info("Loading [ {}: {} ]", prefix, className);

		// load the given class using the local classloader
		Class c = null;
		try {
			c = loader.loadClass(className);
		} catch (ClassNotFoundException cnfe) {
			throw new ParsingException("couldn't load class " + className, cnfe);
		}
		Object instance = null;

		// figure out if there are any parameters to the constructor
		if (!root.hasChildNodes()) {
			// we're using a null constructor, so this is easy
			try {
				instance = c.newInstance();
			} catch (InstantiationException ie) {
				throw new ParsingException("couldn't instantiate " + className
						+ " with empty constructor", ie);
			} catch (IllegalAccessException iae) {
				throw new ParsingException("couldn't get access to instance "
						+ "of " + className, iae);
			}
		} else {
			// parse the arguments to the constructor
			List args = null;
			try {
				// JAXB context and schema will be used in args only if root
				// node is named 'xml'
				args = getArgs(root, jaxbctx, schema);
			} catch (IllegalArgumentException iae) {
				throw new ParsingException("illegal class arguments", iae);
			}
			int argLength = args.size();

			// next we need to see if there's a constructor that matches the
			// arguments provided...this has to be done by hand since
			// Class.getConstructor(Class []) doesn't handle sub-classes and
			// generic types (for instance, a constructor taking List won't
			// match a parameter list containing ArrayList)

			// get the list of all available constructors
			Constructor[] cons = c.getConstructors();
			Constructor constructor = null;

			for (int i = 0; i < cons.length; i++) {
				// get the parameters for this constructor
				Class[] params = cons[i].getParameterTypes();
				if (params.length == argLength) {
					Iterator it = args.iterator();
					int j = 0;

					// loop through the parameters and see if each one is
					// assignable from the coresponding input argument
					while (it.hasNext()) {
						final Class<?> argClass = it.next().getClass();
						LOGGER.debug(
								"Testing if param[{}]='{}' of constructor '{}' is assignable from arg class '{}'",
								new Object[] { Integer.toString(j), params[j],
										cons[i], argClass });
						if (!params[j].isAssignableFrom(argClass)) {
							break;
						}
						j++;
					}

					// if we looked at all the parameters, then this
					// constructor matches the input
					if (j == argLength) {
						constructor = cons[i];
					}
				}

				// if we've found a matching constructor then stop looping
				if (constructor != null) {
					break;
				}
			}

			// make sure we found a matching constructor
			if (constructor == null) {
				throw new ParsingException(
						"couldn't find a matching constructor");
			}

			// finally, instantiate the class
			try {
				instance = constructor.newInstance(args.toArray());
			} catch (InstantiationException ie) {
				throw new ParsingException("couldn't instantiate " + className,
						ie);
			} catch (IllegalAccessException iae) {
				throw new ParsingException(
						"couldn't get access to instance of " + className, iae);
			} catch (InvocationTargetException ite) {
				throw new ParsingException("couldn't create " + className, ite);
			}
		}

		return instance;
	}

	/**
	 * Private helper that gets the constructor arguments for a given class.
	 * Right now this just supports String, List and XML elements, but it's
	 * trivial to add support for other types should that be needed. Right now,
	 * it's not clear that there's any need for other types. The JAXB context
	 * and schema arguments are added to the result arguments for JAXB
	 * unmarshalling and schema validation if root node is named 'xml'. Schema
	 * argument may be null to cancel validation.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private List getArgs(Node root, JAXBContext jaxbctx, Schema schema) {
		List args = new ArrayList();
		NodeList children = root.getChildNodes();

		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			String name = child.getNodeName();

			if (child.getNodeType() == Node.ELEMENT_NODE) {
				if (name.equals("string")) {
					args.add(child.getFirstChild().getNodeValue());
				} else if (name.equalsIgnoreCase("list")) {
					args.add(getArgs(child));
				} else if (name.equals("xml")) {
					// XML configuration element, first child node must be an
					// Element
					final Element elt = (Element) child;
					final NodeList nodes = elt.getElementsByTagName("*");
					if (nodes.getLength() == 0) {
						throw new IllegalArgumentException(
								"Invalid <xml> element: no child element found");
					}

					args.add(nodes.item(0));
					args.add(jaxbctx);
					args.add(schema);
				}

				/*
				 * FIXME: 'xml' is generic enough to address all types of
				 * configuration below. We should not address all possible
				 * FinderModule configuration types here because we expect to
				 * add new ones dynamically at runtime. Each FinderModule must
				 * document and parse the actual configuration options in his
				 * own class/doc, for loose coupling.
				 * 
				 * BEGIN REMOVE ALL THIS
				 */
				else if (name.equalsIgnoreCase("map")) {
					args.add(getArgs(child));
				} else if (name.equals("Options")) {
					args.add(getArgs(child));
				} else if (name.equals("AttributeDbFinder")) {
					args.add(getArgs(child));
				} else if (name.equals("AttributeLdapFinder")) {
					args.add(getArgs(child));
				} else if (name.equals("AttributeKeystoneFinder")) {
					args.add(getArgs(child));
				} else if (name.equals("AttributeRoleFinder")) {
					args.add(getArgs(child));
				} else if (name.equals("AttributeCertificateFinder")) {
					args.add(getArgs(child));
				} else if (name.equalsIgnoreCase("cache")) {
					args.add(getArgs(child));
				} else if (name.equals("url")) {
					Map<String, String> myMap = new HashMap<>();
					myMap.put("url", child.getFirstChild().getNodeValue());
					args.add(myMap);
				} else if (name.equals("attributeSupportedId")) {
					Map<String, String> myMap = new HashMap<>();
					myMap.put("attributeSupportedId", child.getFirstChild()
							.getNodeValue());
					args.add(myMap);
				} else if (name.equals("substituteVmId")) {
					Map<String, String> myMap = new HashMap<String, String>();
					myMap.put("substituteVmId", child.getFirstChild()
							.getNodeValue());
					args.add(myMap);
				} else if (name.equals("substituteTokenId")) {
					Map<String, String> myMap = new HashMap<String, String>();
					myMap.put("substituteTokenId", child.getFirstChild()
							.getNodeValue());
					args.add(myMap);
				} else if (name.equals("roleAttribute")) {
					Map<String, String> myMap = new HashMap<String, String>();
					myMap.put("roleAttribute", child.getFirstChild()
							.getNodeValue());
					args.add(myMap);
				} else if (name.equals("ldapAttribute")) {
					Map<String, String> myMap = new HashMap<String, String>();
					myMap.put("ldapAttribute", child.getFirstChild()
							.getNodeValue());
					args.add(myMap);
				} else if (name.equals("baseDn")) {
					Map<String, String> myMap = new HashMap<String, String>();
					myMap.put("baseDn", child.getFirstChild().getNodeValue());
					args.add(myMap);
				} else if (name.equals("sqlRequest")) {
					Map<String, String> myMap = new HashMap<String, String>();
					myMap.put("sqlRequest", child.getFirstChild()
							.getNodeValue());
					args.add(myMap);
				} else if (name.equals("dbName")) {
					Map<String, String> myMap = new HashMap<String, String>();
					myMap.put("dbName", child.getFirstChild().getNodeValue());
					args.add(myMap);
				} else if (name.equals("substituteValue")) {
					Map<String, String> myMap = new HashMap<String, String>();
					myMap.put("substituteValue", child.getFirstChild()
							.getNodeValue());
					args.add(myMap);
				} else if (name.equals("tenantSubstituteValue")) {
					Map<String, String> myMap = new HashMap<String, String>();
					myMap.put("tenantSubstituteValue", child.getFirstChild()
							.getNodeValue());
					args.add(myMap);
				} else if (name.equals("password")) {
					Map<String, String> myMap = new HashMap<String, String>();
					if (child.getFirstChild() == null) {
						myMap.put("password", "");
					} else {
						myMap.put("password", child.getFirstChild()
								.getNodeValue());
					}
					args.add(myMap);
				} else if (name.equals("username")) {
					Map<String, String> myMap = new HashMap<String, String>();
					myMap.put("username", child.getFirstChild().getNodeValue());
					args.add(myMap);
				} else if (name.equals("driver")) {
					Map<String, String> myMap = new HashMap<String, String>();
					myMap.put("driver", child.getFirstChild().getNodeValue());
					args.add(myMap);
				} else if (name.equals("activate")) {
					Map<String, String> myMap = new HashMap<String, String>();
					myMap.put("activate", child.getFirstChild().getNodeValue());
					args.add(myMap);
				} else if (name.equals("maxElementsInMemory")) {
					Map<String, String> myMap = new HashMap<String, String>();
					myMap.put("maxElementsInMemory", child.getFirstChild()
							.getNodeValue());
					args.add(myMap);
				} else if (name.equals("overflowToDisk")) {
					Map<String, String> myMap = new HashMap<String, String>();
					myMap.put("overflowToDisk", child.getFirstChild()
							.getNodeValue());
					args.add(myMap);
				} else if (name.equals("eternal")) {
					Map<String, String> myMap = new HashMap<String, String>();
					myMap.put("eternal", child.getFirstChild().getNodeValue());
					args.add(myMap);
				} else if (name.equals("timeToLiveSeconds")) {
					Map<String, String> myMap = new HashMap<String, String>();
					myMap.put("timeToLiveSeconds", child.getFirstChild()
							.getNodeValue());
					args.add(myMap);
				} else if (name.equals("adminToken")) {
					Map<String, String> myMap = new HashMap<String, String>();
					myMap.put("adminToken", child.getFirstChild()
							.getNodeValue());
					args.add(myMap);
				}
				/*
				 * END REMOVE ALL THIS
				 */
				else {
					throw new IllegalArgumentException("Unkown arg type: "
							+ name);
				}
			}
		}
		return args;
	}

	private List getArgs(Node root) {
		return getArgs(root, null, null);
	}

	/**
	 * Private helper used by the three factory routines to see if the given
	 * factory should be based on the standard setup
	 */
	private boolean useStandard(Node node, String attributeName) {
		NamedNodeMap map = node.getAttributes();
		if (map == null) {
			return true;
		}

		Node attrNode = map.getNamedItem(attributeName);
		if (attrNode == null) {
			return true;
		}

		return attrNode.getNodeValue().equals("true");
	}

	/**
	 * Returns the default PDP configuration. If no default was specified then
	 * this throws an exception.
	 * 
	 * @return the default PDP configuration
	 * 
	 * @throws UnknownIdentifierException
	 *             if there is no default config
	 */
	public PDPConfig getDefaultPDPConfig() throws UnknownIdentifierException {
		if (defaultPDPConfig == null) {
			LOGGER.error("Default pdp config is null");
			throw new UnknownIdentifierException("no default available");
		}

		return defaultPDPConfig;
	}

	/**
	 * Returns the PDP configuration with the given name. If no such
	 * configuration exists then an exception is thrown.
	 * 
	 * @param name
	 * 
	 * @return the matching PDP configuation
	 * 
	 * @throws UnknownIdentifierException
	 *             if the name is unknown
	 */
	public PDPConfig getPDPConfig(String name)
			throws UnknownIdentifierException {
		Object object = pdpConfigMap.get(name);

		if (object == null) {
			throw new UnknownIdentifierException("unknown pdp: " + name);
		}

		return (PDPConfig) object;
	}

	/**
	 * Returns a set of identifiers representing each PDP configuration
	 * available.
	 * 
	 * @return a <code>Set</code> of <code>String</code>s
	 */
	public Set getSupportedPDPConfigurations() {
		return Collections.unmodifiableSet(pdpConfigMap.keySet());
	}

	/**
	 * Returns the default attribute factory.
	 * 
	 * @return the default attribute factory
	 */
	public AttributeFactory getDefaultAttributeFactory() {
		return defaultAttributeFactory;
	}

	/**
	 * Returns the attribute factory with the given name. If no such factory
	 * exists then an exception is thrown.
	 * 
	 * @return the matching attribute factory
	 * 
	 * @throws UnknownIdentifierException
	 *             if the name is unknown
	 */
	public AttributeFactory getAttributeFactory(String name)
			throws UnknownIdentifierException {
		Object object = attributeMap.get(name);

		if (object == null) {
			throw new UnknownIdentifierException("unknown factory: " + name);
		}

		return (AttributeFactory) object;
	}

	/**
	 * Returns a set of identifiers representing each attribute factory
	 * available.
	 * 
	 * @return a <code>Set</code> of <code>String</code>s
	 */
	public Set getSupportedAttributeFactories() {
		return Collections.unmodifiableSet(attributeMap.keySet());
	}

	/**
	 * Registers all the supported factories with the given identifiers. If a
	 * given identifier is already in use, then that factory is not registered.
	 * This method is provided only as a convenience, and any registration that
	 * may involve identifier clashes should be done by registering each factory
	 * individually.
	 */
	public void registerAttributeFactories() {
		Iterator it = attributeMap.keySet().iterator();

		while (it.hasNext()) {
			String id = (String) (it.next());
			AttributeFactory af = (AttributeFactory) (attributeMap.get(id));

			try {
				AttributeFactory.registerFactory(id, new AFProxy(af));
			} catch (IllegalArgumentException iae) {
				LOGGER.warn("Couldn't register AttributeFactory:" + id
						+ " (already in use)", iae);
			}
		}
	}

	/**
	 * Returns the default combiningAlg factory.
	 * 
	 * @return the default combiningAlg factory
	 */
	public CombiningAlgFactory getDefaultCombiningAlgFactory() {
		return defaultCombiningFactory;
	}

	/**
	 * Returns the combiningAlg factory with the given name. If no such factory
	 * exists then an exception is thrown.
	 * 
	 * @param name
	 * 
	 * @return the matching combiningAlg factory
	 * 
	 * @throws UnknownIdentifierException
	 *             if the name is unknown
	 */
	public CombiningAlgFactory getCombiningAlgFactory(String name)
			throws UnknownIdentifierException {
		Object object = combiningMap.get(name);

		if (object == null)
			throw new UnknownIdentifierException("unknown factory: " + name);

		return (CombiningAlgFactory) object;
	}

	/**
	 * Returns a set of identifiers representing each combiningAlg factory
	 * available.
	 * 
	 * @return a <code>Set</code> of <code>String</code>s
	 */
	public Set getSupportedCombiningAlgFactories() {
		return Collections.unmodifiableSet(combiningMap.keySet());
	}

	/**
	 * Registers all the supported factories with the given identifiers. If a
	 * given identifier is already in use, then that factory is not registered.
	 * This method is provided only as a convenience, and any registration that
	 * may involve identifier clashes should be done by registering each factory
	 * individually.
	 */
	public void registerCombiningAlgFactories() {
		Iterator it = combiningMap.keySet().iterator();

		while (it.hasNext()) {
			String id = (String) (it.next());
			CombiningAlgFactory cf = (CombiningAlgFactory) (combiningMap
					.get(id));

			try {
				CombiningAlgFactory.registerFactory(id, new CAFProxy(cf));
			} catch (IllegalArgumentException iae) {
				LOGGER.warn(
						"Couldn't register CombiningAlgFactory: {} (already in use)",
						id, iae);
			}
		}
	}

	/**
	 * Returns the default function factory proxy.
	 * 
	 * @return the default function factory proxy
	 */
	public FunctionFactoryProxy getDefaultFunctionFactoryProxy() {
		return defaultFunctionFactoryProxy;
	}

	/**
	 * Returns the function factory proxy with the given name. If no such proxy
	 * exists then an exception is thrown.
	 * 
	 * @param name
	 * 
	 * @return the matching function factory proxy
	 * 
	 * @throws UnknownIdentifierException
	 *             if the name is unknown
	 */
	public FunctionFactoryProxy getFunctionFactoryProxy(String name)
			throws UnknownIdentifierException {
		Object object = functionMap.get(name);

		if (object == null)
			throw new UnknownIdentifierException("unknown factory: " + name);

		return (FunctionFactoryProxy) object;
	}

	/**
	 * Returns a set of identifiers representing each function factory proxy
	 * available.
	 * 
	 * @return a <code>Set</code> of <code>String</code>s
	 */
	public Set getSupportedFunctionFactories() {
		return Collections.unmodifiableSet(functionMap.keySet());
	}

	/**
	 * Registers all the supported factories with the given identifiers. If a
	 * given identifier is already in use, then that factory is not registered.
	 * This method is provided only as a convenience, and any registration that
	 * may involve identifier clashes should be done by registering each factory
	 * individually.
	 */
	public void registerFunctionFactories() {
		Iterator it = functionMap.keySet().iterator();

		while (it.hasNext()) {
			String id = (String) (it.next());
			FunctionFactoryProxy ffp = (FunctionFactoryProxy) (functionMap
					.get(id));

			try {
				FunctionFactory.registerFactory(id, ffp);
			} catch (IllegalArgumentException iae) {
				LOGGER.warn("Couldn't register FunctionFactory: " + id
						+ " (already in use)", iae);
			}
		}
	}

	/**
	 * Uses the default configuration to re-set the default factories used by
	 * the system (attribute, combining algorithm, and function). If a default
	 * is not provided for a given factory, then that factory will not be set as
	 * the system's default.
	 */
	public void useDefaultFactories() {
		LOGGER.debug("Switching to default factories from configuration");

		// set the default attribute factory, if it exists here
		if (defaultAttributeFactory != null) {
			AttributeFactory.setDefaultFactory(new AFProxy(
					defaultAttributeFactory));
		}

		// set the default combining algorithm factory, if it exists here
		if (defaultCombiningFactory != null) {
			CombiningAlgFactory.setDefaultFactory(new CAFProxy(
					defaultCombiningFactory));
		}

		// set the default function factories, if they exists here
		if (defaultFunctionFactoryProxy != null) {
			FunctionFactory.setDefaultFactory(defaultFunctionFactoryProxy);
		}
	}

	/**
     *
     */
	class AFProxy implements AttributeFactoryProxy {
		private AttributeFactory factory;

		public AFProxy(AttributeFactory factory) {
			this.factory = factory;
		}

		public AttributeFactory getFactory() {
			return factory;
		}
	}

	/**
     *
     */
	class CAFProxy implements CombiningAlgFactoryProxy {
		private CombiningAlgFactory factory;

		public CAFProxy(CombiningAlgFactory factory) {
			this.factory = factory;
		}

		public CombiningAlgFactory getFactory() {
			return factory;
		}
	}

}
