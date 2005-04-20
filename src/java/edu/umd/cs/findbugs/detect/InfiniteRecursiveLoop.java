/*
 * FindBugs - Find bugs in Java programs
 * Copyright (C) 2004, Tom Truscott <trt@unx.sas.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package edu.umd.cs.findbugs.detect;

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.StatelessDetector;
import edu.umd.cs.findbugs.visitclass.Constants2;

public class InfiniteRecursiveLoop extends BytecodeScanningDetector implements Constants2, StatelessDetector {

	private BugReporter bugReporter;
	private boolean seenTransferOfControl;
	private boolean seenReturn;
	private boolean seenStateChange;
	private int largestBranchTarget;

	private final static boolean DEBUG = false;
	public InfiniteRecursiveLoop(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}

	public void visit(JavaClass obj) {
	}

	int parameters;
	public void visit(Method obj) {
		seenTransferOfControl = false;
		seenStateChange = false;
		seenReturn = false;
		largestBranchTarget = -1;
                parameters = stack.resetForMethodEntry(this);
		if (DEBUG ) {
		System.out.println();
		System.out.println(" --- " + getFullyQualifiedMethodName());
		System.out.println();
		}
	}

	public void sawBranchTo(int seen) {
		if (largestBranchTarget < seen)
			largestBranchTarget = seen;
		seenTransferOfControl = true;
		}


	OpcodeStack stack = new OpcodeStack();


	/** Signal an infinite loop if either:
	 * we see a call to the same method with the same parameters, or
	 * we see a call to the same (dynamically dispatched method), and there
	 * has been no transfer of control.	
	 */
	public void sawOpcode(int seen) {
		
		if (seenReturn && seenTransferOfControl && seenStateChange) return;

	
		if (DEBUG ) {
		System.out.println(stack);	
		System.out.println(getPC() + " : " + OPCODE_NAMES[seen]);
		}

	
		if ((seen == INVOKEVIRTUAL || seen == INVOKEINTERFACE)
			    && getNameConstantOperand().equals("add")
			    && getSigConstantOperand().equals(
				"(Ljava/lang/Object;)Z")
			    && stack.getStackDepth() >= 1)  {
				OpcodeStack.Item it0 = stack.getStackItem(0);
				int r0 = it0.getRegisterNumber();
				OpcodeStack.Item it1 = stack.getStackItem(1);
				int r1 = it1.getRegisterNumber();
				if (r0 == r1 && r0 > 0)
				bugReporter.reportBug(new BugInstance(this, "IL_CONTAINER_ADDED_TO_ITSELF", NORMAL_PRIORITY)
				        .addClassAndMethod(this)
				        .addSourceLine(this)
					);
		}


		if ((seen == INVOKEVIRTUAL || seen == INVOKESPECIAL || seen == INVOKEINTERFACE || seen == INVOKESTATIC) 
			    && getNameConstantOperand().equals(getMethodName())
			    && getSigConstantOperand().equals(getMethodSig())
			    && ((seen == INVOKESTATIC) == getMethod().isStatic())
			    && stack.getStackDepth() >= parameters)  {
		if (DEBUG ) {
		System.out.println("IL: Checking...");
		System.out.println(getClassConstantOperand() + "." + getNameConstantOperand() + " : " + getSigConstantOperand());
		System.out.println("vs. " + getClassName() + "." + getMethodName() + " : " + getMethodSig());
		}
		if ( getClassConstantOperand().equals(getClassName())
				|| seen == INVOKEVIRTUAL || seen == INVOKEINTERFACE) {
			// Invocation of same method
			// Now need to see if parameters are the same
			int firstParameter = 0;
			if (getMethodName().equals("<init>")) firstParameter = 1;
			
			boolean match1 = !seenStateChange;
			for(int i = firstParameter; match1 && i < parameters; i++) {
				OpcodeStack.Item it = stack.getStackItem(parameters-1-i);
				if (!it.isInitialParameter() || it.getRegisterNumber() != i)
					match1 = false;
				}
			boolean sameMethod = 
				seen == INVOKESTATIC
				|| getNameConstantOperand().equals("<init>");
			if (!sameMethod) {
				// Have to check if first parmeter is the same
				// know there must be a this argument
				OpcodeStack.Item p = stack.getStackItem(parameters-1);
				sameMethod = p.isInitialParameter()
					&& p.getRegisterNumber() == 0;
				}
			boolean match2 = sameMethod && !seenTransferOfControl;
			boolean match3 = sameMethod && !seenReturn && largestBranchTarget < getPC();
			if (match1 || match2 || match3)  
				bugReporter.reportBug(new BugInstance(this, "IL_INFINITE_RECURSIVE_LOOP", HIGH_PRIORITY)
				        .addClassAndMethod(this)
				        .addSourceLine(this)
					);
			}
		}

               switch(seen) {
                       case ARETURN:
                       case IRETURN:
                       case LRETURN:
                       case RETURN:
                       case DRETURN:
                       case FRETURN:
				seenReturn = true;
                               seenTransferOfControl = true;
				break;
			case PUTSTATIC:
			case PUTFIELD:
			case IASTORE:
			case AASTORE:
			case DASTORE:
			case FASTORE:
			case LASTORE:
			case SASTORE:
			case CASTORE:
			case BASTORE:
			case INVOKEVIRTUAL:
			case INVOKESPECIAL:
			case INVOKEINTERFACE:
			case INVOKESTATIC:
				seenStateChange = true;
				break;
                       }
		stack.sawOpcode(this,seen);
	}

}
