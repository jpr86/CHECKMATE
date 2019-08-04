/*
 * 
 * Coadaptive Heterogeneous simulation Engine for Combat Kill-webs and 
 * Multi-Agent Training Environment (CHECKMATE)
 *
 * Copyright 2006 Jeff Ridder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ridderware.checkmate;

import java.awt.Color;
import java.awt.Font;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.Set;
import org.apache.logging.log4j.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A command and control class that periodically considers its tracks and
 * assigns targets to subordinates.
 *
 * @author Jeff Ridder
 */
public class TargetAssignmentC2 extends PassThroughC2 implements IShooterContainer
{
    //  This is the set of targets assigned to me.
    private LinkedHashSet<Platform> assigned_targets =
        new LinkedHashSet<>();

    //  This is the map of targets I've assigned to others.
    private HashMap<Platform, TargetAssigned> targets_assigned =
        new HashMap<>();

    private int target_capacity;

    private double assignment_threshold;

    //  Marks whether the object can have targets assigned at this point in time.
    private boolean assignable;

    private final static Logger logger =
        LogManager.getLogger(TargetAssignmentC2.class);

    /**
     * Creates a new instance of TargetAssignmentC2.
     * @param name name of the object.
     * @param world CMWorld in which it plays.
     * @param points arbitrary point value.
     */
    public TargetAssignmentC2(String name, CMWorld world, int points)
    {
        super(name, world, points);
        this.createTargetAssignmentC2();
    }

    /**
     * Creates an instance of TargetAssignmentC2 with attributes for GUI display.
     * @param name name of the object.
     * @param world CMWorld in which it plays.
     * @param points arbitrary point value.
     * @param ttf font.
     * @param fontSymbol font symbol used to draw the object.
     * @param color color to draw the object.
     */
    public TargetAssignmentC2(String name, CMWorld world, int points,
        Font ttf, String fontSymbol, Color color)
    {
        super(name, world, points, ttf, fontSymbol, color);
        this.createTargetAssignmentC2();
    }

    /**
     * Sets the initial attributes of a newly minted TargetAssignmentC2.
     */
    protected final void createTargetAssignmentC2()
    {
        this.target_capacity = 0;
        this.assignment_threshold = 0.;
    }

    /**
     * Called by the simulation framework to reset the agent prior to each
     * simulation run.
     */
    @Override
    public void reset()
    {
        super.reset();

        this.targets_assigned.clear();
        this.assigned_targets.clear();
        this.assignable = false;
    }

    /**
     * Flags whether the object can currently receive new target assignments.
     * @param assignable true if targets can be assigned, false otherwise.
     */
    public void setAssignable(boolean assignable)
    {
        this.assignable = assignable;
    }

    /**
     * Returns whether the C2 can currently be assigned targets.
     * @return true if targets can be assigned, false otherwise.
     */
    public boolean isAssignable()
    {
        return this.assignable;
    }

    /**
     * Sets the maximum number of targets this C2 can handle at any time.
     * @param target_capacity target capacity.
     */
    public void setTargetCapacity(int target_capacity)
    {
        this.target_capacity = target_capacity;
    }

    /**
     * Returns the target capacity.
     * @return target capacity.
     */
    public int getTargetCapacity()
    {
        return this.target_capacity;
    }

    /**
     * Sets the assignment threshold as it relates to the engagability
     * figure-of-merit of targets.  Targets that are within the assignment
     * threshold are considered for assignment to subordinates.
     * @param assignment_threshold assignment threshold.
     */
    public void setAssignmentThreshold(double assignment_threshold)
    {
        this.assignment_threshold = assignment_threshold;
    }

    /**
     * Returns the assignment threshold.
     * @return assignment threshold.
     */
    public double getAssignmentThreshold()
    {
        return this.assignment_threshold;
    }

    /**
     * In addition to the superclass behaviors (which age out and forward tracks),
     * this method looks to assign targets to subordinates.
     * @param time the current time.
     */
    @Override
    public void processTracks(double time)
    {
        super.processTracks(time);

        //  Loop over targets assigned, check engageability of subordinates, and assign them.
        for (Platform p : assigned_targets)
        {
            checkTargetForAssignment(p);
        }

        //  I also need to assign tracks that aren't in the assigned-targets set
        for (Platform p : this.getTracks())
        {
            checkTargetForAssignment(p);
        }

        //  I also need to look for targets that are no longer assigned --
        //  tell my subordinates to forget about them as well.
        //  If a target I've assigned is no longer in assignment range of the shooter,
        //  then tell the shooter to fuhgetaboutit.
        Set<Platform> targets = targets_assigned.keySet();
        Iterator<Platform> it = targets.iterator();
        while (it.hasNext())
        {
            Platform p = it.next();
            TargetAssigned ta = this.targets_assigned.get(p);

            boolean bengaging = false;
            if (ta.getAssignedShooter() instanceof SAMSite)
            {
                SAMSite site = (SAMSite) ta.getAssignedShooter();

                if (site.getEngagedTarget() != null &&
                    site.getEngagedTarget().getTarget() == p &&
                    site.getEngagedTarget().getShooter() != null)
                {
                    bengaging = true;
                }
            }

            if (!bengaging && (ta.getAssignedShooter().getEngageability(p) >
                this.assignment_threshold ||
                //                    p.getStatus() != Platform.Status.ACTIVE || !getTracks().contains(p) ) )
                p.getStatus() != Platform.Status.ACTIVE))
            {
                logger.debug(this.getName() +
                    " UNASSIGNING a shooter (test to see if this code is reached).");
                ta.getAssignedShooter().unassignTarget(p);
                it.remove();
            }
        }
    }

    /**
     * Supporting method to check the specified target for whether it can be
     * assigned to a subordinate, and does so if possible.  This routine also
     * checks for re-assignment.  That is, if it finds a better shooter than the
     * one currently assigned, it will unassign the previous shooter and assign
     * the better shooter.
     * @param p target to be considered for assignment.
     */
    protected void checkTargetForAssignment(Platform p)
    {
        //  Find the best shooter for this target
        double engageability = Double.MAX_VALUE;
        IShooterContainer shooter = null;

        for (Platform s : this.getSubordinates())
        {
            if (s instanceof IShooterContainer)
            {
                IShooterContainer s_cand = (IShooterContainer) s;
                double s_engage = s_cand.getEngageability(p);
                if (s_engage < engageability && s_engage <=
                    this.assignment_threshold)
                {
                    if (s_cand instanceof TargetAssignmentC2)
                    {
                        TargetAssignmentC2 ta_cand = (TargetAssignmentC2) s_cand;

                        if (ta_cand.isAssignable())
                        {
                            //  If there is capacity, or if the SAM site has a better shot at target platform p instead, then
                            //  mark it.
                            if ((ta_cand.getAssignedTargets().size() <
                                ta_cand.getTargetCapacity()) ||
                                (ta_cand instanceof SAMSite &&
                                ((SAMSite) ta_cand).getEngagedTarget() != null &&
                                s_engage < ((SAMSite) ta_cand).getEngageability(((SAMSite) ta_cand).getEngagedTarget().
                                getTarget()) &&
                                ((SAMSite) ta_cand).getEngagedTarget().
                                getShooter() == null))
                            {
                                engageability = s_engage;
                                shooter = s_cand;
                            }
                        }
                    }
                    else
                    {
                        engageability = s_engage;
                        shooter = s_cand;
                    }
//                    if ( (s_cand instanceof TargetAssignmentC2 &&
//                            ((TargetAssignmentC2)s_cand).getAssignedTargets().size() <
//                            ((TargetAssignmentC2)s_cand).getTargetCapacity() &&
//                            ((TargetAssignmentC2)s_cand).isAssignable()) ||
//                            !(s_cand instanceof TargetAssignmentC2) )
//                    {
//                        engageability = s_engage;
//                        shooter = s_cand;
//                    }
                }
            }
        }

        if (shooter != null)
        {
            //  First, see if the shooter is already at capacity and we're going to displace its current assignment
            if (shooter instanceof SAMSite)
            {
                SAMSite site = (SAMSite) shooter;
                if (site.getAssignedTargets().size() >= site.getTargetCapacity() &&
                    site.getEngagedTarget() != null &&
                    site.getEngagedTarget().getTarget() != null && 
                    !(this instanceof SAMBattalion))
                {
                    //  Unassign a target.
                    this.targets_assigned.remove(site.getEngagedTarget().
                        getTarget());
                    site.priorityAssignTarget(p);
                    this.targets_assigned.put(p, new TargetAssigned(p, shooter));
                }
            }

            //  Now see if this target was previously assigned
            TargetAssigned ta = this.targets_assigned.get(p);
            boolean bengaging = false;
            if (ta != null && ta.getAssignedShooter() instanceof SAMSite && !(this instanceof SAMBattalion))
            {
                SAMSite site = (SAMSite) ta.getAssignedShooter();

                if (site.getEngagedTarget() != null &&
                    site.getEngagedTarget().getTarget() == p &&
                    site.getEngagedTarget().getShooter() != null)
                {
                    bengaging = true;
                }
            }

            if (!bengaging && ta != null && ta.getAssignedShooter() != null &&
                ta.getAssignedShooter() != shooter &&
                ta.getAssignedShooter().getEngageability(p) > engageability*1.15 &&
                !(this instanceof SAMBattalion))
            {
                logger.debug(this.getName() +
                    " about to change shooters\nOld shooter: " + ta.getAssignedShooter().
                    getEngageability(p) + "\nNew shooter: " +
                    shooter.getEngageability(p));
                //  Target was assigned, but there is a better shooter out there.
                //  Remove the target from the previous shooter.
                ta.getAssignedShooter().unassignTarget(p);
                this.targets_assigned.remove(p);
            }

            if (this.targets_assigned.get(p) == null)
            {
                //  Assign the target to shooter if not already assigned
                shooter.assignTarget(p);
                this.targets_assigned.put(p, new TargetAssigned(p, shooter));
            }
        }
    }

    /**
     * Recursive call to compute the engageability figure-of-merit of the specified
     * target.
     * @param target target platform.
     * @return engageability.
     */
    @Override
    public double getEngageability(Platform target)
    {
        //  This will be called by the superior to get information necessary to
        //  assign a target to this system.  Assigned targets must be killed.
        double engageability = Double.MAX_VALUE;

        for (Platform p : this.getSubordinates())
        {
            if (p instanceof IShooterContainer)
            {
                engageability =
                    Math.min(((IShooterContainer) p).getEngageability(target),
                    engageability);
            }
        }

        return engageability;
    }

    /**
     * Called by superior to assign a target.
     * @param target target being assigned.
     */
    @Override
    public void assignTarget(Platform target)
    {
        if (assigned_targets.size() < target_capacity)
        {
            logger.debug("Target " + target.getName() + " ASSIGNED to " +
                this.getName() + " at time " + getUniverse().getCurrentTime());
            assigned_targets.add(target);
        }
    }

    /**
     * Called by the superior to assign a higher priority target.  If the shooter has no
     * capacity, it will displace a current target to accept this one.
     * @param target priority target being assigned.
     */
    @Override
    public void priorityAssignTarget(Platform target)
    {
        if (assigned_targets.size() >= target_capacity && target_capacity > 0)
        {
            Iterator it = this.assigned_targets.iterator();
            if (it.hasNext())
            {
                if (it.next() instanceof Platform)
                {
                    it.remove();
                }
            }
        }
        assignTarget(target);
    }

    /**
     * Called by superior to unassign a target.
     * @param target target being unassigned.
     */
    @Override
    public void unassignTarget(Platform target)
    {
        this.assigned_targets.remove(target);

        //  If target as been assigned to subs, then remove from them as well.
        if (targets_assigned.containsKey(target))
        {
            targets_assigned.get(target).getAssignedShooter().unassignTarget(target);
            this.targets_assigned.remove(target);
            logger.debug("Target " + target.getName() + " UNASSIGNED from " +
                this.getName() + " at time " + getUniverse().getCurrentTime());
        }
    }

    /**
     * Called by subordinates to report the outcome of a target engagement.
     * @param target target being reported.
     * @param destroyed true if target was destroyed, false otherwise.
     */
    @Override
    public void targetDestroyed(Platform target, boolean destroyed)
    {
        //  Report to upper echelon.  If destroyed then remove target from
        //  set of targets assigned to me.
        if (destroyed)
        {
            if (this.targets_assigned.containsKey(target))
            {
                this.unassignTarget(target);
            }
        }

        if (getSuperior() instanceof IShooterContainer)
        {
            ((IShooterContainer) getSuperior()).targetDestroyed(target,
                destroyed);
        }
    }

    /**
     * Called by subordinate shooters to inform that target is being aborted.  This
     * puts the target back "in play" for being assigned to another shooter.
     * @param shooter the shooter that is aborting the target.
     * @param target the target being aborted.
     */
    public void abortingTarget(IShooterContainer shooter, Platform target)
    {
        this.targets_assigned.remove(target);
    }

    /**
     * Returns the set of targets assigned to this C2 by upper echelon.
     * @return Set of assigned targets.
     */
    protected Set<Platform> getAssignedTargets()
    {
        return this.assigned_targets;
    }

    /**
     * Parses the XML node to set the attributes of this object.
     * @param node XML node.
     */
    @Override
    public void fromXML(Node node)
    {
        super.fromXML(node);

        NodeList children = node.getChildNodes();

        for (int j = 0; j < children.getLength(); j++)
        {
            Node child = children.item(j);

            if (child.getNodeName().equalsIgnoreCase("target-capacity"))
            {
                this.setTargetCapacity(Integer.parseInt(child.getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("assignment-threshold"))
            {
                this.setAssignmentThreshold(Double.parseDouble(child.getTextContent()));
            }
        }
    }

    /**
     * Creates sub-elements of the XML node containing this object's attributes.
     * @param node XML node.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        Document document = node.getOwnerDocument();

        Element e;

        e = document.createElement("target-capacity");
        e.setTextContent(String.valueOf(this.target_capacity));
        node.appendChild(e);

        e = document.createElement("assignment-threshold");
        e.setTextContent(String.valueOf(this.getAssignmentThreshold()));
        node.appendChild(e);
    }

    /**
     * Class for keeping track of which targets were assigned to which subordinate
     * shooters.
     */
    protected class TargetAssigned
    {
        private Platform target;

        private IShooterContainer assigned_shooter;

        /**
         * Creates a new instance of TargetAssigned.
         * @param target target that was assigned.
         * @param assigned_shooter shooter is was assigned to.
         */
        public TargetAssigned(Platform target,
            IShooterContainer assigned_shooter)
        {
            this.target = target;
            this.assigned_shooter = assigned_shooter;
        }

        /**
         * Returns the target.
         * @return target.
         */
        public Platform getTarget()
        {
            return this.target;
        }

        /**
         * Returns the shooter.
         * @return shooter.
         */
        public IShooterContainer getAssignedShooter()
        {
            return this.assigned_shooter;
        }
    }
}
