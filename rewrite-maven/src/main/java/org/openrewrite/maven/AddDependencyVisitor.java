/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.maven;

import lombok.RequiredArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Validated;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.maven.internal.InsertDependencyComparator;
import org.openrewrite.maven.tree.MavenMetadata;
import org.openrewrite.maven.tree.Version;
import org.openrewrite.semver.LatestRelease;
import org.openrewrite.semver.Semver;
import org.openrewrite.semver.VersionComparator;
import org.openrewrite.xml.AddToTagVisitor;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Xml;

import java.util.Comparator;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;

@RequiredArgsConstructor
public class AddDependencyVisitor extends MavenIsoVisitor<ExecutionContext> {
    private static final XPathMatcher DEPENDENCIES_MATCHER = new XPathMatcher("/project/dependencies");

    private final String groupId;
    private final String artifactId;
    private final String version;

    @Nullable
    private final String versionPattern;

    @Nullable
    private final String scope;

    @Nullable
    private final Boolean releasesOnly;

    @Nullable
    private final String type;

    @Nullable
    private final String classifier;

    @Nullable
    private final Boolean optional;

    @Nullable
    private final Pattern familyRegex;

    @Nullable
    private VersionComparator versionComparator;

    @Nullable
    private String resolvedVersion;

    @Override
    public Xml.Document visitDocument(Xml.Document document, ExecutionContext executionContext) {
        Xml.Document maven = super.visitDocument(document, executionContext);

        Validated versionValidation = Semver.validate(version, versionPattern);
        if (versionValidation.isValid()) {
            versionComparator = versionValidation.getValue();
        }

        Xml.Tag root = maven.getRoot();
        if (!root.getChild("dependencies").isPresent()) {
            doAfterVisit(new AddToTagVisitor<>(root, Xml.Tag.build("<dependencies/>"),
                    new MavenTagInsertionComparator(root.getContent() == null ? emptyList() : root.getContent())));
        }

        doAfterVisit(new InsertDependencyInOrder(scope));

        return maven;
    }

    @RequiredArgsConstructor
    private class InsertDependencyInOrder extends MavenVisitor<ExecutionContext> {

        @Nullable
        private final String scope;

        @Override
        public Xml visitTag(Xml.Tag tag, ExecutionContext ctx) {
            if (DEPENDENCIES_MATCHER.matches(getCursor())) {
                String versionToUse = null;

                if (getResolutionResult().getPom().getManagedVersion(groupId, artifactId, type, classifier) == null) {
                    if (familyRegex != null) {
                        versionToUse = findDependencies(d -> familyRegex.matcher(d.getGroupId()).matches()).stream()
                                .max(Comparator.comparing(d -> new Version(d.getVersion())))
                                .map(d -> d.getRequested().getVersion())
                                .orElse(null);
                    }
                    if (versionToUse == null) {
                        versionToUse = findVersionToUse(groupId, artifactId, ctx);
                    }
                }

                Xml.Tag dependencyTag = Xml.Tag.build(
                        "\n<dependency>\n" +
                                "<groupId>" + groupId + "</groupId>\n" +
                                "<artifactId>" + artifactId + "</artifactId>\n" +
                                (versionToUse == null ? "" :
                                        "<version>" + versionToUse + "</version>\n") +
                                (classifier == null ? "" :
                                        "<classifier>" + classifier + "</classifier>\n") +
                                (scope == null || "compile".equals(scope) ? "" :
                                        "<scope>" + scope + "</scope>\n") +
                                (Boolean.TRUE.equals(optional) ? "<optional>true</optional>\n" : "") +
                                "</dependency>"
                );

                doAfterVisit(new AddToTagVisitor<>(tag, dependencyTag,
                        new InsertDependencyComparator(tag.getContent() == null ? emptyList() : tag.getContent(), dependencyTag)));
                maybeUpdateModel();

                return tag;
            }

            return super.visitTag(tag, ctx);
        }

        private String findVersionToUse(String groupId, String artifactId, ExecutionContext ctx) {
            if (resolvedVersion == null) {
                if (versionComparator == null) {
                    resolvedVersion = version;
                } else {
                    MavenMetadata mavenMetadata = downloadMetadata(groupId, artifactId, ctx);
                    LatestRelease latest = new LatestRelease(versionPattern);
                    resolvedVersion = mavenMetadata.getVersioning().getVersions().stream()
                            .filter(v -> versionComparator.isValid(null, v))
                            .filter(v -> !Boolean.TRUE.equals(releasesOnly) || latest.isValid(null, v))
                            .max((v1, v2) -> versionComparator.compare(null, v1, v2))
                            .orElse(version);
                }
            }

            return resolvedVersion;
        }
    }
}
