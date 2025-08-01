/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.strategy.quantifierHeuristics;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import de.uka.ilkd.key.java.NameAbstractionTable;
import de.uka.ilkd.key.java.Services;
import de.uka.ilkd.key.logic.BooleanContainer;
import de.uka.ilkd.key.logic.JTerm;
import de.uka.ilkd.key.logic.equality.RenamingTermProperty;
import de.uka.ilkd.key.logic.label.TermLabelState;
import de.uka.ilkd.key.logic.op.ProgramVariable;

import org.key_project.logic.Term;
import org.key_project.logic.op.Operator;
import org.key_project.logic.op.QuantifiableVariable;
import org.key_project.logic.op.sv.SchemaVariable;
import org.key_project.logic.sort.Sort;
import org.key_project.util.LRUCache;
import org.key_project.util.collection.DefaultImmutableSet;
import org.key_project.util.collection.ImmutableList;
import org.key_project.util.collection.ImmutableSLList;
import org.key_project.util.collection.ImmutableSet;

import static de.uka.ilkd.key.logic.equality.RenamingSourceElementProperty.RENAMING_SOURCE_ELEMENT_PROPERTY;


/**
 * This class implements a persistent constraint. The constraint contains pairs of Metavariables X
 * and Terms t meaning X=t. It offers services like joining two Constraint objects, adding new
 * constraints to this one by unfying two terms and creating all necessary Metavariable - Term
 * pairs. There are no public constructors to build up a new Constraint use the BOTTOM constraint of
 * the Constraint interface (static final class variable) and add the needed constraints. If a
 * constraint would not be satisfiable (cycles, unification failed) the Constraint TOP of interface
 * Constraint is returned.
 */
@Deprecated
public class EqualityConstraint implements Constraint {

    /** contains a boolean value */
    private static final BooleanContainer CONSTRAINTBOOLEANCONTAINER = new BooleanContainer();

    /**
     * stores constraint content as a mapping from Metavariable to Term
     */
    private final HashMap<Metavariable, JTerm> map;

    /** cache for return values of getInstantiation */
    private HashMap<Metavariable, JTerm> instantiationCache = null;

    private Integer hashCode = null;

    /** Don't use this constructor, use Constraint.BOTTOM instead */
    public EqualityConstraint() {
        this(new LinkedHashMap<>());
    }

    private EqualityConstraint(HashMap<Metavariable, JTerm> map) {
        this.map = map;
    }


    public static ImmutableSet<Metavariable> metaVars(Term t,
            Services services) {

        var mvCache = services.getCaches().getMVCache();

        if (mvCache.containsKey(t)) {
            return mvCache.get(t);
        }

        ImmutableSet<Metavariable> metaVars = DefaultImmutableSet.nil();

        var op = t.op();

        if (op instanceof Metavariable) {
            metaVars = metaVars.add((Metavariable) op);
        }
        for (int i = 0, ar = t.arity(); i < ar; i++) {
            metaVars = metaVars.union(metaVars(t.sub(i), services));
        }

        synchronized (mvCache) {
            final ImmutableSet<Metavariable> result = mvCache.putIfAbsent(t, metaVars);
            if (result != null) {
                return result;
            }
        }

        return metaVars;
    }

    @Override
    protected synchronized Object clone() {
        EqualityConstraint res = new EqualityConstraint((HashMap<Metavariable, JTerm>) map.clone());
        res.instantiationCache = instantiationCache == null ? null
                : (HashMap<Metavariable, JTerm>) instantiationCache.clone();
        return res;
    }

    /**
     * returns true if Bottom
     *
     * @return true if Bottom
     */
    @Override
    final public boolean isBottom() {
        return map.isEmpty();
    }

    /**
     * a constraint being instance of this class is satisfiable. If a method realizes that an
     * unsatisfiable Constraint would be built because of failed unification, cycle or s.th. similar
     * it returns the singleton TOP being instance of the subclass Top
     *
     * @return true always
     */
    @Override
    final public boolean isSatisfiable() {
        return true;
    }


    /**
     * @return list of metavariables, instantiations of which may restricted by this constraint
     */
    public Iterator<Metavariable> restrictedMetavariables() {
        return Collections.unmodifiableSet(map.keySet()).iterator();
    }

    /**
     * @return The most general known term that is more defining than p_mv itself by which p_mv can
     *         be replaced if the constraint is valid (or null if the constraint allows arbitrary
     *         instantiations of p_mv). This is just the entry of map.
     */
    public JTerm getDirectInstantiation(Metavariable p_mv) {
        return map.get(p_mv);
    }


    /**
     * Find a term the given metavariable can be instantiated with which is consistent with every
     * instantiation that satisfies this constraint (that means, the term such an instantiation
     * substitutes the metavariable with can always be unified with the returned term).
     *
     * @param p_mv the Metavariable
     * @param services the Services
     * @return a term the given metavariable can be instantiated with
     */
    @Override
    public synchronized JTerm getInstantiation(Metavariable p_mv, Services services) {
        JTerm t = null;
        if (instantiationCache == null) {
            instantiationCache = new LinkedHashMap<>();
        } else {
            t = instantiationCache.get(p_mv);
        }

        if (t == null) {
            t = map.get(p_mv);
            if (t == null) {
                t = services.getTermBuilder().var(p_mv);
            } else {
                t = instantiate(t, services);
            }

            instantiationCache.put(p_mv, t);
        }

        return t;
    }

    private synchronized JTerm getInstantiationIfExisting(Metavariable p_mv) {
        if (instantiationCache == null) {
            return null;
        }
        return instantiationCache.get(p_mv);
    }

    /**
     * instantiates term <code>p</code> according to the instantiations of the metavariables
     * described by this constraint.
     *
     * @param p the Term p to be instantiated
     * @return the instantiated term
     */
    private JTerm instantiate(JTerm p, Services services) {

        ConstraintAwareSyntacticalReplaceVisitor srVisitor =
            new ConstraintAwareSyntacticalReplaceVisitor(new TermLabelState(), services, this, null,
                null, null, null);
        p.execPostOrder(srVisitor);
        return srVisitor.getTerm();
    }


    /**
     * unifies terms t1 and t2
     *
     * @param t1 Term to be unified
     * @param t2 term to be unified
     * @param services the Services providing access to the type model (e.g. necessary when
     *        introducing intersection sorts)
     * @return TOP if not possible, else a new constraint with after unification of t1 and t2
     */
    @Override
    public Constraint unify(JTerm t1, JTerm t2, Services services) {
        return unify(t1, t2, services, CONSTRAINTBOOLEANCONTAINER);
    }

    /**
     * executes unification for terms t1 and t2.
     *
     * @param t1 Term to be unfied
     * @param t2 Term to be unfied
     * @param services the Services providing access to the type model (e.g. necessary when
     *        introducing intersection sorts)
     * @param unchanged true iff the new constraint equals this one
     * @return TOP if not possible, else a new constraint unifying t1 and t2 ( == this iff this
     *         subsumes the unification )
     */
    @Override
    public Constraint unify(JTerm t1, JTerm t2, Services services, BooleanContainer unchanged) {
        final Constraint newConstraint = unifyHelp(t1, t2, false, services);

        if (!newConstraint.isSatisfiable()) {
            unchanged.setVal(false);
            return TOP;
        }

        if (newConstraint == this) {
            unchanged.setVal(true);
            return this;
        }

        unchanged.setVal(false);
        return newConstraint;
    }

    /**
     * compare two quantifiable variables if they are equal modulo renaming
     *
     * @param ownVar first QuantifiableVariable to be compared
     * @param cmpVar second QuantifiableVariable to be compared
     * @param ownBoundVars variables bound above the current position
     * @param cmpBoundVars variables bound above the current position
     */
    private static boolean compareBoundVariables(QuantifiableVariable ownVar,
            QuantifiableVariable cmpVar, ImmutableList<QuantifiableVariable> ownBoundVars,
            ImmutableList<QuantifiableVariable> cmpBoundVars) {

        final int ownNum = indexOf(ownVar, ownBoundVars);
        final int cmpNum = indexOf(cmpVar, cmpBoundVars);

        if (ownNum == -1 && cmpNum == -1)
        // if both variables are not bound the variables have to be the
        // same object
        {
            return ownVar == cmpVar;
        }

        // otherwise the variables have to be bound at the same point (and both
        // be bound)
        return ownNum == cmpNum;
    }


    /**
     * @return the index of the first occurrence of <code>var</code> in <code>list</code>, or
     *         <code>-1</code> if the variable is not an element of the list
     */
    private static int indexOf(QuantifiableVariable var,
            ImmutableList<QuantifiableVariable> list) {
        int res = 0;
        while (!list.isEmpty()) {
            if (list.head() == var) {
                return res;
            }
            ++res;
            list = list.tail();
        }
        return -1;
    }


    /**
     * Compares two terms modulo bound renaming and return a (possibly new) constraint object that
     * holds the instantiations necessary to make the two terms equal.
     *
     * @param t0 the first term
     * @param t1 the second term
     * @param ownBoundVars variables bound above the current position
     * @param cmpBoundVars variables bound above the current position
     * @param modifyThis <code>this</code> is an object that has just been created during this
     *        unification process
     * @param services the Services providing access to the type model (e.g. necessary when
     *        introducing intersection sorts). Value <code>null</code> is allowed, but unification
     *        fails (i.e. @link Constraint#TOP is returned), if e.g. intersection sorts are
     *        required.
     * @return a constraint under which t0, t1 are equal modulo bound renaming. <code>this</code> is
     *         returned iff the terms are equal modulo bound renaming under <code>this</code>, or
     *         <code>modifyThis==true</code> and the terms are unifiable. For
     *         <code>!modifyThis</code> a new object is created, and <code>this</code> is never
     *         modified. <code>Constraint.TOP</code> is always returned for ununifiable terms
     */
    private Constraint unifyHelp(JTerm t0, JTerm t1,
            ImmutableList<QuantifiableVariable> ownBoundVars,
            ImmutableList<QuantifiableVariable> cmpBoundVars, NameAbstractionTable nat,
            boolean modifyThis, Services services) {

        if (t0 == t1 && ownBoundVars.equals(cmpBoundVars)) {
            return this;
        }

        final Operator op0 = t0.op();

        if (op0 instanceof QuantifiableVariable) {
            return handleQuantifiableVariable(t0, t1, ownBoundVars, cmpBoundVars);
        }

        final Operator op1 = t1.op();

        if (op1 instanceof Metavariable) {
            if (op0 == op1) {
                return this;
            }

            if (op0 instanceof Metavariable) {
                return handleTwoMetavariables(t0, t1, modifyThis, services);
            }

            if (t0.sort().extendsTrans(t1.sort())) {
                return normalize((Metavariable) op1, t0, modifyThis, services);
            }

            return TOP;
        } else if (op0 instanceof Metavariable) {
            if (t1.sort().extendsTrans(t0.sort())) {
                return normalize((Metavariable) op0, t1, modifyThis, services);
            }

            return TOP;
        }

        if (!(op0 instanceof ProgramVariable) && op0 != op1) {
            return TOP;
        }


        if (t0.sort() != t1.sort() || t0.arity() != t1.arity()) {
            return TOP;
        }


        nat = handleJava(t0, t1, nat);
        if (nat == FAILED) {
            return TOP;
        }


        return descendRecursively(t0, t1, ownBoundVars, cmpBoundVars, nat, modifyThis, services);
    }

    /**
     * Resolve an equation <tt>X=Y</tt> (concerning two metavariables) by introducing a third
     * variable <tt>Z</tt> whose sort is the intersection of the sorts of <tt>X</tt>,<tt>Y</tt> and
     * adding equations <tt>X=Z</tt>,<tt>Y=Z</tt>. NB: This method must only be called if none of
     * the sorts of <code>t0</code>,<code>t1</code> is subsort of the other one. Otherwise the
     * resulting equation might get commuted, <tt>Z</tt> might occur on the left side of the new
     * equations and horrible things will happen.
     *
     * @param t0
     * @param t1
     * @param services
     * @return the constraint
     */
    private Constraint introduceNewMV(JTerm t0, JTerm t1, boolean modifyThis, Services services) {
        /*
         * if (services == null) return Constraint.TOP;
         *
         * final ImmutableSet<Sort> set =
         * DefaultImmutableSet.<Sort>nil().add(t0.sort()).add(t1.sort());
         */
        // assert false : "metavariables disabled";
        return TOP;
        // final Sort intersectionSort =
        // IntersectionSort.getIntersectionSort(set, services);
        //
        // if (intersectionSort == null) {
        // return Constraint.TOP;
        // }
        //
        // // I think these MV will never occur in saved proofs, or?
        //
        // final Metavariable newMV =
        // new Metavariable(new Name("#MV"+(MV_COUNTER++)), intersectionSort);
        // final Term newMVTerm = TermFactory.DEFAULT.createFunctionTerm(newMV);
        //
        // final Constraint addFirst = normalize ( (Metavariable)t0.op (),
        // newMVTerm,
        // modifyThis,
        // services );
        // if ( !addFirst.isSatisfiable () ) return Constraint.TOP;
        // return ( (EqualityConstraint)addFirst )
        // .normalize ( (Metavariable)t1.op (),
        // newMVTerm,
        // modifyThis || addFirst != this,
        // services );
    }


    /**
     * used to encode that <tt>handleJava</tt> results in an unsatisfiable constraint (faster than
     * using exceptions)
     */
    private static final NameAbstractionTable FAILED = new NameAbstractionTable();

    private static NameAbstractionTable handleJava(JTerm t0, JTerm t1, NameAbstractionTable nat) {


        if (!t0.javaBlock().isEmpty() || !t1.javaBlock().isEmpty()) {
            nat = checkNat(nat);
            if (RenamingTermProperty.javaBlocksNotEqualModRenaming(t0.javaBlock(), t1.javaBlock(),
                nat)) {
                return FAILED;
            }
        }

        if (!(t0.op() instanceof SchemaVariable) && t0.op() instanceof ProgramVariable) {
            if (!(t1.op() instanceof ProgramVariable)) {
                return FAILED;
            }
            nat = checkNat(nat);
            if (!((ProgramVariable) t0.op()).equalsModProperty(t1.op(),
                RENAMING_SOURCE_ELEMENT_PROPERTY, nat)) {
                return FAILED;
            }
        }

        return nat;
    }

    private Constraint descendRecursively(JTerm t0, JTerm t1,
            ImmutableList<QuantifiableVariable> ownBoundVars,
            ImmutableList<QuantifiableVariable> cmpBoundVars, NameAbstractionTable nat,
            boolean modifyThis, Services services) {
        Constraint newConstraint = this;

        for (int i = 0; i < t0.arity(); i++) {
            ImmutableList<QuantifiableVariable> subOwnBoundVars = ownBoundVars;
            ImmutableList<QuantifiableVariable> subCmpBoundVars = cmpBoundVars;

            if (t0.varsBoundHere(i).size() != t1.varsBoundHere(i).size()) {
                return TOP;
            }
            for (int j = 0; j < t0.varsBoundHere(i).size(); j++) {
                final QuantifiableVariable ownVar = t0.varsBoundHere(i).get(j);
                final QuantifiableVariable cmpVar = t1.varsBoundHere(i).get(j);
                if (ownVar.sort() != cmpVar.sort()) {
                    return TOP;
                }

                subOwnBoundVars = subOwnBoundVars.prepend(ownVar);
                subCmpBoundVars = subCmpBoundVars.prepend(cmpVar);
            }

            newConstraint = ((EqualityConstraint) newConstraint).unifyHelp(t0.sub(i), t1.sub(i),
                subOwnBoundVars, subCmpBoundVars, nat, modifyThis, services);

            if (!newConstraint.isSatisfiable()) {
                return TOP;
            }
            modifyThis = modifyThis || newConstraint != this;
        }

        return newConstraint;
    }

    private static NameAbstractionTable checkNat(NameAbstractionTable nat) {
        if (nat == null) {
            return new NameAbstractionTable();
        }
        return nat;
    }

    private Constraint handleTwoMetavariables(JTerm t0, JTerm t1, boolean modifyThis,
            Services services) {
        final Metavariable mv0 = (Metavariable) t0.op();
        final Metavariable mv1 = (Metavariable) t1.op();
        final Sort mv0S = mv0.sort();
        final Sort mv1S = mv1.sort();
        if (mv1S.extendsTrans(mv0S)) {
            if (mv0S == mv1S) {
                // sorts are equal use Metavariable-order to choose the left MV
                if (mv0.compareTo(mv1) >= 0) {
                    return normalize(mv0, t1, modifyThis, services);
                }
                return normalize(mv1, t0, modifyThis, services);
            }
            return normalize(mv0, t1, modifyThis, services);
        } else if (mv0S.extendsTrans(mv1S)) {
            return normalize(mv1, t0, modifyThis, services);
        }

        // The sorts are incompatible. This is resolved by creating a new
        // metavariable and by splitting the equation into two
        return introduceNewMV(t0, t1, modifyThis, services);
    }

    private Constraint handleQuantifiableVariable(JTerm t0, JTerm t1,
            ImmutableList<QuantifiableVariable> ownBoundVars,
            ImmutableList<QuantifiableVariable> cmpBoundVars) {
        if (!((t1.op() instanceof QuantifiableVariable)
                && compareBoundVariables((QuantifiableVariable) t0.op(),
                    (QuantifiableVariable) t1.op(), ownBoundVars, cmpBoundVars))) {
            return TOP;
        }
        return this;
    }

    /**
     * Unify t1 and t2
     *
     * @param modifyThis <code>this</code> is an object that has just been created during this
     *        unification process
     * @param services the Services providing access to the type model
     *
     * @return a constraint under which t0, t1 are equal modulo bound renaming. <code>this</code> is
     *         returned iff the terms are equal modulo bound renaming under <code>this</code>, or
     *         <code>modifyThis==true</code> and the terms are unifiable. For
     *         <code>!modifyThis</code> a new object is created, and <code>this</code> is never
     *         modified. <code>Constraint.TOP</code> is always returned for ununifiable terms
     */
    private Constraint unifyHelp(JTerm t1, JTerm t2, boolean modifyThis, Services services) {
        return unifyHelp(t1, t2, ImmutableSLList.nil(),
            ImmutableSLList.nil(), null, modifyThis, services);
    }


    /**
     * checks for cycles and adds additional constraints if necessary
     * <p>
     * PRECONDITION: the sorts of mv and t match; if t is also a metavariable with same sort as mv,
     * the order of mv and t is correct (using Metavariable.compareTo)
     *
     * @param mv Metavariable asked to be mapped to the term t
     * @param t the Term the metavariable should be mapped to (if there are no cycles )
     * @param services the Services providing access to the type model
     * @return the resulting Constraint ( == this iff this subsumes the new constraint )
     */
    private Constraint normalize(Metavariable mv, JTerm t, boolean modifyThis,
            Services services) {
        // MV cycles are impossible if the orders of MV pairs are
        // correct

        if (!t.isRigid()) {
            return TOP;
        }

        // metavariable instantiations must not contain free variables
        if (!t.freeVars().isEmpty() ||
                (modifyThis ? hasCycle(mv, t, services) : hasCycleByInst(mv, t, services))) {
            // cycle
            return TOP;
        } else if (map.containsKey(mv)) {
            return unifyHelp(valueOf(mv), t, modifyThis, services);
        }

        final EqualityConstraint newConstraint = getMutableConstraint(modifyThis);
        newConstraint.map.put(mv, t);

        return newConstraint;
    }

    private EqualityConstraint getMutableConstraint(boolean modifyThis) {
        if (modifyThis) {
            return this;
        }
        return new EqualityConstraint((HashMap<Metavariable, JTerm>) map.clone());
    }

    /**
     * checks equality of constraints by subsuming relation (only equal if no new sorts need to be
     * introduced for subsumption)
     */
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Constraint c) {
            if (c instanceof EqualityConstraint) {
                return map.keySet().equals(((EqualityConstraint) c).map.keySet())
                        && join(c, null) == this && c.join(this, null) == c;
            }
            return isAsStrongAs(c) && isAsWeakAs(c);
        }
        return false;
    }

    /**
     * @return true iff this constraint is as strong as "co", i.e. every instantiation satisfying
     *         "this" also satisfies "co".
     */
    @Override
    public boolean isAsStrongAs(Constraint co) {
        if (this == co) {
            return true;
        }
        if (co instanceof EqualityConstraint)
        // use necessary condition for this relation: key set of
        // this is superset of key set of co
        {
            return map.keySet().containsAll(((EqualityConstraint) co).map.keySet())
                    && join(co, null) == this;
        }
        return co.isAsWeakAs(this);
    }

    /**
     * @return true iff this constraint is as weak as "co", i.e. every instantiation satisfying "co"
     *         also satisfies "this".
     */
    @Override
    public boolean isAsWeakAs(Constraint co) {
        if (this == co) {
            return true;
        }
        if (co instanceof EqualityConstraint)
        // use necessary condition for this relation: key set of
        // co is superset of key set of this
        {
            return ((EqualityConstraint) co).map.keySet().containsAll(map.keySet())
                    && co.join(this, null) == co;
        }
        return co.isAsStrongAs(this);
    }

    /**
     * joins the given constraint with this constraint and returns the joint new constraint.
     *
     * @param co Constraint to be joined with this one
     * @return the joined constraint
     */
    @Override
    public Constraint join(Constraint co, Services services) {
        return join(co, services, CONSTRAINTBOOLEANCONTAINER);
    }


    /**
     * joins constraint co with this constraint and returns the joint new constraint. The
     * BooleanContainer is used to wrap a second return value and indicates a subsumption of co by
     * this constraint.
     *
     * @param co Constraint to be joined with this one
     * @param services the Services providing access to the type model
     * @param unchanged the BooleanContainers value set true, if this constraint is as strong as co
     * @return the joined constraint
     */
    @Override
    public synchronized Constraint join(Constraint co, Services services,
            BooleanContainer unchanged) {
        if (co.isBottom() || co == this) {
            unchanged.setVal(true);
            return this;
        } else if (isBottom()) {
            unchanged.setVal(false);
            return co;
        }

        if (!(co instanceof EqualityConstraint)) {
            // BUG: Don't know how to set p_subsumed (at least not
            // efficiently)
            unchanged.setVal(false);
            return co.join(this, services);
        }

        final ECPair cacheKey;

        lookup: synchronized (joinCacheMonitor) {
            ecPair0.set(this, co);
            Constraint res = joinCache.get(ecPair0);

            if (res == null) {
                cacheKey = ecPair0.copy();
                res = joinCacheOld.get(cacheKey);
                if (res == null) {
                    break lookup;
                }
                joinCache.put(cacheKey, res);
            }

            unchanged.setVal(this == res);
            return res;
        }

        final Constraint res = joinHelp((EqualityConstraint) co, services);

        unchanged.setVal(res == this);

        synchronized (joinCacheMonitor) {
            if (joinCache.size() > 1000) {
                joinCacheOld.clear();
                final Map<ECPair, Constraint> t = joinCacheOld;
                joinCacheOld = joinCache;
                joinCache = t;
            }

            joinCache.put(cacheKey, res);
            return res;
        }
    }


    private Constraint joinHelp(EqualityConstraint co, Services services) {
        Constraint newConstraint = this;
        boolean newCIsNew = false;
        for (Map.Entry<Metavariable, JTerm> entry : co.map.entrySet()) {
            newConstraint = ((EqualityConstraint) newConstraint).normalize(entry.getKey(),
                entry.getValue(), newCIsNew, services);
            if (!newConstraint.isSatisfiable()) {
                return TOP;
            }
            newCIsNew = newCIsNew || newConstraint != this;
        }

        return newConstraint;
    }

    /**
     * checks if there is a cycle if the metavariable mv and Term term would be added to this
     * constraint e.g. X=g(Y), Y=f(X)
     *
     * @param mv the Metavariable
     * @param term The Term
     * @return a boolean that is true iff. adding a mapping (mv,term) would cause a cycle
     */
    private boolean hasCycle(Metavariable mv, JTerm term, Services services) {
        ImmutableList<Metavariable> body = ImmutableSLList.nil();
        ImmutableList<JTerm> fringe = ImmutableSLList.nil();
        JTerm checkForCycle = term;

        while (true) {
            for (final Metavariable metavariable : metaVars(checkForCycle, services)) {
                if (!body.contains(metavariable)) {
                    final JTerm termMVterm = getInstantiationIfExisting(metavariable);
                    if (termMVterm != null) {
                        if (metaVars(termMVterm, services).contains(mv)) {
                            return true;
                        }
                    } else {
                        if (map.containsKey(metavariable)) {
                            fringe = fringe.prepend(map.get(metavariable));
                        }
                    }

                    if (metavariable == mv) {
                        return true;
                    }

                    body = body.prepend(metavariable);
                }
            }

            if (fringe.isEmpty()) {
                return false;
            }

            checkForCycle = fringe.head();
            fringe = fringe.tail();
        }
    }

    private boolean hasCycleByInst(Metavariable mv, JTerm term, Services services) {

        for (Metavariable metavariable : metaVars(term, services)) {
            if (metavariable == mv) {
                return true;
            }
            final JTerm termMVterm = getInstantiationIfExisting(metavariable);
            if (termMVterm != null) {
                if (metaVars(termMVterm, services).contains(mv)) {
                    return true;
                }
            } else {
                if (map.containsKey(metavariable)
                        && hasCycle(mv, getDirectInstantiation(metavariable), services)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * ONLY FOR TESTS DONT USE THEM IN ANOTHER WAY
     *
     * @return true if metavar is contained as key
     */
    boolean isDefined(Metavariable mv) {
        return map.containsKey(mv);
    }

    /**
     * ONLY FOR TESTS DONT USE THEM IN ANOTHER WAY
     *
     * @return mapping to mv
     */
    JTerm valueOf(Metavariable mv) {
        return map.get(mv);
    }

    /** @return String representation of the constraint */
    public String toString() {
        return map.toString();
    }


    private static final class ECPair {
        private Constraint first;
        private Constraint second;
        private int hash;

        public boolean equals(Object o) {
            if (!(o instanceof ECPair e)) {
                return false;
            }
            return first == e.first && second == e.second;
        }

        public void set(Constraint first, Constraint second) {
            this.first = first;
            this.second = second;
            this.hash = first.hashCode() + second.hashCode();
        }

        public int hashCode() {
            return hash;
        }

        public ECPair copy() {
            return new ECPair(first, second, hash);
        }

        public ECPair(Constraint first, Constraint second, int hash) {
            this.first = first;
            this.second = second;
            this.hash = hash;
        }
    }

    private static final Object joinCacheMonitor = new Object();

    // the methods using these caches seem not to be used anymore otherwise refactor and move it
    // into ServiceCaches
    private static Map<ECPair, Constraint> joinCache = new LRUCache<>(0);
    private static Map<ECPair, Constraint> joinCacheOld = new LRUCache<>(0);

    private static final ECPair ecPair0 = new ECPair(null, null, 0);

    @Override
    public int hashCode() {
        if (hashCode == null) {
            int h = 0;
            final Iterator<Metavariable> it = restrictedMetavariables();
            while (it.hasNext()) {
                final Metavariable mv = it.next();
                h += mv.hashCode();
                // h += getInstantiation ( mv, services ).hashCode (); // removed line because no
                // idea how
                // to get the services here and the method seems not to be called and the class is
                // deprecated
                // and it still satisfies the contract of 'hashcode'
            }

            hashCode = h;
        }

        return hashCode;
    }
}
