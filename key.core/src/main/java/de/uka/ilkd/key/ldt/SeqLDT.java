/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.ldt;

import de.uka.ilkd.key.java.Expression;
import de.uka.ilkd.key.java.Services;
import de.uka.ilkd.key.java.abstraction.Type;
import de.uka.ilkd.key.java.expression.Literal;
import de.uka.ilkd.key.java.expression.Operator;
import de.uka.ilkd.key.java.expression.literal.EmptySeqLiteral;
import de.uka.ilkd.key.java.expression.operator.adt.SeqConcat;
import de.uka.ilkd.key.java.expression.operator.adt.SeqGet;
import de.uka.ilkd.key.java.expression.operator.adt.SeqIndexOf;
import de.uka.ilkd.key.java.expression.operator.adt.SeqLength;
import de.uka.ilkd.key.java.expression.operator.adt.SeqPut;
import de.uka.ilkd.key.java.expression.operator.adt.SeqReverse;
import de.uka.ilkd.key.java.expression.operator.adt.SeqSingleton;
import de.uka.ilkd.key.java.expression.operator.adt.SeqSub;
import de.uka.ilkd.key.java.reference.ExecutionContext;
import de.uka.ilkd.key.logic.JTerm;
import de.uka.ilkd.key.logic.TermServices;
import de.uka.ilkd.key.logic.op.SortDependingFunction;

import org.key_project.logic.Name;
import org.key_project.logic.op.Function;
import org.key_project.logic.sort.Sort;
import org.key_project.util.ExtList;

import org.jspecify.annotations.Nullable;


public final class SeqLDT extends LDT {

    public static final Name NAME = new Name("Seq");
    public static final Name SEQGET_NAME = new Name("seqGet");

    // getters
    private final SortDependingFunction seqGet;
    private final Function seqLen;
    private final Function seqIndexOf;

    // constructors
    private final Function seqEmpty;
    private final Function seqSingleton;
    private final Function seqConcat;
    private final Function seqSub;
    private final Function seqReverse;
    private final Function seqUpd;
    private final Function seqDef;
    private final Function values;

    public SeqLDT(TermServices services) {
        super(NAME, services);
        seqGet = addSortDependingFunction(services, "seqGet");
        seqLen = addFunction(services, "seqLen");
        seqEmpty = addFunction(services, "seqEmpty");
        seqSingleton = addFunction(services, "seqSingleton");
        seqConcat = addFunction(services, "seqConcat");
        seqSub = addFunction(services, "seqSub");
        seqReverse = addFunction(services, "seqReverse");
        seqUpd = addFunction(services, "seqUpd");
        seqIndexOf = addFunction(services, "seqIndexOf");
        seqDef = addFunction(services, "seqDef");
        values = addFunction(services, "values");
    }


    public SortDependingFunction getSeqGet(Sort instanceSort, TermServices services) {
        return seqGet.getInstanceFor(instanceSort, services);
    }


    public Function getSeqLen() {
        return seqLen;
    }


    public Function getSeqEmpty() {
        return seqEmpty;
    }


    public Function getSeqSingleton() {
        return seqSingleton;
    }


    public Function getSeqConcat() {
        return seqConcat;
    }


    public Function getSeqSub() {
        return seqSub;
    }

    public Function getSeqUpd() {
        return seqUpd;
    }

    public Function getSeqReverse() {
        return seqReverse;
    }


    public Function getSeqDef() {
        return seqDef;
    }

    /**
     * Placeholder for the sequence of values observed through the execution of an enhanced for
     * loop. Follows David Cok's proposal to adapt JML to Java5.
     */
    public Function getValues() {
        return values;
    }


    @Override
    public boolean isResponsible(Operator op, JTerm[] subs,
            Services services, ExecutionContext ec) {
        return isResponsible(op, (JTerm) null, services, ec);
    }


    @Override
    public boolean isResponsible(Operator op, JTerm left, JTerm right,
            Services services, ExecutionContext ec) {
        return false;
    }


    @Override
    public boolean isResponsible(Operator op, JTerm sub,
            TermServices services, ExecutionContext ec) {
        return op instanceof SeqSingleton || op instanceof SeqConcat || op instanceof SeqSub
                || op instanceof SeqReverse || op instanceof SeqIndexOf || op instanceof SeqGet
                || op instanceof SeqLength || op instanceof SeqPut;
    }


    @Override
    public JTerm translateLiteral(Literal lit, Services services) {
        assert lit instanceof EmptySeqLiteral;
        return services.getTermBuilder().func(seqEmpty);
    }


    @Override
    public Function getFunctionFor(Operator op, Services serv,
            ExecutionContext ec) {
        if (op instanceof SeqSingleton) {
            return seqSingleton;
        } else if (op instanceof SeqConcat) {
            return seqConcat;
        } else if (op instanceof SeqSub) {
            return seqSub;
        } else if (op instanceof SeqReverse) {
            return seqReverse;
        } else if (op instanceof SeqPut) {
            return seqUpd;
        } else if (op instanceof SeqIndexOf) {
            return seqIndexOf;
        } else if (op instanceof SeqGet) {
            return seqGet;
        } else if (op instanceof SeqLength) {
            return seqLen;
        }
        assert false;
        return null;
    }

    @Override
    public @Nullable Function getFunctionFor(String operationName, Services services) {
        if (operationName.equals("add")) {
            return getSeqConcat();
        }
        return null;
    }


    @Override
    public boolean hasLiteralFunction(Function f) {
        return f.equals(seqEmpty);
    }


    @Override
    public Expression translateTerm(JTerm t, ExtList children, Services services) {
        if (t.op().equals(seqEmpty)) {
            return EmptySeqLiteral.INSTANCE;
        }
        assert false;
        return null;
    }


    @Override
    public Type getType(JTerm t) {
        assert false;
        return null;
    }


    public Function getSeqIndexOf() {
        return seqIndexOf;
    }
}
