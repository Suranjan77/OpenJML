/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.util.CoreUtils;
import jtreg.SkippedException;

/**
 * @test
 * @bug 8193124
 * @summary Test the clhsdb 'findpc' command with Xcomp on live process
 * @requires vm.hasSA
 * @requires vm.compiler1.enabled
 * @requires vm.opt.DeoptimizeALot != true
 * @library /test/lib
 * @run main/othervm/timeout=480 ClhsdbFindPC true false
 */

/**
 * @test
 * @bug 8193124
 * @summary Test the clhsdb 'findpc' command with Xcomp on core file
 * @requires vm.compMode != "Xcomp"
 * @requires vm.hasSA
 * @requires vm.compiler1.enabled
 * @library /test/lib
 * @run main/othervm/timeout=480 ClhsdbFindPC true true
 */

/**
 * @test
 * @bug 8193124
 * @summary Test the clhsdb 'findpc' command w/o Xcomp on live process
 * @requires vm.hasSA
 * @requires vm.compiler1.enabled
 * @requires vm.opt.DeoptimizeALot != true
 * @library /test/lib
 * @run main/othervm/timeout=480 ClhsdbFindPC false false
 */

/**
 * @test
 * @bug 8193124
 * @summary Test the clhsdb 'findpc' command w/o Xcomp on core file
 * @requires vm.compMode != "Xcomp"
 * @requires vm.hasSA
 * @requires vm.compiler1.enabled
 * @library /test/lib
 * @run main/othervm/timeout=480 ClhsdbFindPC false true
 */

public class ClhsdbFindPC {

    private static void testFindPC(boolean withXcomp, boolean withCore) throws Exception {
        LingeredApp theApp = null;
        String coreFileName = null;
        try {
            ClhsdbLauncher test = new ClhsdbLauncher();

            theApp = new LingeredAppWithTrivialMain();
            theApp.setForceCrash(withCore);
            if (withXcomp) {
                LingeredApp.startApp(theApp, "-Xcomp");
            } else {
                LingeredApp.startApp(theApp, "-Xint");
            }
            System.out.print("Started LingeredApp ");
            if (withXcomp) {
                System.out.print("(-Xcomp) ");
            } else {
                System.out.print("(-Xint) ");
            }
            System.out.println("with pid " + theApp.getPid());

            // Get the core file name if we are debugging a core instead of live process
            if (withCore) {
                coreFileName = CoreUtils.getCoreFileLocation(theApp.getOutput().getStdout());
            }

            // Run 'jstack -v' command to get the findpc address
            List<String> cmds = List.of("jstack -v");
            String output;
            if (withCore) {
                output = test.runOnCore(coreFileName, cmds, null, null);
            } else {
                output = test.run(theApp.getPid(), cmds, null, null);
            }

            // Extract pc address from the following line:
            //   - LingeredAppWithTrivialMain.main(java.lang.String[]) @bci=1, line=33, pc=0x00007ff18ff519f0, ...
            String pcAddress = null;
            String[] parts = output.split("LingeredAppWithTrivialMain.main");
            String[] tokens = parts[1].split(" ");
            for (String token : tokens) {
                if (token.contains("pc")) {
                    String[] addresses = token.split("=");
                    // addresses[1] represents the address of the Method
                    pcAddress = addresses[1].replace(",","");
                    break;
                }
            }
            if (pcAddress == null) {
                throw new RuntimeException("Cannot find LingeredAppWithTrivialMain.main pc in output");
            }

            // Test the 'findpc' command passing in the pc obtained from above
            cmds = new ArrayList<String>();
            String cmdStr = "findpc " + pcAddress;
            cmds.add(cmdStr);
            Map<String, List<String>> expStrMap = new HashMap<>();
            if (withXcomp) {
                expStrMap.put(cmdStr, List.of(
                            "In code in NMethod for LingeredAppWithTrivialMain.main",
                            "content:",
                            "oops:",
                            "frame size:"));
            } else {
                expStrMap.put(cmdStr, List.of(
                            "In interpreter codelet"));
            }

<<<<<<< HEAD
            if (withCore) {
                test.runOnCore(coreFileName, cmds, expStrMap, null);
=======
            // Run findpc on a Method*. We can find one in the jstack output. For example:
            // - LingeredApp.steadyState(java.lang.Object) @bci=1, line=33, pc=..., Method*=0x0000008041000208 ...
            // This is testing the PointerFinder support for C++ MetaData types.
            parts = jStackOutput.split("LingeredApp.steadyState");
            parts = parts[1].split("Method\\*=");
            parts = parts[1].split(" ");
            String methodAddr = parts[0];
            cmdStr = "findpc " + methodAddr;
            cmds = List.of(cmdStr);
            expStrMap = new HashMap<>();
            expStrMap.put(cmdStr, List.of("Method ",
                                          "LingeredApp.steadyState",
                                          methodAddr));
            runTest(withCore, cmds, expStrMap);

            // Run findpc on a JavaThread*. We can find one in the jstack output.
            // The tid for a thread is it's JavaThread*. For example:
            //  "main" #1 prio=5 tid=0x00000080263398f0 nid=0x277e0 ...
            // This is testing the PointerFinder support for all C++ types other than MetaData types.
            parts = jStackOutput.split("tid=");
            parts = parts[1].split(" ");
            String tid = parts[0];  // address of the JavaThread
            cmdStr = "findpc " + tid;
            cmds = List.of(cmdStr);
            expStrMap = new HashMap<>();
            expStrMap.put(cmdStr, List.of("Is of type JavaThread"));
            runTest(withCore, cmds, expStrMap);

            // Run findpc on a java stack address. We can find one in the jstack output.
            //   "main" #1 prio=5 tid=... nid=0x277e0 waiting on condition [0x0000008025aef000]
            // The stack address is the last word between the brackets.
            // This is testing the PointerFinder support for thread stack addresses.
            parts = jStackOutput.split("tid=");
            parts = parts[1].split(" \\[");
            parts = parts[1].split("\\]");
            String stackAddress = parts[0];  // address of the thread's stack
            if (Long.decode(stackAddress) == 0L) {
                System.out.println("Stack address is " + stackAddress + ". Skipping test.");
            } else {
                cmdStr = "findpc " + stackAddress;
                cmds = List.of(cmdStr);
                expStrMap = new HashMap<>();
                // Note, sometimes a stack address points to a hotspot type, thus allow for "Is of type".
                expStrMap.put(cmdStr, List.of("(In java stack)|(Is of type)"));
                runTest(withCore, cmds, expStrMap);
            }

            // Run 'examine <addr>' using a thread's tid as the address. The
            // examine output will be the of the form:
            //    <tid>: <value>
            // Where <value> is the word stored at <tid>. <value> also happens to
            // be the vtable address. We then run findpc on this vtable address.
            // This tests PointerFinder support for native C++ symbols.
            cmds = List.of("examine " + tid);
            String examineOutput = runTest(withCore, cmds, null);
            // Extract <value>.
            parts = examineOutput.split(tid + ": ");
            String value = parts[1].split(linesep)[0];
            // Use findpc on <value>. The output should look something like:
            //    Address 0x00007fed86f610b8: vtable for JavaThread + 0x10
            cmdStr = "findpc " + value;
            cmds = List.of(cmdStr);
            expStrMap = new HashMap<>();
            if (Platform.isWindows()) {
                expStrMap.put(cmdStr, List.of("jvm.+JavaThread"));
            } else if (Platform.isOSX()) {
                if (withCore) {
                    expStrMap.put(cmdStr, List.of("__ZTV10JavaThread"));
                } else { // address -> symbol lookups not supported with OSX live process
                    expStrMap.put(cmdStr, List.of("In unknown location"));
                }
>>>>>>> openjdk-src
            } else {
                test.run(theApp.getPid(), cmds, expStrMap, null);
            }
        } catch (SkippedException se) {
            throw se;
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        } finally {
            if (!withCore) {
                LingeredApp.stopApp(theApp);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        boolean withXcomp = Boolean.parseBoolean(args[0]);
        boolean withCore = Boolean.parseBoolean(args[1]);
        System.out.println("Starting the ClhsdbFindPC test");
        testFindPC(withXcomp, withCore);
        System.out.println("Test PASSED");
    }
}
