/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.speclang;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import de.uka.ilkd.key.java.Services;
import de.uka.ilkd.key.java.abstraction.KeYJavaType;
import de.uka.ilkd.key.logic.JTerm;
import de.uka.ilkd.key.logic.op.IObserverFunction;
import de.uka.ilkd.key.logic.op.LocationVariable;
import de.uka.ilkd.key.proof.init.ContractPO;
import de.uka.ilkd.key.proof.init.InitConfig;
import de.uka.ilkd.key.proof.init.ProofOblInput;

import org.key_project.util.collection.ImmutableList;
import org.key_project.util.collection.ImmutableSLList;

/**
 * A contractual agreement about an ObserverFunction.
 */
public interface Contract extends SpecificationElement {

    int INVALID_ID = Integer.MIN_VALUE;

    /**
     *
     * @return {@code true} if any only if this contract does not necessarily need to be proven in
     *         its own proof obligation. E.g., this is true for {@link FunctionalBlockContract}s and
     *         {@link FunctionalLoopContract}s.
     */
    default boolean isAuxiliary() {
        return false;
    }

    /**
     * Returns the id number of the contract. If a contract has instances for several methods
     * (inheritance!), all instances have the same id. The id is either non-negative or equal to
     * INVALID_ID.
     */
    int id();

    /**
     * Returns the contracted function symbol.
     */
    IObserverFunction getTarget();

    /**
     * Tells whether the contract contains a measured_by clause.
     */
    boolean hasMby();

    /**
     * Returns the original LocationVariables to replace them easier. The second list consists of
     * the
     * parameters.
     */
    OriginalVariables getOrigVars();

    /**
     * Returns the precondition of the contract.
     *
     * @param heap heap variable
     * @param selfVar self variable
     * @param paramVars parameter variables
     * @param atPreVars variables at previous heap
     * @param services services object
     * @return precondition
     */
    JTerm getPre(LocationVariable heap, LocationVariable selfVar,
            ImmutableList<LocationVariable> paramVars,
            Map<LocationVariable, LocationVariable> atPreVars, Services services);

    /**
     * Returns the precondition of the contract.
     *
     * @param heapContext heap context
     * @param selfVar self variable
     * @param paramVars parameter variables
     * @param atPreVars variables at previous heap
     * @param services services object
     * @return precondition
     */
    JTerm getPre(List<LocationVariable> heapContext, LocationVariable selfVar,
            ImmutableList<LocationVariable> paramVars,
            Map<LocationVariable, LocationVariable> atPreVars, Services services);

    /**
     * Returns the precondition of the contract.
     *
     * @param heap heap variable
     * @param heapTerm heap term
     * @param selfTerm self term
     * @param paramTerms parameter terms
     * @param atPres terms of variables at previous heap
     * @param services services object
     * @return the precondition
     */
    JTerm getPre(LocationVariable heap, JTerm heapTerm, JTerm selfTerm,
            ImmutableList<JTerm> paramTerms, Map<LocationVariable, JTerm> atPres,
            Services services);

    /**
     * Returns the precondition of the contract.
     *
     * @param heapContext heap context
     * @param heapTerms heap terms
     * @param selfTerm term of self variable
     * @param paramTerms terms of parameter variables
     * @param atPres terms of variables at previous heap
     * @param services services object
     * @return the precondition
     */
    JTerm getPre(List<LocationVariable> heapContext, Map<LocationVariable, JTerm> heapTerms,
            JTerm selfTerm, ImmutableList<JTerm> paramTerms, Map<LocationVariable, JTerm> atPres,
            Services services);

    /**
     * Returns the dependency set of the contract.
     *
     * @param heap the heap variable
     * @param atPre boolean whether old heap should be used
     * @param selfVar self variable
     * @param paramVars parameter variables
     * @param atPreVars variables at previous heap
     * @param services services object
     * @return the dependency set
     */
    JTerm getDep(LocationVariable heap, boolean atPre, LocationVariable selfVar,
            ImmutableList<LocationVariable> paramVars,
            Map<LocationVariable, LocationVariable> atPreVars, Services services);

    /**
     * Returns the dependency set of the contract.
     *
     * @param heap the heap variable
     * @param atPre boolean whether old heap should be used
     * @param heapTerm the heap variable term
     * @param selfTerm term of self variable
     * @param paramTerms terms of parameter variables
     * @param atPres terms of variables at previous heap
     * @param services services object
     * @return the dependency set
     */
    JTerm getDep(LocationVariable heap, boolean atPre, JTerm heapTerm, JTerm selfTerm,
            ImmutableList<JTerm> paramTerms, Map<LocationVariable, JTerm> atPres,
            Services services);

    JTerm getRequires(LocationVariable heap);

    JTerm getModifiable(LocationVariable heap);

    JTerm getAccessible(LocationVariable heap);

    JTerm getGlobalDefs();

    JTerm getGlobalDefs(LocationVariable heap, JTerm heapTerm, JTerm selfTerm,
            ImmutableList<JTerm> paramTerms, Services services);

    JTerm getMby();

    /**
     * Returns the measured_by clause of the contract.
     *
     * @param selfVar the self variable
     * @param paramVars the parameter variables
     * @param services services object
     * @return the measured-by term
     */
    JTerm getMby(LocationVariable selfVar, ImmutableList<LocationVariable> paramVars,
            Services services);

    /**
     * Returns the measured_by clause of the contract.
     *
     * @param heapTerms terms for the heap context
     * @param selfTerm term of self variable
     * @param paramTerms terms of parameter variables
     * @param atPres terms of variables at previous heap
     * @param services services object
     * @return the measured-by term
     */
    JTerm getMby(Map<LocationVariable, JTerm> heapTerms, JTerm selfTerm,
            ImmutableList<JTerm> paramTerms, Map<LocationVariable, JTerm> atPres,
            Services services);

    /**
     * Returns the contract in pretty HTML format.
     *
     * @param services services instance
     * @return the html representation
     */
    String getHTMLText(Services services);

    /**
     * Returns the contract in pretty plain text format.
     *
     * @param services services instance
     * @return the plain text representation
     */
    String getPlainText(Services services);

    /**
     * Tells whether, on saving a proof where this contract is available, the contract should be
     * saved too. (this is currently true for contracts specified directly in DL, but not for JML
     * contracts)
     *
     * @return see above
     */
    boolean toBeSaved();

    boolean transactionApplicableContract();

    /**
     * Returns a parseable String representation of the contract. Precondition: toBeSaved() must be
     * true.
     *
     * @param services the services instance
     * @return the (parseable) String representation
     */
    String proofToString(Services services);

    /**
     * Returns a proof obligation to the passed initConfig.
     *
     * @param initConfig the initial configuration
     * @return the proof obligation
     */
    ContractPO createProofObl(InitConfig initConfig);

    /**
     * Lookup the proof obligation belonging to the contract in the specification repository.
     *
     * @param services the services instance
     * @return the proof obligation according to the specification repository
     */
    ProofOblInput getProofObl(Services services);

    /**
     * Returns a proof obligation to the passed contract and initConfig.
     *
     * @param initConfig the initial configuration
     * @param contract the contract
     * @return the proof obligation
     */
    ProofOblInput createProofObl(InitConfig initConfig, Contract contract);

    /**
     * Returns a proof obligation to the passed contract and initConfig.
     *
     * @param initConfig the initial configuration
     * @param contract the contract
     * @param supportSymbolicExecutionAPI boolean saying whether symbolic execution api is supported
     * @return the proof obligation
     */
    ProofOblInput createProofObl(InitConfig initConfig, Contract contract,
            boolean supportSymbolicExecutionAPI);

    /**
     * Returns a contract which is identical this contract except that the id is set to the new id.
     *
     * @param newId the new id value
     * @return an identical contract with the new id
     */
    Contract setID(int newId);

    /**
     * Returns a contract which is identical to this contract except that the KeYJavaType and
     * IObserverFunction are set to the new values.
     *
     * @param newKJT the new KeYJavaType
     * @param newPM the new observer function
     * @return an identical contract with the new KJT and PM (see above)
     */
    Contract setTarget(KeYJavaType newKJT, IObserverFunction newPM);

    /**
     * Returns technical name for the contract type.
     *
     * @return the technical name
     */
    String getTypeName();

    /**
     * Checks if a self variable is originally provided.
     *
     * @return {@code true} self variable is originally provided, {@code false} no self variable
     *         available.
     */
    boolean hasSelfVar();

    @Override
    Contract map(UnaryOperator<JTerm> op, Services services);

    /**
     * Class for storing the original variables without always distinguishing several different
     * cases depending on which variables are present/needed in order to provide a general
     * interface. At the moment only used for well-definedness checks (as those refer to already
     * existing structures).
     *
     * @author Michael Kirsten
     */
    final class OriginalVariables {

        public final LocationVariable self;
        public final LocationVariable result;
        public final LocationVariable exception;
        public final Map<LocationVariable, LocationVariable> atPres;
        public final ImmutableList<LocationVariable> params;

        /**
         * Create new instance of original variables
         *
         * @param selfVar the original self variable
         * @param resVar the original result variable
         * @param excVar the original exception variable
         * @param atPreVars the original atPreVars
         * @param paramVars the original parameter variables
         */
        @SuppressWarnings("unchecked")
        public OriginalVariables(LocationVariable selfVar, LocationVariable resVar,
                LocationVariable excVar,
                Map<LocationVariable, LocationVariable> atPreVars,
                ImmutableList<LocationVariable> paramVars) {
            this.self = selfVar;
            this.result = resVar;
            this.exception = excVar;
            this.atPres = atPreVars;
            if (paramVars == null) {
                this.params = ImmutableSLList.nil();
            } else {
                this.params = paramVars;
            }
        }

        /**
         * Adds a list of parameters and deletes the prior ones (if any).
         *
         * @param newParams
         * @return the changed original variables
         */
        public OriginalVariables add(ImmutableList<LocationVariable> newParams) {
            return new OriginalVariables(self, result, exception, atPres, newParams);
        }
    }
}
