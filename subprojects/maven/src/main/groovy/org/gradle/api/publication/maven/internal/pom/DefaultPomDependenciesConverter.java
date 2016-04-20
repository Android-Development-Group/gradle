/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.publication.maven.internal.pom;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.maven.Conf2ScopeMapping;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.publication.maven.internal.VersionRangeMapper;

import java.util.*;

class DefaultPomDependenciesConverter implements PomDependenciesConverter {
    private static final List<Exclusion> EXCLUDE_ALL = initExcludeAll();
    private ExcludeRuleConverter excludeRuleConverter;
    private VersionRangeMapper versionRangeMapper;

    public DefaultPomDependenciesConverter(ExcludeRuleConverter excludeRuleConverter, VersionRangeMapper versionRangeMapper) {
        this.excludeRuleConverter = excludeRuleConverter;
        this.versionRangeMapper = versionRangeMapper;
    }

    public List<Dependency> convert(Conf2ScopeMappingContainer conf2ScopeMappingContainer, Set<Configuration> configurations) {
        Map<ModuleDependency, Set<Configuration>> dependencyToConfigurations = createDependencyToConfigurationsMap(configurations);
        Map<ModuleDependency, Conf2ScopeMapping> dependenciesMap = createDependencyToScopeMap(conf2ScopeMappingContainer, dependencyToConfigurations);
        Map<Dependency, Integer> dependenciesWithPriorities = new LinkedHashMap<Dependency, Integer>();
        for (ModuleDependency dependency : dependenciesMap.keySet()) {
            Conf2ScopeMapping conf2ScopeMapping = dependenciesMap.get(dependency);
            String scope = conf2ScopeMapping.getScope();
            Integer priority = conf2ScopeMapping.getPriority() == null ? 0 : conf2ScopeMapping.getPriority();
            Set<Configuration> dependencyConfigurations = dependencyToConfigurations.get(dependency);
            if (dependency.getArtifacts().size() == 0) {
                addFromDependencyDescriptor(dependenciesWithPriorities, dependency, scope, priority, dependencyConfigurations);
            } else {
                addFromArtifactDescriptor(dependenciesWithPriorities, dependency, scope, priority, dependencyConfigurations);
            }
        }
        return new ArrayList<Dependency>(dependenciesWithPriorities.keySet());
    }

    private Map<ModuleDependency, Conf2ScopeMapping> createDependencyToScopeMap(Conf2ScopeMappingContainer conf2ScopeMappingContainer,
                                                                                Map<ModuleDependency, Set<Configuration>> dependencyToConfigurations) {
        Map<ModuleDependency, Conf2ScopeMapping> dependencyToScope = new LinkedHashMap<ModuleDependency, Conf2ScopeMapping>();
        for (ModuleDependency dependency : dependencyToConfigurations.keySet()) {
            Conf2ScopeMapping conf2ScopeDependencyMapping = conf2ScopeMappingContainer.getMapping(dependencyToConfigurations.get(dependency));
            if (!useScope(conf2ScopeMappingContainer, conf2ScopeDependencyMapping)) {
                continue;
            }
            dependencyToScope.put(findDependency(dependency, conf2ScopeDependencyMapping.getConfiguration()), conf2ScopeDependencyMapping);
        }
        return dependencyToScope;
    }

    private ModuleDependency findDependency(ModuleDependency dependency, Configuration configuration) {
        for (ModuleDependency configurationDependency : configuration.getDependencies().withType(ModuleDependency.class)) {
            if (dependency.equals(configurationDependency)) {
                return configurationDependency;
            }
        }
        throw new GradleException("Dependency could not be found. We should never get here!");
    }

    private boolean useScope(Conf2ScopeMappingContainer conf2ScopeMappingContainer, Conf2ScopeMapping conf2ScopeMapping) {
        return conf2ScopeMapping.getScope() != null || !conf2ScopeMappingContainer.isSkipUnmappedConfs();
    }

    private Map<ModuleDependency, Set<Configuration>> createDependencyToConfigurationsMap(Set<Configuration> configurations) {
        Map<ModuleDependency, Set<Configuration>> dependencySetMap = new HashMap<ModuleDependency, Set<Configuration>>();
        for (Configuration configuration : configurations) {
            for (ModuleDependency dependency : configuration.getDependencies().withType(ModuleDependency.class)) {
                if (dependencySetMap.get(dependency) == null) {
                    dependencySetMap.put(dependency, new HashSet<Configuration>());
                }
                dependencySetMap.get(dependency).add(configuration);
            }
        }
        return dependencySetMap;
    }

    private void addFromArtifactDescriptor(Map<Dependency, Integer> dependenciesPriorityMap,
                                           ModuleDependency dependency, String scope, Integer priority,
                                           Set<Configuration> configurations) {
        for (DependencyArtifact artifact : dependency.getArtifacts()) {
            addMavenDependencies(dependenciesPriorityMap, dependency, artifact.getName(), artifact.getType(), scope, artifact.getClassifier(), priority, configurations);
        }
    }

    private void addFromDependencyDescriptor(Map<Dependency, Integer> dependenciesPriorityMap,
                                             ModuleDependency dependency, String scope, Integer priority,
                                             Set<Configuration> configurations) {
        addMavenDependencies(dependenciesPriorityMap, dependency, dependency.getName(), null, scope, null, priority, configurations);
    }

    private void addMavenDependencies(Map<Dependency, Integer> dependenciesWithPriorities,
                                      ModuleDependency dependency, String name, String type, String scope, String classifier, Integer priority,
                                      Set<Configuration> configurations) {
        List<Dependency> mavenDependencies = new ArrayList<Dependency>();

        if (dependency instanceof ProjectDependency) {
            ProjectDependency projectDependency = (ProjectDependency) dependency;
            final String artifactId = determineProjectDependencyArtifactId((ProjectDependency) dependency);

            Configuration dependencyConfig = projectDependency.getProjectConfiguration();
            for (PublishArtifact artifactToPublish : dependencyConfig.getAllArtifacts()) {
                Dependency mavenDependency = new Dependency();
                mavenDependency.setArtifactId(artifactId);
                if (artifactToPublish.getClassifier() != null && !artifactToPublish.getClassifier().equals("")) {
                    mavenDependency.setClassifier(artifactToPublish.getClassifier());
                }
                mavenDependencies.add(mavenDependency);
            }
        } else {
            Dependency mavenDependency = new Dependency();
            mavenDependency.setArtifactId(name);
            mavenDependency.setClassifier(classifier);
            mavenDependencies.add(mavenDependency);
        }

        for (Dependency mavenDependency : mavenDependencies) {
            mavenDependency.setGroupId(dependency.getGroup());
            mavenDependency.setVersion(mapToMavenSyntax(dependency.getVersion()));
            mavenDependency.setType(type);
            mavenDependency.setScope(scope);
            mavenDependency.setExclusions(getExclusions(dependency, configurations));
            // Deduplicate based on mapped configuration/scope priority
            Optional<Dependency> duplicateDependency = findEqualIgnoreScopeVersionAndExclusions(dependenciesWithPriorities.keySet(), mavenDependency);
            if (!duplicateDependency.isPresent()) {
                // Add if absent
                dependenciesWithPriorities.put(mavenDependency, priority);
            } else if (priority > dependenciesWithPriorities.get(duplicateDependency.get())) {
                // Replace if higher priority
                dependenciesWithPriorities.remove(duplicateDependency.get());
                dependenciesWithPriorities.put(mavenDependency, priority);
            }
        }
    }

    private Optional<Dependency> findEqualIgnoreScopeVersionAndExclusions(Collection<Dependency> dependencies, Dependency candidate) {
        // Ignore scope on purpose
        // Ignore version because Maven don't support dependencies with different versions, even on different scopes
        // Ignore exclusions because we don't know how to choose/merge them
        // Consequence is that we use the elected dependency version and exclusions when de-duplicating
        // Use Maven Dependency "Management Key" as discriminator: groupId:artifactId:type:classifier
        final String candidateManagementKey = candidate.getManagementKey();
        return Iterables.tryFind(dependencies, new Predicate<Dependency>() {
            @Override
            public boolean apply(Dependency dependency) {
                return dependency.getManagementKey().equals(candidateManagementKey);
            }
        });
    }

    private String mapToMavenSyntax(String version) {
        return versionRangeMapper.map(version);
    }

    protected String determineProjectDependencyArtifactId(ProjectDependency dependency) {
        return new ProjectDependencyArtifactIdExtractorHack(dependency).extract();
    }

    private List<Exclusion> getExclusions(ModuleDependency dependency, Set<Configuration> configurations) {
        if (!dependency.isTransitive()) {
            return EXCLUDE_ALL;
        }
        List<Exclusion> mavenExclusions = new ArrayList<Exclusion>();
        Set<ExcludeRule> excludeRules = new HashSet<ExcludeRule>(dependency.getExcludeRules());
        for (Configuration configuration : configurations) {
            excludeRules.addAll(configuration.getExcludeRules());
        }
        for (ExcludeRule excludeRule : excludeRules) {
            Exclusion mavenExclusion = (Exclusion) excludeRuleConverter.convert(excludeRule);
            if (mavenExclusion != null) {
                mavenExclusions.add(mavenExclusion);
            }
        }
        return mavenExclusions;
    }

    private static List<Exclusion> initExcludeAll() {
        Exclusion excludeAll = new Exclusion();
        excludeAll.setGroupId("*");
        excludeAll.setArtifactId("*");
        return Collections.singletonList(excludeAll);
    }

    public ExcludeRuleConverter getExcludeRuleConverter() {
        return excludeRuleConverter;
    }
}
