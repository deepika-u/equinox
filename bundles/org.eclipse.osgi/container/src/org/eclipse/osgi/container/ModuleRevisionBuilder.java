/*******************************************************************************
 * Copyright (c) 2012, 2021 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.container;

import java.security.AllPermission;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.eclipse.osgi.internal.container.NamespaceList;
import org.eclipse.osgi.internal.container.NamespaceList.Builder;
import org.eclipse.osgi.internal.framework.FilterImpl;
import org.osgi.framework.AdminPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.resource.Namespace;

/**
 * A builder for creating module {@link ModuleRevision} objects.  A builder can only be used by
 * the module {@link ModuleContainer container} to build revisions when
 * {@link ModuleContainer#install(Module, String, ModuleRevisionBuilder, Object)
 * installing} or {@link ModuleContainer#update(Module, ModuleRevisionBuilder, Object) updating} a module.
 * <p>
 * The builder provides the instructions to the container for creating a {@link ModuleRevision}.
 * They are not thread-safe; in the absence of external synchronization, they do not support concurrent access by multiple threads.
 * @since 3.10
 */
public final class ModuleRevisionBuilder {
	private final static Class<?> SINGLETON_MAP_CLASS = Collections.singletonMap(null, null).getClass();
	private final static Class<?> UNMODIFIABLE_MAP_CLASS = Collections.unmodifiableMap(Collections.emptyMap()).getClass();

	/**
	 * Provides information about a capability or requirement
	 */
	public static class GenericInfo {
		final String namespace;
		final Map<String, String> directives;
		final Map<String, Object> attributes;
		final boolean mutable;

		GenericInfo(String namespace, Map<String, String> directives, Map<String, Object> attributes, boolean mutable) {
			this.namespace = namespace;
			this.directives = directives;
			this.attributes = attributes;
			this.mutable = mutable;
		}

		/**
		 * Returns the namespace of this generic info
		 * @return the namespace
		 */
		public String getNamespace() {
			return namespace;
		}

		/**
		 * Returns the directives of this generic info
		 * @return the directives
		 */
		public Map<String, String> getDirectives() {
			return directives;
		}

		/**
		 * Returns the attributes of this generic info
		 * @return the attributes
		 */
		public Map<String, Object> getAttributes() {
			return attributes;
		}
	}

	private String symbolicName = null;
	private Version version = Version.emptyVersion;
	private int types = 0;
	private final NamespaceList.Builder<GenericInfo> capabilityInfos = Builder.create(GenericInfo::getNamespace);
	private final NamespaceList.Builder<GenericInfo> requirementInfos = Builder.create(GenericInfo::getNamespace);
	private long id = -1;

	/**
	 * Constructs a new module builder
	 */
	public ModuleRevisionBuilder() {
		// nothing
	}

	/**
	 * Sets the symbolic name for the builder
	 * @param symbolicName the symbolic name
	 */
	public void setSymbolicName(String symbolicName) {
		this.symbolicName = symbolicName;
	}

	/**
	 * Sets the module version for the builder.
	 * @param version the version
	 */
	public void setVersion(Version version) {
		this.version = version;
	}

	/**
	 * Sets the module types for the builder.
	 * @param types the module types
	 */
	public void setTypes(int types) {
		this.types = types;
	}

	/**
	 * Sets the module ID for the builder.
	 * <p>
	 * This module ID will be used if this builder is used to
	 * {@link ModuleContainer#install(Module, String, ModuleRevisionBuilder, Object) install}
	 * a module.  If the ID is not set then a module ID will be generated by the module
	 * container at install time. If a module already exists with the specified ID
	 * then an error will occur when attempting to install a new module with this
	 * builder.
	 * <p>
	 * Note that the system module with location {@link Constants#SYSTEM_BUNDLE_LOCATION}
	 * always gets module ID of zero.  The builder for the system module is not
	 * asked to provide the module ID for the system module at install time.
	 * @param id the module ID to use.  Must be >= 1.
	 * @since 3.13
	 */
	public void setId(long id) {
		if (id < 1) {
			throw new IllegalArgumentException("ID must be >=1."); //$NON-NLS-1$
		}
		this.id = id;
	}

	void setInternalId(long id) {
		this.id = id;
	}

	/**
	 * Adds a capability to this builder using the specified namespace, directives and attributes
	 * @param namespace the namespace of the capability
	 * @param directives the directives of the capability
	 * @param attributes the attributes of the capability
	 */
	public void addCapability(String namespace, Map<String, String> directives, Map<String, Object> attributes) {
		addGenericInfo(capabilityInfos, namespace, directives, attributes);
	}

	/**
	 * Returns a snapshot of the capabilities for this builder
	 * @return the capabilities
	 */
	public List<GenericInfo> getCapabilities() {
		return getCapabilities(null);
	}

	/**
	 * Returns a snapshot of the capabilities in the given namespace for this
	 * builder
	 * 
	 * @param namespace The namespace of the capabilities to return or null to
	 *                  return the capabilities from all namespaces.
	 * @return the capabilities
	 * @since 3.17
	 */
	public List<GenericInfo> getCapabilities(String namespace) {
		return capabilityInfos.getNamespaceElements(namespace);
	}

	/**
	 * Adds a requirement to this builder using the specified namespace, directives and attributes
	 * @param namespace the namespace of the requirement
	 * @param directives the directives of the requirement
	 * @param attributes the attributes of the requirement
	 */
	public void addRequirement(String namespace, Map<String, String> directives, Map<String, Object> attributes) {
		addGenericInfo(requirementInfos, namespace, directives, attributes);
	}

	/**
	 * Returns a snapshot of the requirements for this builder
	 * @return the requirements
	 */
	public List<GenericInfo> getRequirements() {
		return getRequirements(null);
	}

	NamespaceList.Builder<GenericInfo> getRequirementsBuilder() {
		return requirementInfos;
	}

	/**
	 * Returns a snapshot of the requirements in the given namespace for this
	 * builder
	 * 
	 * @param namespace The namespace of the requirements to return or null to
	 *                  return the requirements from all namespaces.
	 * @return the requirements
	 * @since 3.17
	 */
	public List<GenericInfo> getRequirements(String namespace) {
		return requirementInfos.getNamespaceElements(namespace);
	}

	/**
	 * Returns the symbolic name for this builder.
	 * @return the symbolic name for this builder.
	 */
	public String getSymbolicName() {
		return symbolicName;
	}

	/**
	 * Returns the module version for this builder.
	 * @return the module version for this builder.
	 */
	public Version getVersion() {
		return version;
	}

	/**
	 * Returns the module type for this builder.
	 * @return the module type for this builder.
	 */
	public int getTypes() {
		return types;
	}

	/**
	 * Returns the module id for this builder. A value of -1
	 * indicates that the module ID will be generated by the
	 * module container at {@link ModuleContainer#install(Module, String, ModuleRevisionBuilder, Object) install}
	 * time.
	 * @return the module id for this builder.
	 * @since 3.13
	 */
	public long getId() {
		return id;
	}

	/**
	 * Used by the container to build a new revision for a module.
	 * This builder is used to build a new {@link Module#getCurrentRevision() current}
	 * revision for the specified module.
	 * @param module the module to build a new revision for
	 * @param revisionInfo the revision info for the new revision, may be {@code null}
	 * @return the new new {@link Module#getCurrentRevision() current} revision.
	 */
	ModuleRevision addRevision(Module module, Object revisionInfo) {
		ModuleRevisions revisions = module.getRevisions();
		ModuleRevision revision = new ModuleRevision(symbolicName, version, types, capabilityInfos, requirementInfos, revisions, revisionInfo);

		revisions.addRevision(revision);
		module.getContainer().getAdaptor().associateRevision(revision, revisionInfo);

		try {
			checkFrameworkExtensionPermission(module, revision);
			module.getContainer().checkAdminPermission(module.getBundle(), AdminPermission.LIFECYCLE);
		} catch (SecurityException e) {
			revisions.removeRevision(revision);
			throw e;
		}
		return revision;
	}

	private void checkFrameworkExtensionPermission(Module module, ModuleRevision revision) {
		if (System.getSecurityManager() == null) {
			return;
		}
		if ((revision.getTypes() & BundleRevision.TYPE_FRAGMENT) != 0) {
			Collection<?> systemNames = Collections.emptyList();
			Module systemModule = module.getContainer().getModule(0);
			if (systemModule != null) {
				ModuleRevision systemRevision = systemModule.getCurrentRevision();
				List<ModuleCapability> hostCapabilities = systemRevision.getModuleCapabilities(HostNamespace.HOST_NAMESPACE);
				for (ModuleCapability hostCapability : hostCapabilities) {
					Object hostNames = hostCapability.getAttributes().get(HostNamespace.HOST_NAMESPACE);
					if (hostNames instanceof Collection) {
						systemNames = (Collection<?>) hostNames;
					} else if (hostNames instanceof String) {
						systemNames = Arrays.asList(hostNames);
					}
				}
			}
			List<ModuleRequirement> hostRequirements = revision.getModuleRequirements(HostNamespace.HOST_NAMESPACE);
			for (ModuleRequirement hostRequirement : hostRequirements) {
				FilterImpl f = null;
				String filterSpec = hostRequirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE);
				if (filterSpec != null) {
					try {
						f = FilterImpl.newInstance(filterSpec);
						String hostName = f.getPrimaryKeyValue(HostNamespace.HOST_NAMESPACE);
						if (hostName != null) {
							if (systemNames.contains(hostName)) {
								Bundle b = module.getBundle();
								if (b != null && !b.hasPermission(new AllPermission())) {
									SecurityException se = new SecurityException(
											"Must have AllPermission granted to install an extension bundle: " + b); //$NON-NLS-1$
									// TODO this is such a hack: making the cause a bundle exception so we can throw the right one later
									BundleException be = new BundleException(se.getMessage(), BundleException.SECURITY_ERROR, se);
									se.initCause(be);
									throw se;
								}
								module.getContainer().checkAdminPermission(module.getBundle(), AdminPermission.EXTENSIONLIFECYCLE);
							}
						}
					} catch (InvalidSyntaxException e) { // ignore
					}
				}
			}
		}
	}

	private void addGenericInfo(NamespaceList.Builder<GenericInfo> infos, String namespace, Map<String, String> directives, Map<String, Object> attributes) {
		infos.add(new GenericInfo(namespace, directives, attributes, true));
	}

	void basicAddCapability(String namespace, Map<String, String> directives, Map<String, Object> attributes) {
		basicAddGenericInfo(capabilityInfos, namespace, directives, attributes);
	}

	void basicAddRequirement(String namespace, Map<String, String> directives, Map<String, Object> attributes) {
		basicAddGenericInfo(requirementInfos, namespace, directives, attributes);
	}

	private static void basicAddGenericInfo(NamespaceList.Builder<GenericInfo> infos, String namespace, Map<String, String> directives, Map<String, Object> attributes) {
		infos.add(new GenericInfo(namespace, unmodifiableMap(directives), unmodifiableMap(attributes), false));
	}

	@SuppressWarnings("unchecked")
	static <K, V> Map<K, V> unmodifiableMap(Map<? extends K, ? extends V> map) {
		int size = map.size();
		if (size == 0) {
			return Collections.emptyMap();
		}
		if (size == 1) {
			if (map.getClass() != SINGLETON_MAP_CLASS) {
				Map.Entry<? extends K, ? extends V> entry = map.entrySet().iterator().next();
				map = Collections.singletonMap(entry.getKey(), entry.getValue());
			}
		} else {
			if (map.getClass() != UNMODIFIABLE_MAP_CLASS) {
				map = Collections.unmodifiableMap(map);
			}
		}
		return (Map<K, V>) map;
	}

	void clear() {
		capabilityInfos.clear();
		requirementInfos.clear();
		id = -1;
		symbolicName = null;
		version = Version.emptyVersion;
		types = 0;
	}
}
