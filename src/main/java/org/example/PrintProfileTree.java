/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.example;

import java.util.Arrays;
import java.util.Iterator;

import org.glowroot.collector.spi.model.ProfileTreeOuterClass.ProfileTree;
import org.glowroot.collector.spi.model.ProfileTreeOuterClass.ProfileTree.LeafThreadState;
import org.glowroot.collector.spi.model.ProfileTreeOuterClass.ProfileTree.ProfileNode;

class PrintProfileTree {

    static void printProfileTree(ProfileTree profileTree) throws Exception {
        for (ProfileNode profileNode : profileTree.getNodeList()) {
            String packageName = profileTree.getPackageName(profileNode.getPackageNameIndex());
            String className = profileTree.getClassName(profileNode.getClassNameIndex());
            String methodName = profileTree.getMethodName(profileNode.getMethodNameIndex());
            String fileName = profileTree.getFileName(profileNode.getFileNameIndex());
            if (!packageName.isEmpty()) {
                className = packageName + '.' + className;
            }
            StringBuffer sb = new StringBuffer();
            String indent = getIndent(profileNode.getDepth());
            sb.append(indent);
            sb.append(new StackTraceElement(className, methodName, fileName,
                    profileNode.getLineNumber()).toString());
            sb.append(", sample count: ");
            sb.append(profileNode.getSampleCount());
            Iterator<Integer> timerNameIndexes = profileNode.getTimerNameIndexList().iterator();
            if (timerNameIndexes.hasNext()) {
                sb.append(", timer names: ");
                while (timerNameIndexes.hasNext()) {
                    sb.append(profileTree.getTimerName(timerNameIndexes.next()));
                    if (timerNameIndexes.hasNext()) {
                        sb.append(", ");
                    }
                }
            }
            System.out.println(sb);
            LeafThreadState leafThreadState = profileNode.getLeafThreadState();
            if (leafThreadState != LeafThreadState.NONE) {
                System.out.println(indent + "  " + leafThreadState.name());
            }
        }
    }

    private static String getIndent(int depth) {
        char[] array = new char[depth + 2];
        Arrays.fill(array, ' ');
        return new String(array);
    }
}
