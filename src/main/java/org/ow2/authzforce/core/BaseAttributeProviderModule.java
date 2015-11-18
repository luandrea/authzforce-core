/**
 * Copyright (C) 2011-2015 Thales Services SAS.
 *
 * This file is part of AuthZForce.
 *
 * AuthZForce is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * AuthZForce is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AuthZForce. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ow2.authzforce.core;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.AttributeDesignatorType;

import org.ow2.authzforce.core.expression.AttributeGUID;
import org.ow2.authzforce.core.value.DatatypeFactoryRegistry;
import org.ow2.authzforce.xmlns.pdp.ext.AbstractAttributeProvider;

/**
 * This is the base class that all <code>AttributeProvider</code> modules extend.
 * <p>
 * Implements {@link Closeable} because it may may use resources external to the JVM such as a cache, a disk, a connection to a remote server, etc. for
 * retrieving the attribute values. Therefore, these resources must be released by calling {@link #close()} when it is no longer needed.
 */
public abstract class BaseAttributeProviderModule implements Closeable, AttributeProviderModule
{
	/**
	 * Intermediate dependency-aware {@link BaseAttributeProviderModule} factory that can create instances of modules from a XML/JAXB configuration, and also
	 * provides the dependencies (required attributes) (based on this configuration), that any such instance (created by it) will need. Providing the
	 * dependencies helps to optimize the {@code depAttrProvider} argument to {@link #getInstance(DatatypeFactoryRegistry, AttributeProvider)} and therefore
	 * optimize the created module's job of finding its own supported attribute values based on other attributes in the evaluation context.
	 * 
	 */
	public interface DependencyAwareFactory
	{

		/**
		 * Returns non-null <code>Set</code> of <code>AttributeDesignator</code>s required as runtime inputs to the attribute Provider module instance created by
		 * this builder. The PDP framework calls this method to know what input attributes the module will require (dependencies) before
		 * {@link #getInstance(DatatypeFactoryRegistry, AttributeProvider)} , and based on this, creates a specific dependency attribute Provider that will enable
		 * the module to find its dependency attributes. So when the PDP framework calls {@link #getInstance(DatatypeFactoryRegistry, AttributeProvider)}
		 * subsequently to instantiate the module, the last argument is this dependency attribute Provider.
		 * 
		 * @return a <code>Set</code> of required <code>AttributeDesignatorType</code>s. Null or empty if none required.
		 */
		Set<AttributeDesignatorType> getDependencies();

		/**
		 * Create AttributeProviderModule instance
		 * 
		 * @param attrDatatypeFactory
		 *            Attribute datatype factory for the module to be able to create attribute values
		 * @param depAttrProvider
		 *            Attribute Provider for the module to find dependency/required attributes
		 * 
		 * @return attribute value in internal model
		 */
		BaseAttributeProviderModule getInstance(DatatypeFactoryRegistry attrDatatypeFactory, AttributeProvider depAttrProvider);
	}

	/**
	 * Preliminary factory that creates a dependency-aware AttributeProviderModule factory from parsing thee attribute dependencies (attributes on which the
	 * module depends to find its own supported attributes) declared in the XML configuration (possibly dynamic).
	 * 
	 * @param <CONF_T>
	 *            type of configuration (XML-schema-derived) of the module (initialization parameter)
	 * 
	 *            This class follows the Step Factory Pattern to guide clients through the creation of the object in a particular sequence of method calls:
	 *            <p>
	 *            http://rdafbn.blogspot.fr/2012/07/step-builder-pattern_28.html
	 *            </p>
	 */
	public static abstract class Factory<CONF_T extends AbstractAttributeProvider> extends JaxbBoundPdpExtension<CONF_T>
	{

		/**
		 * Creates an attribute-dependency-aware module factory by inferring attribute dependencies (required attributes) from {@code conf}.
		 * 
		 * @param conf
		 *            module configuration, that may define what attributes are required (dependency attributes)
		 * @return a factory aware of dependencies (required attributes) possibly inferred from input {@code conf}
		 */
		public abstract DependencyAwareFactory parseDependencies(CONF_T conf);
	}

	static final class Map extends HashMap<AttributeGUID, BaseAttributeProviderModule> implements Closeable
	{

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		private transient final Set<BaseAttributeProviderModule> modules = new HashSet<>();

		Map(int initialCapacity)
		{
			super(initialCapacity);
		}

		Map()
		{
			super();
		}

		/**
		 * Creates/add new module to the map
		 * 
		 * @param jaxbAttributeProviderConf
		 *            attribute Provider module configuration
		 * @param attributeFactory
		 *            attribute datatype factory
		 * @throws IllegalArgumentException
		 *             if list of attributes provided by the created module is undefined/empty; or the created module is in conflict with another one already
		 *             registered to provide the same or part of the same attributes
		 * @throws IOException
		 *             error closing the module when and before an IllegalArgumentException is raised
		 */
		void addModule(AbstractAttributeProvider jaxbAttributeProviderConf, DatatypeFactoryRegistry attributeFactory) throws IllegalArgumentException,
				IOException
		{
			final BaseAttributeProviderModule.Factory<AbstractAttributeProvider> attrProviderModBuilder = PdpExtensionLoader.getJaxbBoundExtension(
					BaseAttributeProviderModule.Factory.class, jaxbAttributeProviderConf.getClass());
			final BaseAttributeProviderModule.DependencyAwareFactory depAwareAttrProviderModBuilder = attrProviderModBuilder
					.parseDependencies(jaxbAttributeProviderConf);
			final Set<AttributeDesignatorType> requiredAttrs = depAwareAttrProviderModBuilder.getDependencies();
			/*
			 * Each AttributeProviderModule is given a read-only AttributeProvider - aka "dependency attribute Provider" - to find any attribute they require
			 * (dependency), based on the attribute Provider modules that provide these required attributes (set above); read-only so that modules use this
			 * attribute Provider only to get required attributes, nothing else. Create this dependency attribute Provider.
			 */
			final BaseAttributeProvider.DefaultImpl depAttrProvider;
			if (requiredAttrs == null)
			{
				depAttrProvider = new BaseAttributeProvider.DefaultImpl();
			} else
			{
				final java.util.Map<AttributeGUID, AttributeProviderModule> immutableCopyOfAttrProviderModsByAttrId = Collections
						.<AttributeGUID, AttributeProviderModule> unmodifiableMap(this);
				depAttrProvider = new BaseAttributeProvider.DefaultImpl(immutableCopyOfAttrProviderModsByAttrId, requiredAttrs);
			}

			// attrProviderMod closing isn't done in this method but handled in close() method when closing all modules
			final BaseAttributeProviderModule attrProviderMod = depAwareAttrProviderModBuilder.getInstance(attributeFactory, depAttrProvider);
			final Set<AttributeDesignatorType> providedAttributes = attrProviderMod.getProvidedAttributes();
			if (providedAttributes == null || providedAttributes.isEmpty())
			{
				attrProviderMod.close();
				throw new IllegalArgumentException("Invalid AttributeProvider #" + attrProviderMod.getInstanceID()
						+ " : list of supported AttributeDesignators is null or empty");
			}

			modules.add(attrProviderMod);

			for (final AttributeDesignatorType attrDesignator : providedAttributes)
			{
				final AttributeGUID attrGUID = new AttributeGUID(attrDesignator);
				if (containsKey(attrGUID))
				{
					attrProviderMod.close();
					throw new IllegalArgumentException("Conflict: AttributeProvider module #" + attrProviderMod.getInstanceID()
							+ " providing the same AttributeDesignator (" + attrGUID + ") as another already registered.");
				}

				put(attrGUID, attrProviderMod);
			}
		}

		@Override
		public void close() throws IOException
		{
			// An error occuring on closing one module should not stop from closing the others
			// But we keep the exception in memory if any, to throw it at the end as we do not want to hide that an error occurred
			IOException latestEx = null;
			for (final BaseAttributeProviderModule mod : modules)
			{
				try
				{
					mod.close();
				} catch (IOException e)
				{
					latestEx = e;
				}
			}

			if (latestEx != null)
			{
				throw latestEx;
			}
		}

	}

	protected static final UnsupportedOperationException UNSUPPORTED_ATTRIBUTE_CATEGORY_EXCEPTION = new UnsupportedOperationException(
			"Unsupported attribute category");
	protected static final UnsupportedOperationException UNSUPPORTED_ATTRIBUTE_ISSUER_EXCEPTION = new UnsupportedOperationException(
			"Unsupported attribute issuer");
	protected static final UnsupportedOperationException UNSUPPORTED_ATTRIBUTE_ID_EXCEPTION = new UnsupportedOperationException("Unsupported attribute ID");
	protected static final UnsupportedOperationException UNSUPPORTED_ATTRIBUTE_DATATYPE_EXCEPTION = new UnsupportedOperationException(
			"Unsupported attribute datetype");
	private static final IllegalArgumentException UNDEF_MODULE_INSTANCE_ID = new IllegalArgumentException("Undefined attribute Provider module's instance ID");

	protected final String instanceID;
	protected final AttributeProvider dependencyAttributeProvider;
	protected final DatatypeFactoryRegistry attributeFactory;

	// cached method result
	private transient final int hashCode;
	private transient final String toString;

	/**
	 * Instantiates the attribute Provider module
	 * 
	 * @param instanceID
	 *            module instance ID (to be used as unique identifier for this instance in the logs for example);
	 * @param attributeFactory
	 *            factory for creating attribute values
	 * @param depAttributeProvider
	 *            dependency attribute Provider. This module may require other attributes as input to do the job. As it does not know how to get them (it is not
	 *            its job), it may call this {@code depAttributeProvider} to get them on its behalf.
	 * @throws IllegalArgumentException
	 *             if instanceId null
	 */
	protected BaseAttributeProviderModule(String instanceID, DatatypeFactoryRegistry attributeFactory, AttributeProvider depAttributeProvider)
			throws IllegalArgumentException
	{
		if (instanceID == null)
		{
			throw UNDEF_MODULE_INSTANCE_ID;
		}

		this.instanceID = instanceID;
		this.dependencyAttributeProvider = depAttributeProvider;
		this.attributeFactory = attributeFactory;
		this.hashCode = instanceID.hashCode();
		this.toString = "AttributeProvider[" + instanceID + "]";
	}

	/**
	 * Get user-defined ID for this module instance
	 * 
	 * @return instance ID
	 */
	public final String getInstanceID()
	{
		return this.instanceID;
	}

	@Override
	public int hashCode()
	{
		return hashCode;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}

		if (!(obj instanceof BaseAttributeProviderModule))
		{
			return false;
		}

		final BaseAttributeProviderModule other = (BaseAttributeProviderModule) obj;
		return this.instanceID.equals(other.instanceID);
	}

	@Override
	public String toString()
	{
		return toString;
	}

}
