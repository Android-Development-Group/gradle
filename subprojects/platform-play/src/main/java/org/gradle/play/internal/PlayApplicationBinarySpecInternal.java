/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal;

import org.gradle.language.scala.ScalaLanguageSourceSet;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.internal.toolchain.PlayToolChainInternal;
import org.gradle.play.platform.PlayPlatform;

import java.io.File;

public interface PlayApplicationBinarySpecInternal extends PlayApplicationBinarySpec, BinarySpecInternal {
    void setTargetPlatform(PlayPlatform platform);
    void setToolChain(PlayToolChainInternal toolChain);
    PlayToolChainInternal getToolChain();
    void setJarFile(File file);
    void setGeneratedScala(ScalaLanguageSourceSet scalaSources);
}
