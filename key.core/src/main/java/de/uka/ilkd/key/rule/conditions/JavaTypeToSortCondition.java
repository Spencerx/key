/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.rule.conditions;

import de.uka.ilkd.key.java.Expression;
import de.uka.ilkd.key.java.Services;
import de.uka.ilkd.key.java.reference.TypeReference;
import de.uka.ilkd.key.logic.JTerm;
import de.uka.ilkd.key.logic.op.JOperatorSV;
import de.uka.ilkd.key.logic.sort.ArraySort;
import de.uka.ilkd.key.logic.sort.GenericSort;
import de.uka.ilkd.key.logic.sort.ProgramSVSort;
import de.uka.ilkd.key.rule.inst.GenericSortCondition;
import de.uka.ilkd.key.rule.inst.SVInstantiations;
import de.uka.ilkd.key.rule.inst.SortException;
import de.uka.ilkd.key.util.Debug;

import org.key_project.logic.LogicServices;
import org.key_project.logic.SyntaxElement;
import org.key_project.logic.op.sv.SchemaVariable;
import org.key_project.logic.sort.Sort;
import org.key_project.prover.rules.VariableCondition;
import org.key_project.prover.rules.instantiation.MatchResultInfo;


/**
 * Variable condition that enforces a given generic sort to be instantiated with the sort of a
 * program expression a schema variable is instantiated with
 */
public final class JavaTypeToSortCondition implements VariableCondition {

    private final JOperatorSV exprOrTypeSV;
    private final GenericSort sort;
    private final boolean elemSort;


    public JavaTypeToSortCondition(final JOperatorSV exprOrTypeSV, final GenericSort sort,
            final boolean elemSort) {
        this.exprOrTypeSV = exprOrTypeSV;
        this.sort = sort;
        this.elemSort = elemSort;

        if (!checkSortedSV(exprOrTypeSV)) {
            throw new RuntimeException("Expected a program schemavariable for expressions");
        }
    }


    public static boolean checkSortedSV(final JOperatorSV exprOrTypeSV) {
        final Sort svSort = exprOrTypeSV.sort();
        return svSort == ProgramSVSort.EXPRESSION || svSort == ProgramSVSort.SIMPLEEXPRESSION
                || svSort == ProgramSVSort.NONSIMPLEEXPRESSION || svSort == ProgramSVSort.TYPE
                || exprOrTypeSV.arity() == 0;
    }


    @Override
    public MatchResultInfo check(SchemaVariable var, SyntaxElement svSubst,
            MatchResultInfo matchCond, LogicServices services) {
        if (var != exprOrTypeSV) {
            return matchCond;
        }

        Debug.assertTrue(svSubst instanceof Expression || svSubst instanceof TypeReference
                || svSubst instanceof JTerm);

        final var inst = (SVInstantiations) matchCond.getInstantiations();
        Sort type;
        if (svSubst instanceof JTerm) {
            type = ((JTerm) svSubst).sort();
        } else if (svSubst instanceof TypeReference) {
            type = ((TypeReference) svSubst).getKeYJavaType().getSort();
        } else {
            final Expression expr = (Expression) svSubst;
            type = expr.getKeYJavaType((Services) services, inst.getExecutionContext()).getSort();
        }
        if (elemSort) {
            if (type instanceof ArraySort) {
                type = ((ArraySort) type).elementSort();
            } else {
                return null;
            }
        }
        try {
            return matchCond.setInstantiations(
                inst.add(GenericSortCondition.createIdentityCondition(sort, type), services));
        } catch (SortException e) {
            return null;
        }
    }


    @Override
    public String toString() {
        return "\\hasSort(" + (elemSort ? "\\elemSort(" + exprOrTypeSV + ")" : exprOrTypeSV) + ", "
            + sort + ")";
    }
}
