/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.maven.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VersionRequirementTest {
    private Iterable<String> available() {
        return List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
    }

    @Test
    void rangeSet() {
        assertThat(VersionRequirement.fromVersion("[1,11)", 0).resolve(this::available))
          .isEqualTo("10");
    }

    @Test
    void multipleSoftRequirements() {
        assertThat(VersionRequirement.fromVersion("1", 1).addRequirement("2").resolve(this::available))
          .isEqualTo("1");
    }

    @Test
    void softRequirementThenHardRequirement() {
        assertThat(VersionRequirement.fromVersion("1", 1).addRequirement("[1,11]")
          .resolve(this::available))
          .isEqualTo("10");
    }

    @Test
    void hardRequirementThenSoftRequirement() {
        assertThat(VersionRequirement.fromVersion("[1,11]", 1).addRequirement("1")
          .resolve(this::available))
          .isEqualTo("10");
    }

    @Test
    void nearestRangeWins() {
        assertThat(VersionRequirement.fromVersion("[1,2]", 1).addRequirement("[9,10]")
          .resolve(this::available))
          .isEqualTo("2");
    }
}
