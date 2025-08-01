/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.symbolic_execution.rule;

import java.util.List;

import de.uka.ilkd.key.java.Services;
import de.uka.ilkd.key.logic.JTerm;
import de.uka.ilkd.key.logic.TermBuilder;
import de.uka.ilkd.key.logic.TermServices;
import de.uka.ilkd.key.logic.op.*;
import de.uka.ilkd.key.proof.Goal;
import de.uka.ilkd.key.proof.mgt.ProofEnvironment;
import de.uka.ilkd.key.rule.BuiltInRule;
import de.uka.ilkd.key.rule.DefaultBuiltInRuleApp;
import de.uka.ilkd.key.rule.IBuiltInRuleApp;
import de.uka.ilkd.key.rule.QueryExpand;
import de.uka.ilkd.key.symbolic_execution.util.SymbolicExecutionSideProofUtil;

import org.key_project.logic.Name;
import org.key_project.logic.op.Function;
import org.key_project.logic.sort.Sort;
import org.key_project.prover.rules.RuleAbortException;
import org.key_project.prover.rules.RuleApp;
import org.key_project.prover.sequent.PIOPathIterator;
import org.key_project.prover.sequent.PosInOccurrence;
import org.key_project.prover.sequent.Sequent;
import org.key_project.prover.sequent.SequentFormula;
import org.key_project.util.collection.ImmutableList;

import org.jspecify.annotations.NonNull;

/**
 * <p>
 * A {@link BuiltInRule} which evaluates a query in a side proof.
 * </p>
 * <p>
 * This rule is applicable on each equality which contains a query:
 * <ul>
 * <li>{@code ...(<something> = <query>)...} or</li>
 * <li>{@code ...(<query> = <something>)...}</li>
 * </ul>
 * </p>
 * <p>
 * The original {@link SequentFormula} which contains the equality is always removed in the
 * following {@link Goal}. How the result of the query computed in the side proof is represented
 * depends on the occurrence of the equality:
 * <ol>
 * <li><b>top level {@code <something> = <query>} or {@code <query> = <something>}</b><br>
 * For each possible result value is a {@link SequentFormula} added to the {@link Sequent} of the
 * form:
 * <ul>
 * <li>Antecedent: {@code <resultCondition> -> <something> = <result>} or</li>
 * <li>Antecedent: {@code <resultCondition> -> <result> = <something>} or</li>
 * <li>Succedent: {@code <resultCondition> & <something> = <result>} or</li>
 * <li>Succedent: {@code <resultCondition> & <result> = <something>}</li>
 * </ul>
 * </li>
 * <li><b>right side of an implication on top level
 * {@code <queryCondition> -> <something> = <query>} or
 * {@code <queryCondition> -> <query> = <something>}</b><br>
 * For each possible result value is a {@link SequentFormula} added to the {@link Sequent} of the
 * form:
 * <ul>
 * <li>Antecedent: {@code
 *
 *

<pre>
 *  -> (<resultCondition> -> <something> = <result>)} or</li>
 * <li>Antecedent: {@code
 *
 *

<pre>
 *  -> (<resultCondition> -> <result> = <something>)} or</li>
 * <li>Succedent: {@code
 *
 *

<pre>
 *  -> (<resultCondition> & <something> = <result>)} or</li>
 * <li>Succedent: {@code
 *
 *

<pre>
 *  -> (<resultCondition> & <result> = <something>)}</li>
 * </ul>
 * </li>
 * <li><b>everywhere else {@code ...(<something> = <query>)...} or
 * {@code ...(<query> = <something>)...}</b><br>
 * In the original {@link SequentFormula} is the {@code <query>} replaced by a new constant function
 * named {@code QueryResult} and added to the antecedent/succedent in which it was contained before.
 * For each possible result value is an additional {@link SequentFormula} added to the
 * <b>antecedent</b> of the form:
 * <ul>
 * <li>{@code <resultCondition> -> QueryResult = <result>} or</li>
 * <li>{@code <resultCondition> -> <result> = QueryResult}</li>
 * </ul>
 * </li>
 * </ol>
 * The side proof uses the default side proof settings (splitting = delayed) and is started via
 * {@link SymbolicExecutionSideProofUtil#startSideProof}. In
 * case that at least one result branch has applicable rules an exception is thrown and the rule is
 * aborted.
 * </p>
 *
 * @author Martin Hentschel
 */
public final class QuerySideProofRule extends AbstractSideProofRule {
    /**
     * The singleton instance of this class.
     */
    public static final QuerySideProofRule INSTANCE = new QuerySideProofRule();

    /**
     * The {@link Name} of this rule.
     */
    private static final Name NAME = new Name("Evaluate Query in Side Proof");

    /**
     * Constructor to forbid multiple instances.
     */
    private QuerySideProofRule() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isApplicable(Goal goal, PosInOccurrence pio) {
        boolean applicable = false;
        if (pio != null) {
            // abort if inside of transformer
            if (Transformer.inTransformer(pio)) {
                return false;
            }
            JTerm term = (JTerm) pio.subTerm();
            if (term != null) {
                if (term.op() == Equality.EQUALS) {
                    applicable = isApplicableQuery(goal, term.sub(0), pio)
                            || isApplicableQuery(goal, term.sub(1), pio);
                }
            }
        }
        return applicable;
    }

    /**
     * Checks if the query term is supported. The functionality is identical to
     * {@link QueryExpand#isApplicable(Goal, PosInOccurrence)}.
     *
     * @param goal The {@link Goal}.
     * @param pmTerm The {@link JTerm} to with the query to check.
     * @param pio The {@link PosInOccurrence} in the {@link Goal}.
     * @return {@code true} is applicable, {@code false} is not applicable
     */
    private boolean isApplicableQuery(Goal goal, JTerm pmTerm,
            PosInOccurrence pio) {
        if (pmTerm.op() instanceof IProgramMethod pm && pmTerm.freeVars().isEmpty()) {
            final Sort nullSort = goal.proof().getJavaInfo().nullSort();
            if (pm.isStatic()
                    || (pmTerm.sub(1).sort().extendsTrans(goal.proof().getJavaInfo().objectSort())
                            && !pmTerm.sub(1).sort().extendsTrans(nullSort))) {
                PIOPathIterator it = pio.iterator();
                while (it.next() != -1) {
                    var focus = it.getSubTerm();
                    if (focus.op() instanceof UpdateApplication
                            || focus.op() instanceof JModality) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuiltInRuleApp createApp(PosInOccurrence pos, TermServices services) {
        return new DefaultBuiltInRuleApp(this, pos);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ImmutableList<Goal> apply(Goal goal, RuleApp ruleApp)
            throws RuleAbortException {
        try {
            // Extract required Terms from goal
            PosInOccurrence pio = ruleApp.posInOccurrence();
            Sequent goalSequent = goal.sequent();
            var equalitySF = pio.sequentFormula();
            JTerm equalityTerm = (JTerm) pio.subTerm();
            JTerm queryTerm;
            JTerm varTerm;
            boolean varFirst;
            if (equalityTerm.sub(0).op() instanceof LocationVariable) {
                queryTerm = equalityTerm.sub(1);
                varTerm = equalityTerm.sub(0);
                varFirst = true;
            } else {
                queryTerm = equalityTerm.sub(0);
                varTerm = equalityTerm.sub(1);
                varFirst = false;
            }
            JTerm queryConditionTerm = null;
            if (equalitySF.formula().op() == Junctor.IMP
                    && equalitySF.formula().sub(1) == equalityTerm) {
                queryConditionTerm = (JTerm) equalitySF.formula().sub(0);
            }
            // Compute sequent for side proof to compute query in.
            // New OneStepSimplifier is required because it has an internal state and the default
            // instance can't be used parallel.
            final ProofEnvironment sideProofEnv = SymbolicExecutionSideProofUtil
                    .cloneProofEnvironmentWithOwnOneStepSimplifier(goal.proof(), true);
            final Services sideProofServices = sideProofEnv.getServicesForEnvironment();
            Sequent sequentToProve = SymbolicExecutionSideProofUtil
                    .computeGeneralSequentToProve(goalSequent, equalitySF);
            Function newPredicate = createResultFunction(sideProofServices, queryTerm.sort());
            JTerm newTerm = sideProofServices.getTermBuilder().func(newPredicate, queryTerm);
            sequentToProve =
                sequentToProve.addFormula(new SequentFormula(newTerm), false, false)
                        .sequent();
            // Compute results and their conditions
            List<ResultsAndCondition> conditionsAndResultsMap =
                computeResultsAndConditions(goal, sideProofEnv, sequentToProve,
                    newPredicate);
            // Create new single goal in which the query is replaced by the possible results
            ImmutableList<Goal> goals = goal.split(1);
            Goal resultGoal = goals.head();
            final var services = goal.getOverlayServices();
            final TermBuilder tb = services.getTermBuilder();
            resultGoal.removeFormula(pio);
            if (pio.isTopLevel() || queryConditionTerm != null) {
                for (ResultsAndCondition conditionsAndResult : conditionsAndResultsMap) {
                    JTerm conditionTerm = tb.and(conditionsAndResult.conditions());
                    JTerm newEqualityTerm =
                        varFirst ? tb.equals(varTerm, conditionsAndResult.result())
                                : tb.equals(conditionsAndResult.result(), varTerm);
                    JTerm resultTerm = pio.isInAntec() ? tb.imp(conditionTerm, newEqualityTerm)
                            : tb.and(conditionTerm, newEqualityTerm);
                    if (queryConditionTerm != null) {
                        resultTerm = tb.imp(queryConditionTerm, resultTerm);
                    }
                    resultGoal.addFormula(new SequentFormula(resultTerm), pio.isInAntec(), false);
                }
            } else {
                Function resultFunction = createResultConstant(services, varTerm.sort());
                JTerm resultFunctionTerm = tb.func(resultFunction);
                resultGoal.addFormula(
                    replace(pio,
                        tb.equals(resultFunctionTerm, varTerm),
                        services),
                    pio.isInAntec(), false);
                for (ResultsAndCondition conditionsAndResult : conditionsAndResultsMap) {
                    JTerm conditionTerm = tb.and(conditionsAndResult.conditions());
                    JTerm resultTerm = tb.imp(conditionTerm,
                        varFirst ? tb.equals(resultFunctionTerm, conditionsAndResult.result())
                                : tb.equals(conditionsAndResult.result(), resultFunctionTerm));
                    resultGoal.addFormula(new SequentFormula(resultTerm), true, false);
                }
            }
            return goals;
        } catch (Exception e) {
            throw new RuleAbortException(e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Name name() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String displayName() {
        return NAME.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return displayName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isApplicableOnSubTerms() {
        return true;
    }
}
